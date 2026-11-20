package br.com.java.nexus.service.chatbot;

import br.com.java.nexus.model.produto.Produto;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Restringe a entrada antes de enviar dados do cliente ao modelo local. */
final class PoliticaChat {
    static final String VERSAO = "booknexus-escopo-v1";
    /** Orientações fixas sobre o fluxo de compra; não consultam o modelo nem enviam candidatos. */
    static final String VERSAO_LOJA = "booknexus-loja-v1";
    static final String REDIRECIONAMENTO = "Sou o assistente de livros do BookNexus. "
            + "Posso ajudar com o catálogo, sugestões de leitura e orientações de compra de livros. "
            + "Qual livro, autor ou gênero você procura?";
    static final String BOAS_VINDAS = "Olá! Sou o assistente de livros do BookNexus. Posso recomendar leituras, "
            + "buscar títulos, autores ou gêneros e tirar dúvidas sobre as obras do catálogo. O que você procura?";
    static final String AGRADECIMENTO = "Por nada! Quando quiser, peça uma recomendação ou pergunte sobre "
            + "um título, autor ou gênero do catálogo.";
    private static final Pattern SAUDACAO = Pattern.compile(
            "(?:(?:oi|ola|opa|bom dia|boa tarde|boa noite|e ai|tudo bem|tudo bom|como vai)\\s*)+"
            + "(?:assistente|booknexus)?");
    private static final Pattern AGRADECIDO = Pattern.compile(
            "(?:(?:muito )?obrigad[oa]s?|valeu|ate mais|tchau)(?: (?:pela ajuda|assistente))?");
    private static final Pattern BUSCA_GERAL = Pattern.compile(
            "^(?:(?:voces|voce|a loja) )?(?:tem|teria|teriam|vende|vendem|possui|possuem|procuro|busco"
            + "|estou procurando)\\b");
    /** Tópico normalizado → orientação, na ordem em que aparece quando a pergunta cita mais de um. */
    private static final Map<Pattern, String> ORIENTACOES = new LinkedHashMap<>();

