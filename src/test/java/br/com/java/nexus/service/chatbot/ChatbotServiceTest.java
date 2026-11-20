package br.com.java.nexus.service.chatbot;

import br.com.java.nexus.model.chatbot.RespostaChat;
import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.produto.Produto;
import br.com.java.nexus.repository.chatbot.ComprasLeituraRepository;
import br.com.java.nexus.repository.chatbot.PreferenciasLeituraRepository;
import br.com.java.nexus.repository.chatbot.RespostaChatRepository;
import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.repository.produto.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ChatbotServiceTest {
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ProdutoRepository produtos = mock(ProdutoRepository.class);
    private final ComprasLeituraRepository compras = mock(ComprasLeituraRepository.class);
    private final PreferenciasLeituraRepository preferencias = mock(PreferenciasLeituraRepository.class);
    private final RespostaChatRepository respostas = mock(RespostaChatRepository.class);
    private final ModeloChat modelo = mock(ModeloChat.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ChatbotService chat = new ChatbotService(
            clientes, produtos, compras, preferencias, respostas, modelo, json);

    @BeforeEach
    void preparar() {
        when(clientes.findById(1L)).thenReturn(Optional.of(new Cliente()));
        when(clientes.findById(2L)).thenReturn(Optional.of(new Cliente()));
        when(modelo.versao()).thenReturn("booknexus-qwen-lora-teste");
        when(respostas.save(any(RespostaChat.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    void aMesmaPerguntaRecebeCandidatosDiferentesPeloHistoricoDeCadaCliente() {
        Produto fantasia = livro(10L, "Runas", "Fantasia", "45.00", 1);
        Produto misterio = livro(15L, "Pistas", "Mistério", "55.00", 1);
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0)).thenReturn(List.of(fantasia, misterio));
        when(compras.livrosComprados(eq(1L), any(), any()))
                .thenReturn(List.of(livro(11L, "Mapas", "Fantasia", "23.00", 0)));
        when(compras.livrosComprados(eq(2L), any(), any()))
                .thenReturn(List.of(livro(16L, "Enigmas", "Mistério", "30.00", 0)));
        when(modelo.responder(any())).thenAnswer(invocacao -> {
            var dados = json.readTree((String) invocacao.getArgument(0));
            long id = dados.path("candidatos").path(0).path("id").asLong();
            return new ModeloChat.Saida("Uma sugestão para você.", List.of(id));
        });

        assertEquals(10L, chat.perguntar(1L, "O que você recomenda?").livros().getFirst().id());
        assertEquals(15L, chat.perguntar(2L, "O que você recomenda?").livros().getFirst().id());
    }

    @Test
    void rejeitaIdInventadoEIgnoraTitulosForaDoPrecoOuSemEstoque() {
        Produto barato = livro(10L, "Runas", "Fantasia", "45.00", 1);
        Produto caro = livro(15L, "O mapa", "Fantasia", "55.00", 1);
        Produto esgotado = livro(16L, "Portal", "Fantasia", "30.00", 0);
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0))
                .thenReturn(List.of(barato, caro, esgotado));
        when(modelo.responder(any())).thenAnswer(invocacao -> {
            var candidatos = json.readTree((String) invocacao.getArgument(0)).path("candidatos");
            assertEquals(1, candidatos.size());
            assertEquals(10L, candidatos.path(0).path("id").asLong());
            return new ModeloChat.Saida("Uma sugestão.", List.of(99L));
        });

        var mensagem = chat.perguntar(1L, "Quero fantasia até R$ 50,00.");
        assertEquals(List.of(10L), mensagem.livros().stream().map(ChatbotService.CartaoLivro::id).toList());
        assertEquals("booknexus-catalogo-v1", mensagem.modelo());
        verify(modelo, times(2)).responder(any());
    }

    @Test
    void registraFeedbackSomenteParaOClienteDaRespostaEAtualizaPreferencias() {
        when(respostas.avaliar(eq(1L), eq(22L), eq(false), any())).thenReturn(1);
        chat.avaliar(1L, 22L, false);
        assertThrows(NoSuchElementException.class, () -> chat.avaliar(2L, 22L, true));
        assertEquals(new ChatbotService.Preferencias("Fantasia", ""),
                chat.salvarPreferencias(1L, new ChatbotService.Preferencias(" Fantasia ", null)));
        verify(preferencias).gravar(1L, "Fantasia", "");
    }

    @Test
    void recusaOutroAssuntoSemChamarModeloMesmoComHistoricoERegistraResposta() {
        when(respostas.findTop12ByClienteIdOrderByIdDesc(1L)).thenReturn(List.of(new RespostaChat(
                1L, "Quero fantasia", "{}", "{\"texto\":\"Sugiro Runas.\",\"livros\":[10]}", "qwen")));
        var mensagem = chat.perguntar(1L, "Você vende carros?");
        assertEquals(PoliticaChat.REDIRECIONAMENTO, mensagem.texto());
        assertTrue(mensagem.livros().isEmpty());
        assertEquals(PoliticaChat.VERSAO, mensagem.modelo());
        verifyNoInteractions(modelo, compras, preferencias);
        verify(respostas).save(any());
    }

    @Test
    void limpaAberturaRepetidaAntesDeSalvarEExibir() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0))
                .thenReturn(List.of(livro(10L, "Runas", "Fantasia", "45.00", 1)));
        when(modelo.responder(any())).thenReturn(new ModeloChat.Saida(
                "O que você me recomenda? Sugiro Runas, uma leitura de fantasia.", List.of(10L)));
        assertTrue(chat.perguntar(1L, "Quero fantasia").texto().startsWith("Runas · Autor · Fantasia"));
        verify(respostas).save(org.mockito.ArgumentMatchers.argThat(r ->
                !r.getResposta().contains("O que você me recomenda?")));
    }

    @Test
    void redirecionamentoNaoContaminaContextoDaProximaPerguntaLiteraria() {
        when(respostas.findTop12ByClienteIdOrderByIdDesc(1L)).thenReturn(List.of(new RespostaChat(
                1L, "Fale sobre política", "{}", "{\"texto\":\"Posso ajudar com livros.\",\"livros\":[]}",
                PoliticaChat.VERSAO)));
        when(modelo.responder(any())).thenAnswer(invocacao -> {
            assertTrue(json.readTree((String) invocacao.getArgument(0)).path("conversaRecente").isEmpty());
            return new ModeloChat.Saida("Qual gênero de leitura você prefere?", List.of());
        });
        assertEquals(RespostaCatalogoChat.semResultado(List.of()), chat.perguntar(1L, "Recomende livros").texto());
    }

    @Test
    void limpaAberturaRepetidaEmMensagensAntigasSemAlterarBanco() {
        when(respostas.findTop12ByClienteIdOrderByIdDesc(1L)).thenReturn(List.of(new RespostaChat(
                1L, "Quero fantasia", "{}", "{\"texto\":\"O que você me recomenda? Sugiro Runas.\",\"livros\":[]}",
                "qwen")));
        assertEquals("Sugiro Runas.", chat.abrir(1L).mensagens().getFirst().texto());
        verify(respostas, never()).save(any());
    }

    @Test
    void substituiDesvioDoModeloPorRedirecionamentoSemLivros() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0))
                .thenReturn(List.of(livro(10L, "Runas", "Fantasia", "45.00", 1)));
        when(modelo.responder(any())).thenReturn(new ModeloChat.Saida(
                "Vamos falar sobre política e sobre o governo.", List.of()));
        var mensagem = chat.perguntar(1L, "Recomende livros");
        assertEquals(PoliticaChat.REDIRECIONAMENTO, mensagem.texto());
        assertTrue(mensagem.livros().isEmpty());
        assertEquals(PoliticaChat.VERSAO, mensagem.modelo());
    }

    @Test
    void romanceSolicitadoPorLucasTemPrioridadeSobreFantasiaEConversaAnterior() {
        var fantasia = livro(10L, "Runas", "Fantasia", "45.00", 1);
        var romance = livro(25L, "Café na Rua do Sol", "Romance", "39.00", 1);
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0)).thenReturn(List.of(fantasia, romance));
        when(compras.livrosComprados(eq(1L), any(), any())).thenReturn(List.of(fantasia));
        when(preferencias.findById(1L)).thenReturn(Optional.of(json.readValue(
                "{\"clienteId\":1,\"favoritos\":\"Fantasia\",\"evitar\":\"Romance\"}",
                br.com.java.nexus.model.chatbot.PreferenciasLeitura.class)));
        when(respostas.findTop12ByClienteIdOrderByIdDesc(1L)).thenReturn(List.of(new RespostaChat(
                1L, "Quero fantasia", "{}", "{\"texto\":\"Sugiro Runas.\",\"livros\":[10]}", "qwen")));
        when(modelo.responder(any())).thenAnswer(invocacao -> {
            var dados = json.readTree((String) invocacao.getArgument(0));
            var candidatos = dados.path("candidatos");
            assertTrue(dados.path("historicoCompras").isEmpty());
            assertTrue(dados.path("conversaRecente").isEmpty());
            assertEquals("", dados.path("preferencias").path("favoritos").asText());
            assertEquals(1, candidatos.size());
            assertEquals(25L, candidatos.path(0).path("id").asLong());
            return new ModeloChat.Saida("Sugiro Café na Rua do Sol, um romance do catálogo.", List.of(25L));
        });
        assertEquals(25L, chat.perguntar(1L, "Que livros de romance você me recomenda para ler?")
                .livros().getFirst().id());
    }

    @Test
    void corrigeUmaRespostaComIdDePosicaoSemSalvarARespostaInvalida() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0))
                .thenReturn(List.of(livro(25L, "Café na Rua do Sol", "Romance", "39.00", 1)));
        when(modelo.responder(any())).thenReturn(new ModeloChat.Saida("Sugiro o primeiro.", List.of(1L)))
                .thenAnswer(invocacao -> {
                    assertTrue(json.readTree((String) invocacao.getArgument(0)).has("correcaoFormato"));
                    return new ModeloChat.Saida("Sugiro Café na Rua do Sol.", List.of(25L));
                });
        assertEquals(25L, chat.perguntar(1L, "Recomende romance").livros().getFirst().id());
        verify(respostas, times(1)).save(any());
        verify(modelo, times(2)).responder(any());
    }

    @Test
    void corrigeJsonInvalidoMasNaoRepeteFalhasDeConexao() {
        when(modelo.responder(any())).thenThrow(new ModeloChat.RespostaInvalida("JSON inválido"))
                .thenReturn(new ModeloChat.Saida("Qual autor você gostaria de ler?", List.of()));
        assertEquals(RespostaCatalogoChat.semResultado(List.of()), chat.perguntar(1L, "Recomende livros").texto());
        verify(modelo, times(2)).responder(any());

        org.mockito.Mockito.reset(modelo);
        when(modelo.responder(any())).thenThrow(new ModeloChat.ModeloIndisponivel());
        assertThrows(ModeloChat.ModeloIndisponivel.class, () -> chat.perguntar(1L, "Recomende livros"));
        verify(modelo, times(1)).responder(any());
    }

    @Test
    void naoExibeMarcadorDeTituloCopiadoDoPrompt() {
        when(modelo.responder(any())).thenReturn(new ModeloChat.Saida("Sugiro [título], porque...", List.of()))
                .thenReturn(new ModeloChat.Saida("Recomendo um título inventado, de um autor inventado.", List.of()));
        assertEquals(RespostaCatalogoChat.semResultado(List.of()), chat.perguntar(1L, "Recomende livros").texto());
        verify(modelo, times(2)).responder(any());
    }

    @Test
    void naoConfundeTituloDeFantasiaComIdDeRomance() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0)).thenReturn(List.of(
                livro(2L, "O Bosque das Marés", "Fantasia", "44.00", 1),
                livro(26L, "A Última Carta de Junho", "Romance", "44.00", 1)));
        when(modelo.responder(any())).thenReturn(new ModeloChat.Saida(
                "Recomendo O Bosque das Marés, um romance de outra autora.", List.of(26L)));
        var mensagem = chat.perguntar(1L, "Que livros de romance você me recomenda para ler?");
        assertTrue(mensagem.texto().startsWith("A Última Carta de Junho · Autor · Romance"));
        assertTrue(!mensagem.texto().contains("O Bosque das Marés"));
        assertEquals(26L, mensagem.livros().getFirst().id());
        verify(modelo, times(1)).responder(any());
    }

    @Test
    void historicoUsaOsDadosDaObraIndicadaMesmoComTextoAntigoIncorreto() {
        var romance = livro(26L, "A Última Carta de Junho", "Romance", "44.00", 1);
        when(produtos.findAllById(List.of(26L))).thenReturn(List.of(romance));
        when(respostas.findTop12ByClienteIdOrderByIdDesc(1L)).thenReturn(List.of(new RespostaChat(
                1L, "Quero romance", "{}", "{\"texto\":\"Sugiro Runas, de outra autora.\",\"livros\":[26]}", "qwen")));
        assertTrue(chat.abrir(1L).mensagens().getFirst().texto().startsWith("A Última Carta de Junho · Autor · Romance"));
    }

    @Test
    void buscaSemCorrespondenciaInformaOsGenerosSemChamarOModelo() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0)).thenReturn(List.of(
                livro(10L, "Runas", "Fantasia", "45.00", 1), livro(25L, "Café na Rua do Sol", "Romance", "39.00", 1)));
        for (String pergunta : List.of("Vocês têm o livro Duna?", "Quero livros de terror", "Quero livros sobre política")) {
            var mensagem = chat.perguntar(1L, pergunta);
            assertEquals("Não encontrei no catálogo livros que correspondam a esse pedido. No momento, temos livros de "
                    + "Fantasia e Romance. Posso buscar por um desses gêneros, por título ou autor, ou recomendar uma "
                    + "leitura pelo seu perfil.", mensagem.texto(), pergunta);
            assertTrue(mensagem.livros().isEmpty());
            assertEquals("booknexus-catalogo-v1", mensagem.modelo());
        }
        verifyNoInteractions(modelo);
    }

    @Test
    void orientaCupomEFreteSemModeloNemCandidatos() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0))
                .thenReturn(List.of(livro(1L, "O Farol das Runas", "Fantasia", "39.00", 1)));
        var cupom = chat.perguntar(1L, "Como uso meu cupom?");
        assertTrue(cupom.texto().startsWith("Os cupons, promocionais ou de troca, são escolhidos no carrinho"));
        assertEquals(PoliticaChat.VERSAO_LOJA, cupom.modelo());
        assertTrue(chat.perguntar(1L, "Posso trocar O Farol das Runas?").texto().startsWith("Para trocar ou devolver"));
        verifyNoInteractions(modelo, compras);
        verify(respostas, times(2)).save(org.mockito.ArgumentMatchers.argThat(r ->
                r.getResposta().contains("\"livros\":[]")));
    }

    @Test
    void cumprimentaSemRedirecionarNemConsultarCatalogo() {
        assertEquals(PoliticaChat.BOAS_VINDAS, chat.perguntar(1L, "Bom dia!").texto());
        assertEquals(PoliticaChat.AGRADECIMENTO, chat.perguntar(1L, "Obrigado").texto());
        verifyNoInteractions(modelo, produtos, compras);
    }

    @Test
    void respostasFixasNaoEntramComoConversaRecente() {
        when(produtos.findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(0))
                .thenReturn(List.of(livro(10L, "Runas", "Fantasia", "45.00", 1)));
        when(respostas.findTop12ByClienteIdOrderByIdDesc(1L)).thenReturn(List.of(
                new RespostaChat(1L, "Como uso meu cupom?", "{}", "{\"texto\":\"Os cupons…\",\"livros\":[]}",
                        PoliticaChat.VERSAO_LOJA),
                new RespostaChat(1L, "Tem Duna?", "{}", "{\"texto\":\"Não encontrei…\",\"livros\":[]}",
                        "booknexus-catalogo-v1")));
        when(modelo.responder(any())).thenAnswer(invocacao -> {
            assertTrue(json.readTree((String) invocacao.getArgument(0)).path("conversaRecente").isEmpty());
            return new ModeloChat.Saida("Sugiro Runas.", List.of(10L));
        });
        assertEquals(10L, chat.perguntar(1L, "Quero fantasia").livros().getFirst().id());
    }

    private Produto livro(Long id, String titulo, String genero, String preco, int estoque) {
        Produto livro = new Produto();
        livro.setId(id);
        livro.setTitulo(titulo);
        livro.setAutor("Autor");
        livro.setGenero(genero);
        livro.setPreco(new BigDecimal(preco));
        livro.setPaginas(200);
        livro.setEstoque(estoque);
        return livro;
    }
}
