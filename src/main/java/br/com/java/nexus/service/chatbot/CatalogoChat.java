package br.com.java.nexus.service.chatbot;

import br.com.java.nexus.model.produto.Produto;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CatalogoChat {
    private static final Pattern PRECO_MAXIMO = Pattern.compile(
            "(?:ate|no maximo|menos de)\\s*(?:r\\$\\s*(\\d+(?:[.,]\\d{1,2})?)|(\\d+(?:[.,]\\d{1,2})?)\\s*reais)");
    private static final Pattern PAGINAS_MAXIMAS = Pattern.compile(
            "(?:ate|no maximo|menos de)\\s*(\\d{1,6})\\s*paginas");
    private static final Pattern REFERENCIA_ANTERIOR = Pattern.compile(
            "\\b(esse|essa|este|esta|aquele|aquela|primeir[oa]|segund[oa]|terceir[oa]|anterior)\\b");
    private static final Pattern ORDINAL = Pattern.compile("\\b(primeir[oa]|segund[oa]|terceir[oa])\\b");
    private static final Pattern SUGESTAO = Pattern.compile("\\b(?:recomend\\w*|sug(?:er|ir|est)\\w*|indic\\w*|nova leitura|o que ler)\\b");
    private static final Pattern RELEITURA = Pattern.compile("\\b(?:reler|releitura|ler de novo|que ja li)\\b");
    private static final Pattern DUVIDA_OBRA = Pattern.compile(
            "(?:qual (?:e )?(?:o |a )?(?:sinopse|preco|autor|autora|idioma|editora|isbn)|"
            + "quem escreveu|quantas paginas(?: (?:ele |ela )?tem)?|do que (?:trata|fala))"
            + "(?: (?:d[eoae]ss[ea]|d[eoae]st[ea]|do|da|o|a) (?:livro|obra|primeir[oa]|segund[oa]|terceir[oa]))?[ ?.!]*");
    private static final Set<String> PALAVRAS_COMUNS = Set.of("livro", "livros", "queria", "quero",
            "pode", "sobre", "algum", "alguma", "voce", "para", "recomende", "recomenda", "sugira",
            "indica", "buscar", "procuro", "busco", "tem", "uma", "dos", "das", "com", "qual",
            "mais", "quanto", "custa", "reais", "paginas", "preco", "favor", "sinopse", "autor",
            "autora", "estoque", "disponivel", "alguns", "pelo", "indique", "menos", "maximo");
    /** Palavras que não identificam uma obra; uma busca formada só por elas não é tratada como "sem resultado". */
    private static final Set<String> VAGOS = Set.of("voces", "vcs", "algo", "ler", "leitura", "leituras",
            "gostaria", "gosto", "tipo", "obra", "obras", "titulo", "titulos", "nome", "chamado", "chamada",
            "escritor", "escritora", "escreveu", "genero", "generos", "loja", "catalogo", "livraria", "booknexus",
            "existe", "existem", "possui", "possuem", "vende", "vendem", "venda", "hoje", "agora", "ainda",
            "novo", "nova", "novos", "novas", "bom", "boa", "bons", "boas", "legal", "interessante", "outro",
            "outra", "outros", "outras", "tambem", "aqui", "ter", "temos", "tenho", "teria", "teriam", "preciso",
            "procurando", "estou", "encontrar", "achar", "mostrar", "mostre", "ver", "quais", "quem", "como",
            "onde", "quando", "porque", "sim", "nao", "trata", "fala", "historia", "conta", "sinopse", "esse",
            "essa", "este", "esta", "aquele", "aquela", "disponiveis", "barato", "barata", "baratos", "baratas",
            "curto", "curta", "curtos", "curtas", "opcoes", "opcao", "edicao", "idioma", "editora", "isbn", "ano",
            "primeiro", "primeira", "segundo", "segunda", "terceiro", "terceira", "anterior");

    private CatalogoChat() {
    }

    /** A pergunta pede uma sugestão de leitura ou livros de um gênero do catálogo. */
    static boolean pedeLeitura(List<Produto> estoque, String pergunta) {
        return SUGESTAO.matcher(normalizar(pergunta)).find() || !generosSolicitados(estoque, pergunta).isEmpty();
    }

    /** Além de {@link #pedeLeitura}, a pergunta cita um título ou autor do catálogo. */
    static boolean pedeLivro(List<Produto> estoque, String pergunta) {
        String texto = normalizar(pergunta);
        return pedeLeitura(estoque, pergunta)
                || estoque.stream().anyMatch(p -> contemNome(texto, p.getTitulo()) || contemNome(texto, p.getAutor()));
    }

    /**
     * Busca específica cujos termos não aparecem em nenhum título, autor, gênero, subgênero ou sinopse
     * disponível, como "Vocês têm Duna?" ou "Quero livros de terror". Pedidos abertos de sugestão,
     * referências à conversa, filtros de preço ou páginas e continuações seguem para o modelo.
     */
    static boolean semCorrespondencia(List<Produto> estoque, String pergunta, List<Long> indicadosAntes,
                                      boolean continuacao) {
        String texto = normalizar(pergunta);
        if (continuacao || pedeLivro(estoque, pergunta) || PoliticaChat.recomendacaoAberta(pergunta)
                || PRECO_MAXIMO.matcher(texto).find() || PAGINAS_MAXIMAS.matcher(texto).find()
                || (!indicadosAntes.isEmpty() && (REFERENCIA_ANTERIOR.matcher(texto).find()
                        || duvidaSobreObraAnterior(pergunta)))) {
            return false;
        }
        Set<String> pedido = tema(texto);
        return !pedido.isEmpty() && estoque.stream().noneMatch(p -> trataDoTema(p, pedido));
    }

    /**
     * Busca por assunto ("livros sobre navios") sem título, autor ou gênero do catálogo: os candidatos
     * passam a ser as obras cujo título, subgênero ou sinopse contém o assunto pedido.
     */
    static boolean buscaPorTema(List<Produto> estoque, String pergunta) {
        String texto = normalizar(pergunta);
        if (PoliticaChat.recomendacaoAberta(pergunta) || !generosSolicitados(estoque, pergunta).isEmpty()
                || estoque.stream().anyMatch(p -> contemNome(texto, p.getTitulo()) || contemNome(texto, p.getAutor()))) {
            return false;
        }
        Set<String> pedido = tema(texto);
        return !pedido.isEmpty() && estoque.stream().anyMatch(p -> trataDoTema(p, pedido));
    }

    private static Set<String> tema(String textoNormalizado) {
        Set<String> pedido = new HashSet<>(termos(textoNormalizado));
        pedido.removeAll(VAGOS);
        return pedido;
    }

    private static boolean trataDoTema(Produto livro, Set<String> tema) {
        return comuns(tema, termos(livro.getTitulo() + " " + livro.getAutor() + " " + categorias(livro) + " "
                + livro.getSinopse())) > 0;
    }

    static List<Produto> selecionar(List<Produto> estoque, String pergunta, String perguntaAnterior,
                                    List<Long> indicadosAntes, String favoritos, String evitar, List<Produto> compras) {
        String texto = normalizar(pergunta);
        var preco = PRECO_MAXIMO.matcher(texto);
        BigDecimal limite = preco.find()
                ? new BigDecimal((preco.group(1) == null ? preco.group(2) : preco.group(1)).replace(',', '.')) : null;
        var paginas = PAGINAS_MAXIMAS.matcher(texto);
        Integer maxPaginas = paginas.find() ? Integer.valueOf(paginas.group(1)) : null;
        Set<String> pedido = termos(texto);
        Set<String> anterior = termos(perguntaAnterior);
        Set<String> preferidos = termos(favoritos);
        Set<String> evitados = termos(evitar);
        Set<String> generosPedidos = generosSolicitados(estoque, pergunta);
        var titulos = estoque.stream().filter(p -> contemNome(texto, p.getTitulo())).toList();
        var autores = titulos.isEmpty()
                ? estoque.stream().filter(p -> contemNome(texto, p.getAutor())).map(Produto::getAutor).collect(Collectors.toSet())
                : Set.<String>of();
        var ordinal = ORDINAL.matcher(texto);
        Long livroReferenciado = null;
        if (titulos.isEmpty() && autores.isEmpty() && generosPedidos.isEmpty() && ordinal.find()) {
            int posicao = switch (ordinal.group().charAt(0)) {
                case 'p' -> 0;
                case 's' -> 1;
                default -> 2;
            };
            if (posicao < indicadosAntes.size()) {
                livroReferenciado = indicadosAntes.get(posicao);
            }
        } else if (titulos.isEmpty() && autores.isEmpty() && generosPedidos.isEmpty()
                && indicadosAntes.size() == 1
                && (REFERENCIA_ANTERIOR.matcher(texto).find() || duvidaSobreObraAnterior(pergunta))) {
            livroReferenciado = indicadosAntes.getFirst();
        }
        Long idReferenciado = livroReferenciado;
        Set<Long> referenciados = REFERENCIA_ANTERIOR.matcher(texto).find() ? Set.copyOf(indicadosAntes) : Set.of();
        Set<String> generosComprados = new HashSet<>();
        Set<Long> comprados = new HashSet<>();
        compras.forEach(livro -> {
            generosComprados.addAll(termos(categorias(livro)));
            comprados.add(livro.getId());
        });

        var elegiveis = estoque.stream()
                .filter(Produto::estaDisponivel)
                .filter(livro -> titulos.isEmpty() || titulos.contains(livro))
                .filter(livro -> autores.isEmpty() || autores.contains(livro.getAutor()))
                .filter(livro -> idReferenciado == null || idReferenciado.equals(livro.getId()))
                .filter(livro -> generosPedidos.isEmpty()
                        || generosPedidos.contains(normalizar(livro.getGenero())))
                .filter(livro -> limite == null || livro.getPreco().compareTo(limite) <= 0)
                .filter(livro -> maxPaginas == null || livro.getPaginas() <= maxPaginas)
                .toList();
        if (idReferenciado == null && buscaPorTema(estoque, pergunta)) {
            Set<String> assunto = tema(texto);
            var sobreOTema = elegiveis.stream().filter(livro -> trataDoTema(livro, assunto)).toList();
            if (!sobreOTema.isEmpty()) {
                elegiveis = sobreOTema;
            }
        }
        if (titulos.isEmpty() && idReferenciado == null && SUGESTAO.matcher(texto).find()
                && !RELEITURA.matcher(texto).find()) {
            var novos = elegiveis.stream().filter(livro -> !comprados.contains(livro.getId())).toList();
            if (!novos.isEmpty()) {
                elegiveis = novos;
            }
        }
        if (PoliticaChat.recomendacaoAberta(pergunta)) {
            var afins = elegiveis.stream().filter(p -> comuns(preferidos, termos(categorias(p))) > 0).toList();
            if (afins.isEmpty()) {
                afins = elegiveis.stream().filter(p -> comuns(generosComprados, termos(categorias(p))) > 0).toList();
            }
            if (!afins.isEmpty()) {
                elegiveis = afins;
            }
        }
        return elegiveis.stream()
                .map(livro -> new Candidato(livro, pontuar(livro, texto, pedido, anterior, preferidos,
                        evitados, generosComprados, comprados) + (referenciados.contains(livro.getId()) ? 35 : 0)))
                .sorted(Comparator.comparingInt(Candidato::pontos).reversed()
                        .thenComparing(candidato -> candidato.livro().getId()))
                .limit(8)
                .map(Candidato::livro)
                .toList();
    }

    static Set<String> generosSolicitados(List<Produto> estoque, String pergunta) {
        String texto = normalizar(pergunta);
        if (estoque.stream().anyMatch(livro -> contemNome(texto, livro.getTitulo()))) {
            return Set.of();
        }
        return estoque.stream().map(Produto::getGenero).map(CatalogoChat::normalizar)
                .filter(genero -> !genero.isBlank()).filter(genero -> generoSolicitado(texto, genero))
                .collect(Collectors.toSet());
    }

    static boolean generoCompativel(Produto livro, Set<String> generos) {
        return generos.isEmpty() || generos.contains(normalizar(livro.getGenero()));
    }

    static boolean duvidaSobreObraAnterior(String pergunta) {
        return DUVIDA_OBRA.matcher(normalizar(pergunta).strip()).matches();
    }

    static boolean buscaDireta(String pergunta, List<Produto> candidatos, List<Long> indicadosAntes) {
        String texto = normalizar(pergunta);
        return candidatos.stream().anyMatch(p -> contemNome(texto, p.getTitulo()) || contemNome(texto, p.getAutor()))
                || (!indicadosAntes.isEmpty() && (REFERENCIA_ANTERIOR.matcher(texto).find()
                        || duvidaSobreObraAnterior(pergunta)));
    }

    private static boolean generoSolicitado(String texto, String genero) {
        var ocorrencias = Pattern.compile("\\b" + Pattern.quote(genero) + "s?\\b").matcher(texto);
        while (ocorrencias.find()) {
            String antes = texto.substring(0, ocorrencias.start());
            if (!antes.matches("(?s).*\\b(?:sem|evite|evitar|nao (?:quero|gosto de|inclua|recomende))"
                    + "\\s+(?:livros?\\s+(?:de\\s+)?)?$")) {
                return true;
            }
        }
        return false;
    }

    private static boolean contemNome(String texto, String nome) {
        String titulo = normalizar(nome);
        return titulo.length() >= 3 && Pattern.compile("(?<![a-z0-9])" + Pattern.quote(titulo)
                + "(?![a-z0-9])").matcher(texto).find();
    }

    private static int pontuar(Produto livro, String texto, Set<String> pedido, Set<String> anterior,
                               Set<String> preferidos, Set<String> evitados, Set<String> generosComprados,
                               Set<Long> comprados) {
        String titulo = normalizar(livro.getTitulo());
        Set<String> nomes = termos(livro.getTitulo() + " " + livro.getAutor());
        Set<String> generos = termos(categorias(livro));
        Set<String> sinopse = termos(livro.getSinopse());
        int correspondencia = 14 * comuns(pedido, nomes) + 13 * comuns(pedido, generos) + 3 * comuns(pedido, sinopse);
        if (titulo.length() >= 3 && (" " + texto + " ").contains(" " + titulo + " ")) {
            correspondencia += 70;
        }
        Set<String> topicos = new HashSet<>(generos);
        topicos.addAll(sinopse);
        int ignorados = (int) evitados.stream().filter(topicos::contains).filter(termo -> !pedido.contains(termo)).count();
        return correspondencia + 2 * comuns(anterior, nomes)
                + 7 * comuns(preferidos, generos) + 2 * comuns(preferidos, sinopse)
                + 4 * comuns(generosComprados, generos)
                - 15 * ignorados - (comprados.contains(livro.getId()) ? 12 : 0);
    }

    private static String categorias(Produto livro) {
        return livro.getGenero() + " " + (livro.getSubGenero() == null ? "" : livro.getSubGenero());
    }

    private static int comuns(Set<String> primeiro, Set<String> segundo) {
        return (int) primeiro.stream().filter(segundo::contains).count();
    }

    private static Set<String> termos(String texto) {
        return Arrays.stream(normalizar(texto).split("[^a-z]+"))
                .filter(palavra -> palavra.length() >= 3 && !PALAVRAS_COMUNS.contains(palavra))
                .collect(Collectors.toSet());
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private record Candidato(Produto livro, int pontos) {}
}