    static {
        ORIENTACOES.put(Pattern.compile("\\b(?:troca|trocar|devolu\\w*|devolver)\\b"),
                "Para trocar ou devolver um livro, abra Minha conta, entre nos detalhes de um pedido entregue e use "
                + "Solicitar troca, escolhendo os itens. Depois da análise e do recebimento, o valor pode ser "
                + "convertido em cupom de troca.");
        ORIENTACOES.put(Pattern.compile("\\b(?:cupom|cupons|desconto)\\b"),
                "Os cupons, promocionais ou de troca, são escolhidos no carrinho, na seleção de cupons. O desconto "
                + "aparece no resumo da compra, e o próprio carrinho confere se o cupom pode ser usado.");
        ORIENTACOES.put(Pattern.compile("\\b(?:frete|cep|endereco|enderecos)\\b"),
                "No carrinho, selecione ou cadastre o endereço de entrega. O frete é calculado a partir desse "
                + "endereço e aparece no resumo antes do pagamento.");
        ORIENTACOES.put(Pattern.compile("\\b(?:pagamento|pagar|cartao|cartoes|parcel\\w*)\\b"),
                "Na página de pagamento, selecione um ou mais cartões cadastrados e distribua o valor entre eles. "
                + "A soma precisa ser igual ao total do pedido.");
        ORIENTACOES.put(Pattern.compile("\\b(?:pedido|pedidos|entrega|status|rastre\\w*)\\b"),
                "Em Minha conta, a lista de pedidos mostra o status de cada compra e os detalhes de itens, frete, "
                + "pagamento e endereço.");
        ORIENTACOES.put(Pattern.compile("\\b(?:carrinho|checkout|finalizar)\\b"),
                "Use Adicionar ao carrinho na vitrine. No carrinho, ajuste as quantidades, escolha o endereço, "
                + "o frete e os cupons e siga para o pagamento.");
    }
    private static final Pattern LITERATURA = Pattern.compile(
            "\\b(livros?|leituras?|ler|literatur\\w*|literari\\w*|autores?|autoras?|escritores?|"
            + "escritoras?|sinopses?|isbn|editoras?|romances?|fantasia|misterio|suspense|terror|"
            + "poesia|poemas?|contos?|biografias?|ficcao|quadrinhos?|mangas?|infantil|juvenil)\\b");
    private static final Pattern LIVRO_EXPLICITO = Pattern.compile(
            "\\b(livros?|leituras?|ler|literatur\\w*|literari\\w*|sinopses?|isbn|"
            + "romances?|biografias?|contos?|poemas?|quadrinhos?|mangas?)\\b");
    private static final Pattern LOJA = Pattern.compile(
            "\\b(carrinho|checkout|cupom|cupons|frete|pagamento|cartao|cartoes|pedidos?|"
            + "devolu\\w*|devolver|troca|trocar|comprar|compra|entrega|endereco|estoque|preco|precos|catalogo|"
            + "desconto|cep|pagar|parcel\\w*|finalizar)\\b");
    private static final Pattern DESVIO = Pattern.compile(
            "\\b(carros?|automoveis|veiculos?|motos?|celular(?:es)?|notebooks?|politic\\w*|"
            + "eleic\\w*|partidos?|presidente|governo|votar|futebol|jogos?|receitas?|programa\\w*|"
            + "codigo|python|javascript|matematica|clima|horoscopo|medicamentos?)\\b");
    private static final Pattern INSTRUCAO = Pattern.compile(
            "\\b(ignore|ignorar|esqueca|desconsidere|revele|mostre|altere|substitua)\\b.{0,80}"
            + "\\b(instrucoes|regras|prompt|sistema)\\b|\\b(fin[gj]a|atue|aja)\\s+(que|como)\\b|"
            + "<\\|(?:im_start|system)|\\b(role|system)\\s*:");
    private static final Pattern BUSCA_LIVRO = Pattern.compile(
            "\\b(?:qual|quais|tem|procuro|busco|quero|recomende|sugira)\\b.{0,30}\\b(?:livros?|leituras?)\\b");
    private static final Pattern RECOMENDACAO = Pattern.compile(
            "(?:o que (?:voce )?(?:me )?(?:recomenda|indica|sugere)|"
            + "(?:pode |voce pode )?(?:me )?(?:recomendar|recomenda|recomende|indique|sugira|sugerir))"
            + "(?: com base no que (?:eu )?ja comprei)?");
    private static final Pattern CONTINUACAO = Pattern.compile(
            "(?:sim|nao|pode ser|obrigad[oa]|outr[oa]s?|mais (?:opcoes|barat[oa]s?|curt[oa]s?)|"
            + "(?:quero |tem |mostre |prefiro )?(?:outr[oa]s?|mais barat[oa]s?|mais curt[oa]s?)|"
            + "(?:e )?(?:o |a )?(?:primeir[oa]|segund[oa]|terceir[oa])|"
            + "(?:quanto custa|qual (?:o )?preco|qual (?:a )?sinopse|quem (?:e )?(?:o )?autor)"
            + "(?: d?[oea]s? (?:primeir[oa]|segund[oa]|terceir[oa]|livro))?|"
            + "(?:ate|menos de|no maximo) (?:r )?\\d+(?: \\d+)?(?: reais| paginas)?)");
    private static final Pattern ABERTURA_REPETIDA = Pattern.compile(
            "(?iu)^[\\s\"'“”‘’]*(?:pergunta\\s*:\\s*)?o que (?:voc[eê] )?(?:me )?"
            + "(?:recomenda|indica|sugere)\\s*\\?[\\s\"'“”‘’:—-]*");

    private PoliticaChat() {}

    static boolean permite(String pergunta, List<Produto> catalogo, boolean conversaSobreLivros) {
        String texto = normalizar(pergunta);
        if (INSTRUCAO.matcher(texto).find() || desvia(texto)) {
            return false;
        }
        if (LITERATURA.matcher(texto).find() || LOJA.matcher(texto).find()) {
            return true;
        }
        String simples = texto.replaceAll("[^a-z0-9]+", " ").strip();
        // "Vocês têm Duna?" é uma busca na livraria, mesmo sem a palavra livro.
        if (BUSCA_GERAL.matcher(simples).find()) {
            return true;
        }
        if (RECOMENDACAO.matcher(simples).matches()
                || (conversaSobreLivros && (CONTINUACAO.matcher(simples).matches()
                        || CatalogoChat.duvidaSobreObraAnterior(pergunta)))) {
            return true;
        }
        return catalogo.stream().anyMatch(livro -> contemNome(texto, livro.getTitulo())
                || contemNome(texto, livro.getAutor()) || contemNome(texto, livro.getGenero())
                || contemNome(texto, livro.getSubGenero()));
    }

