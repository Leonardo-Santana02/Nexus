package br.com.java.nexus.service.chatbot;

import br.com.java.nexus.model.produto.Produto;

import java.text.Collator;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class RespostaCatalogoChat {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Pattern DETALHES = Pattern.compile(
            "(?iu)\\b(páginas|paginas|editora|isbn|idioma|ano|publica\\p{L}*|ediç\\p{L}*|edi[cç]ão)\\b");

    private RespostaCatalogoChat() {}

    /** Resposta para buscas sem correspondência, com os gêneros que existem de fato no estoque. */
    static String semResultado(List<Produto> estoque) {
        var generos = estoque.stream().map(Produto::getGenero).filter(g -> g != null && !g.isBlank())
                .map(String::strip).distinct().sorted(Collator.getInstance(PT_BR)).toList();
        if (generos.isEmpty()) {
            return "Não encontrei livros disponíveis no catálogo para esse pedido.";
        }
        String lista = generos.size() == 1 ? generos.getFirst()
                : String.join(", ", generos.subList(0, generos.size() - 1)) + " e " + generos.getLast();
        return "Não encontrei no catálogo livros que correspondam a esse pedido. No momento, temos livros de "
                + lista + ". Posso buscar por um desses gêneros, por título ou autor, ou recomendar uma leitura "
                + "pelo seu perfil.";
    }

    static String descrever(String pergunta, List<Produto> livros) {
        return livros.stream().map(livro -> descreverLivro(pergunta, livro)).collect(Collectors.joining("\n\n"));
    }

    private static String descreverLivro(String pergunta, Produto livro) {
        var texto = new StringBuilder(livro.getTitulo());
        acrescentar(texto, " · ", livro.getAutor(), "Autor não informado");
        acrescentar(texto, " · ", livro.getGenero(), "Gênero não informado");
        if (livro.getPreco() != null) {
            texto.append(" · ").append(NumberFormat.getCurrencyInstance(PT_BR).format(livro.getPreco()));
        }
        if (DETALHES.matcher(pergunta).find()) {
            texto.append('\n');
            acrescentar(texto, "Páginas: ", livro.getPaginas(), "não informado");
            acrescentar(texto, " · Idioma: ", livro.getIdioma(), "não informado");
            acrescentar(texto, " · Editora: ", livro.getEditora(), "não informado");
            acrescentar(texto, " · Ano: ", livro.getAnoPublicacao(), "não informado");
            acrescentar(texto, " · ISBN: ", livro.getIsbn(), "não informado");
        }
        acrescentar(texto, "\n", livro.getSinopse(), "Sinopse não informada no catálogo.");
        return texto.toString();
    }

    private static void acrescentar(StringBuilder texto, String separador, Object valor, String ausente) {
        texto.append(separador).append(valor == null || valor.toString().isBlank() ? ausente : valor.toString().strip());
    }
}
