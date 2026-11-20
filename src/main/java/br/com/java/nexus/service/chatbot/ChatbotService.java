package br.com.java.nexus.service.chatbot;

import br.com.java.nexus.model.chatbot.RespostaChat;
import br.com.java.nexus.model.pedido.enums.StatusPedido;
import br.com.java.nexus.model.produto.Produto;
import br.com.java.nexus.repository.chatbot.ComprasLeituraRepository;
import br.com.java.nexus.repository.chatbot.PreferenciasLeituraRepository;
import br.com.java.nexus.repository.chatbot.RespostaChatRepository;
import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.repository.produto.ProdutoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatbotService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatbotService.class);
    private static final String VERSAO_CATALOGO = "booknexus-catalogo-v1";
    private static final EnumSet<StatusPedido> COMPRAS = EnumSet.of(
            StatusPedido.APROVADO, StatusPedido.EM_TRANSPORTE, StatusPedido.ENTREGUE,
            StatusPedido.EM_TROCA, StatusPedido.TROCA_AUTORIZADA, StatusPedido.TROCA_RECUSADA);

    private final ClienteRepository clientes;
    private final ProdutoRepository produtos;
    private final ComprasLeituraRepository compras;
    private final PreferenciasLeituraRepository preferencias;
    private final RespostaChatRepository respostas;
    private final ModeloChat modelo;
    private final ObjectMapper json;

    ChatbotService(ClienteRepository clientes, ProdutoRepository produtos, ComprasLeituraRepository compras,
                   PreferenciasLeituraRepository preferencias, RespostaChatRepository respostas,
                   ModeloChat modelo, ObjectMapper json) {
        this.clientes = clientes;
        this.produtos = produtos;
        this.compras = compras;
        this.preferencias = preferencias;
        this.respostas = respostas;
        this.modelo = modelo;
        this.json = json;
    }

    public Estado abrir(Long clienteId) {
        validarCliente(clienteId);
        var recentes = respostas.findTop12ByClienteIdOrderByIdDesc(clienteId).reversed();
        var ids = recentes.stream().flatMap(r -> lerResposta(r).livros().stream()).distinct().toList();
        var catalogo = produtos.findAllById(ids).stream().filter(Produto::estaDisponivel)
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        return new Estado(preferenciasDe(clienteId), recentes.stream()
                .map(r -> mensagem(r, lerResposta(r), catalogo)).toList());
    }

    public Preferencias salvarPreferencias(Long clienteId, Preferencias perfil) {
        validarCliente(clienteId);
        var atual = new Preferencias(limpar(perfil.favoritos()), limpar(perfil.evitar()));
        preferencias.gravar(clienteId, atual.favoritos(), atual.evitar());
        return atual;
    }

    public Mensagem perguntar(Long clienteId, String pergunta) {
        validarCliente(clienteId);
        String texto = pergunta.strip();
        var cortesia = PoliticaChat.cortesia(texto);
        if (cortesia.isPresent()) {
            return registrarFixa(clienteId, texto, cortesia.get(), PoliticaChat.VERSAO);
        }
        var estoque = produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0);
        var recentes = respostas.findTop12ByClienteIdOrderByIdDesc(clienteId).stream()
                .filter(this::serveDeContexto)
                .filter(r -> PoliticaChat.permite(r.getPergunta(), estoque, true)).limit(2).toList();
        if (!PoliticaChat.permite(texto, estoque, !recentes.isEmpty())) {
            return registrarFixa(clienteId, texto, PoliticaChat.REDIRECIONAMENTO, PoliticaChat.VERSAO);
        }
        var orientacao = PoliticaChat.orientacaoLoja(texto, CatalogoChat.pedeLeitura(estoque, texto));
        if (orientacao.isPresent()) {
            return registrarFixa(clienteId, texto, orientacao.get(), PoliticaChat.VERSAO_LOJA);
        }
        var perfil = preferenciasDe(clienteId);
        var comprados = compras.livrosComprados(clienteId, COMPRAS, PageRequest.of(0, 30)).stream()
                .distinct().limit(8).toList();
        var anteriores = recentes.stream().map(r -> {
            var saida = PoliticaChat.ajustar(lerResposta(r), r.getPergunta());
            var selecionados = saida.livros().stream()
                    .flatMap(id -> estoque.stream().filter(p -> id.equals(p.getId()))).toList();
            String textoAnterior = saida.livros().isEmpty() ? saida.texto()
                    : RespostaCatalogoChat.descrever(r.getPergunta(), selecionados);
            return new Turno(r.getPergunta(), limitar(textoAnterior, 350), saida.livros());
        }).toList();
        String perguntaAnterior = anteriores.isEmpty() ? "" : anteriores.getFirst().pergunta();
        List<Long> indicadosAntes = anteriores.isEmpty() ? List.of() : anteriores.getFirst().livros();
        if (CatalogoChat.semCorrespondencia(estoque, texto, indicadosAntes,
                !anteriores.isEmpty() && PoliticaChat.continuacao(texto))) {
            // Sem candidatos plausíveis, o modelo poderia indicar um livro qualquer do perfil.
            return registrarFixa(clienteId, texto, RespostaCatalogoChat.semResultado(estoque), VERSAO_CATALOGO);
        }
        var candidatos = CatalogoChat.selecionar(estoque,
                texto, perguntaAnterior, indicadosAntes, perfil.favoritos(), perfil.evitar(), comprados);
        var generos = CatalogoChat.generosSolicitados(estoque, texto);
        var idsCompativeis = estoque.stream().filter(p -> CatalogoChat.generoCompativel(p, generos))
                .map(Produto::getId).collect(Collectors.toSet());
        // O modelo pequeno confunde títulos antigos com candidatos se o assunto mudou de gênero.
        var conversa = generos.isEmpty() ? anteriores.reversed() : anteriores.reversed().stream()
                .filter(t -> !t.livros().isEmpty() && idsCompativeis.containsAll(t.livros())).toList();
        var perfilContexto = generos.isEmpty() ? perfil : new Preferencias("", "");
        boolean buscaDireta = !generos.isEmpty() || CatalogoChat.buscaDireta(texto, candidatos, indicadosAntes)
                || CatalogoChat.buscaPorTema(estoque, texto)
                || (!candidatos.isEmpty() && PoliticaChat.recomendacaoAberta(texto));
        var contexto = new Contexto(texto, buscaDireta && !candidatos.isEmpty(), perfilContexto, comprados.stream()
                .filter(p -> CatalogoChat.generoCompativel(p, generos)).map(LivroComprado::de).toList(),
                candidatos.stream().map(Livro::de).toList(), conversa);
        String dados = json.writeValueAsString(contexto);
        Map<Long, Produto> catalogo = candidatos.stream().collect(Collectors.toMap(Produto::getId, Function.identity()));
        var saida = gerarResposta(dados, texto, catalogo);
        String versao = modelo.versao();
        if (saida != null && saida.livros().isEmpty() && PoliticaChat.desvia(saida.texto())) {
            saida = new ModeloChat.Saida(PoliticaChat.REDIRECIONAMENTO, List.of());
            versao = PoliticaChat.VERSAO;
        } else if (saida == null || (saida.livros().isEmpty() && buscaDireta)) {
            var ids = candidatos.stream().limit(3).map(Produto::getId).toList();
            saida = new ModeloChat.Saida(ids.isEmpty() && !generos.isEmpty()
                    ? "Não encontrei livros disponíveis desse gênero com os filtros de preço ou páginas solicitados."
                    : RespostaCatalogoChat.semResultado(estoque), ids);
            versao = VERSAO_CATALOGO;
        }
        // O texto exibido vem sempre do catálogo: o modelo escolhe os IDs, mas o texto livre
        // de um modelo pequeno mistura títulos, repete a pergunta ou inventa dados (ver README).
        if (!saida.livros().isEmpty()) {
            saida = new ModeloChat.Saida(RespostaCatalogoChat.descrever(texto,
                    saida.livros().stream().map(catalogo::get).toList()), saida.livros());
        } else if (!VERSAO_CATALOGO.equals(versao) && !PoliticaChat.VERSAO.equals(versao)) {
            saida = new ModeloChat.Saida(RespostaCatalogoChat.semResultado(estoque), List.of());
        }
        var salvo = respostas.save(new RespostaChat(clienteId, texto, dados,
                json.writeValueAsString(saida), versao));
        return mensagem(salvo, saida, catalogo);
    }

    /** Respostas sem consulta ao modelo: cortesia, redirecionamento, orientação de compra e busca sem resultado. */
    private Mensagem registrarFixa(Long clienteId, String pergunta, String texto, String versao) {
        var saida = new ModeloChat.Saida(texto, List.of());
        String dados = json.writeValueAsString(Map.of("pergunta", pergunta, "candidatos", List.of()));
        var salvo = respostas.save(new RespostaChat(clienteId, pergunta, dados, json.writeValueAsString(saida), versao));
        return mensagem(salvo, saida, Map.of());
    }

    /** Só turnos em que o catálogo indicou livros, ou que o modelo respondeu, entram como conversa recente. */
    private boolean serveDeContexto(RespostaChat resposta) {
        String versao = resposta.getModelo();
        if (PoliticaChat.VERSAO.equals(versao) || PoliticaChat.VERSAO_LOJA.equals(versao)) {
            return false;
        }
        return !VERSAO_CATALOGO.equals(versao) || !lerResposta(resposta).livros().isEmpty();
    }

    private ModeloChat.Saida gerarResposta(String dados, String pergunta, Map<Long, Produto> catalogo) {
        String entrada = dados;
        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            String motivo;
            try {
                var saida = PoliticaChat.ajustar(modelo.responder(entrada), pergunta);
                motivo = validarSaida(saida, catalogo);
                if (motivo == null) {
                    return saida;
                }
            } catch (ModeloChat.RespostaInvalida e) {
                motivo = e.motivo();
            }
            LOG.warn("Resposta do chatbot rejeitada (tentativa {}/2): {}", tentativa, motivo);
            if (tentativa < 2) {
                var revisado = (ObjectNode) json.readTree(dados);
                revisado.put("correcaoFormato", "A resposta anterior foi rejeitada: " + motivo
                        + ". Use até três IDs distintos dos candidatos, ou lista vazia se nenhum atender. "
                        + "Responda à pergunta atual em uma frase breve e sem marcadores.");
                entrada = json.writeValueAsString(revisado);
            }
        }
        return null;
    }

    private static String validarSaida(ModeloChat.Saida saida, Map<Long, Produto> catalogo) {
        if (saida == null || saida.livros() == null || saida.livros().size() > 3) {
            return "lista de livros ausente ou maior que três";
        }
        if (!catalogo.keySet().containsAll(saida.livros())) {
            return "ID fora dos candidatos da pergunta atual";
        }
        if (new HashSet<>(saida.livros()).size() != saida.livros().size()) {
            return "IDs de livros repetidos";
        }
        if (saida.livros().isEmpty() && (saida.texto() == null || saida.texto().isBlank()
                || saida.texto().length() > 2000 || saida.texto().matches("(?is).*\\[(?:t[ií]tulo|autor|nome do livro)\\].*"))) {
            return "texto ausente, fora do limite ou com marcador";
        }
        return null;
    }

    public void avaliar(Long clienteId, Long respostaId, boolean util) {
        validarCliente(clienteId);
        if (respostas.avaliar(clienteId, respostaId, util, Instant.now()) == 0) {
            throw new NoSuchElementException("Resposta do chat não encontrada para este cliente.");
        }
    }

    private void validarCliente(Long id) {
        if (id == null || id <= 0 || clientes.findById(id).filter(c -> !Boolean.TRUE.equals(c.getInativado())).isEmpty()) {
            throw new NoSuchElementException("Selecione um cliente ativo para usar o chat.");
        }
    }

    private Preferencias preferenciasDe(Long id) {
        return preferencias.findById(id).map(p -> new Preferencias(p.getFavoritos(), p.getEvitar()))
                .orElse(new Preferencias("", ""));
    }

    private ModeloChat.Saida lerResposta(RespostaChat resposta) {
        return json.readValue(resposta.getResposta(), ModeloChat.Saida.class);
    }

    private static Mensagem mensagem(RespostaChat r, ModeloChat.Saida saida, Map<Long, Produto> catalogo) {
        var livros = saida.livros().stream().filter(catalogo::containsKey).map(catalogo::get).toList();
        String texto = saida.livros().isEmpty() ? PoliticaChat.ajustar(saida, r.getPergunta()).texto()
                : livros.isEmpty() ? "Os livros desta resposta não estão disponíveis no catálogo."
                : RespostaCatalogoChat.descrever(r.getPergunta(), livros);
        return new Mensagem(r.getId(), r.getPergunta(), texto,
                livros.stream().map(CartaoLivro::de).toList(), r.getModelo(), r.getUtil());
    }

    private static String limpar(String valor) {
        return valor == null ? "" : valor.strip();
    }

    private static String limitar(String valor, int limite) {
        String texto = limpar(valor);
        if (texto.length() <= limite) {
            return texto;
        }
        int fim = Character.isHighSurrogate(texto.charAt(limite - 1)) ? limite - 1 : limite;
        return texto.substring(0, fim);
    }

    public record Estado(Preferencias preferencias, List<Mensagem> mensagens) {}
    public record Preferencias(String favoritos, String evitar) {}
    public record Mensagem(Long id, String pergunta, String texto, List<CartaoLivro> livros, String modelo,
                           Boolean util) {}
    public record CartaoLivro(Long id, String titulo, String autor, BigDecimal preco) {
        static CartaoLivro de(Produto p) {
            return new CartaoLivro(p.getId(), p.getTitulo(), p.getAutor(), p.getPreco());
        }
    }
    private record Contexto(String pergunta, boolean selecionarLivro, Preferencias preferencias, List<LivroComprado> historicoCompras,
                            List<Livro> candidatos, List<Turno> conversaRecente) {}
    private record Turno(String pergunta, String resposta, List<Long> livros) {}
    private record LivroComprado(Long id, String titulo, String genero) {
        static LivroComprado de(Produto p) {
            return new LivroComprado(p.getId(), p.getTitulo(), p.getGenero());
        }
    }
    private record Livro(Long id, String titulo, String autor, String genero, String subgenero,
                         String sinopse, BigDecimal preco, Integer paginas, String idioma, Integer ano,
                         String editora, String isbn) {
        static Livro de(Produto p) {
            return new Livro(p.getId(), p.getTitulo(), p.getAutor(), p.getGenero(), p.getSubGenero(),
                    limitar(p.getSinopse(), 240), p.getPreco(), p.getPaginas(), p.getIdioma(),
                    p.getAnoPublicacao(), p.getEditora(), p.getIsbn());
        }
    }
}