    /** Cumprimentos e agradecimentos isolados recebem resposta cordial, sem consultar o modelo. */
    static Optional<String> cortesia(String pergunta) {
        String simples = simplificar(pergunta);
        if (SAUDACAO.matcher(simples).matches()) {
            return Optional.of(BOAS_VINDAS);
        }
        return AGRADECIDO.matcher(simples).matches() ? Optional.of(AGRADECIMENTO) : Optional.empty();
    }

    static boolean continuacao(String pergunta) {
        return CONTINUACAO.matcher(simplificar(pergunta)).matches();
    }

    /**
     * Dúvidas sobre carrinho, cupom, frete, pagamento, pedido ou troca recebem orientação fixa
     * do fluxo da loja, mesmo quando citam um título ("Posso trocar O Farol das Runas?").
     * Buscas de livros, recomendações e pedidos de gênero seguem para o catálogo.
     */
    static Optional<String> orientacaoLoja(String pergunta, boolean pedeLeitura) {
        String texto = normalizar(pergunta);
        if (pedeLeitura || BUSCA_LIVRO.matcher(texto).find()
                || recomendacaoAberta(pergunta)) {
            return Optional.empty();
        }
        String orientacao = ORIENTACOES.entrySet().stream().filter(e -> e.getKey().matcher(texto).find())
                .map(Map.Entry::getValue).distinct().limit(2).collect(Collectors.joining("\n\n"));
        return orientacao.isBlank() ? Optional.empty()
                : Optional.of(orientacao + "\n\nO chat não executa essas operações; elas são feitas nas próprias telas da loja.");
    }

    static boolean recomendacaoAberta(String pergunta) {
        String texto = normalizar(pergunta).replaceAll("[^a-z0-9]+", " ").strip();
        return RECOMENDACAO.matcher(texto).matches()
                || texto.matches("(?:recomende|indique|sugira)(?: (?:um|alguns))? livros?");
    }

    static boolean desvia(String valor) {
        String texto = normalizar(valor);
        // Uma referência a um livro não autoriza uma segunda solicitação sobre outro assunto.
        for (String trecho : texto.split("[.!?;\\n]+|\\b(?:e tambem|alem disso|agora|mas)\\b|"
                + "\\be (?=(?:me )?(?:diga|explique|conte|fale|responda|quem|qual|como)\\b)")) {
            var tema = DESVIO.matcher(trecho);
            var obra = LIVRO_EXPLICITO.matcher(trecho);
            if (tema.find() && (!obra.find()
                    || (obra.start() > tema.start() && !BUSCA_LIVRO.matcher(trecho).find()))) {
                return true;
            }
        }
        return false;
    }

    static ModeloChat.Saida ajustar(ModeloChat.Saida saida, String pergunta) {
        if (saida == null || saida.texto() == null || saida.texto().isBlank()) {
            return saida;
        }
        String texto = saida.texto().strip();
        String anterior;
        do {
            anterior = texto;
            texto = ABERTURA_REPETIDA.matcher(texto).replaceFirst("").strip();
            var eco = Pattern.compile("(?iu)^[\\s\"'“”‘’]*(?:pergunta\\s*:\\s*)?"
                    + Pattern.quote(pergunta.strip()) + "[\\s\"'“”‘’:—-]*").matcher(texto);
            texto = eco.replaceFirst("").strip();
        } while (!texto.equals(anterior));
        if (texto.isBlank()) {
            texto = saida.livros() != null && !saida.livros().isEmpty()
                    ? "Estas são as opções do catálogo para sua busca."
                    : "Qual gênero, autor ou título você gostaria de encontrar no catálogo?";
        }
        return new ModeloChat.Saida(texto, saida.livros());
    }

    private static boolean contemNome(String texto, String nome) {
        String termo = normalizar(nome).strip();
        return termo.length() >= 3 && Pattern.compile("(?<![a-z0-9])" + Pattern.quote(termo)
                + "(?![a-z0-9])").matcher(texto).find();
    }

    private static String simplificar(String valor) {
        return normalizar(valor).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
