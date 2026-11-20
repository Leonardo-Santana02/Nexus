package br.com.java.nexus.service.chatbot;

import br.com.java.nexus.model.produto.Produto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogoChatTest {
    @Test
    void identificaBuscaSemCorrespondenciaSemBloquearPedidosAbertosOuContinuacoes() {
        var farol = livro(1, "O Farol das Runas", "Fantasia", 144, 39);
        farol.setSubGenero("Magia");
        farol.setSinopse("Uma aprendiz de cartógrafa descobre runas que mudam as rotas de navios.");
        var livros = List.of(farol, livro(25, "Café na Rua do Sol", "Romance", 180, 39));
        for (String pergunta : List.of("Vocês têm o livro Duna?", "Quero livros de terror", "Quem escreveu Duna?",
                "Tem Harry Potter?")) {
            assertTrue(CatalogoChat.semCorrespondencia(livros, pergunta, List.of(), false), pergunta);
        }
        for (String pergunta : List.of("Quero livros sobre navios", "Quero livros de magia", "Quero algo para ler",
                "O que você me recomenda?", "Me indica um livro para presentear", "Tem O Farol das Runas?",
                "Quero romance até R$ 50", "Tem livros?")) {
            assertFalse(CatalogoChat.semCorrespondencia(livros, pergunta, List.of(), false), pergunta);
        }
        assertFalse(CatalogoChat.semCorrespondencia(livros, "Mais barato", List.of(1L), true));
        assertFalse(CatalogoChat.semCorrespondencia(livros, "Do que trata esse?", List.of(1L), false));

        assertTrue(CatalogoChat.buscaPorTema(livros, "Quero livros sobre navios"));
        assertEquals(List.of(1L), CatalogoChat.selecionar(livros, "Quero livros sobre navios", "", List.of(),
                "Romance", "", List.of()).stream().map(Produto::getId).toList());
        assertFalse(CatalogoChat.buscaPorTema(livros, "O que você me recomenda?"));
        assertFalse(CatalogoChat.buscaPorTema(livros, "E o segundo?"));
    }

    @Test
    void generosExplicitosRespeitamPrecoEPaginasMesmoComPreferenciasDiferentes() {
        var livros = List.of(livro(1, "Runas", "Fantasia", 200, 20),
                livro(25, "Café na Rua do Sol", "Romance", 180, 39),
                livro(26, "Carta de Junho", "Romance", 300, 70));
        var selecionados = CatalogoChat.selecionar(livros, "Quero romances até R$ 50 e até 200 páginas",
                "Sugira fantasia", List.of(1L), "Fantasia", "Romance", List.of(livros.getFirst()));
        assertEquals(List.of(25L), selecionados.stream().map(Produto::getId).toList());
    }

    @Test
    void palavraRomanceNoTituloNaoEscondeLivroDeOutroGenero() {
        var fantasia = livro(1, "O Romance das Runas", "Fantasia", 200, 40);
        var romance = livro(25, "Café na Rua do Sol", "Romance", 180, 39);
        assertEquals(1L, CatalogoChat.selecionar(List.of(romance, fantasia),
                "Qual a sinopse de O Romance das Runas?", "", List.of(), "", "", List.of())
                .getFirst().getId());
    }

    @Test
    void aceitaMaisDeUmGeneroENaoInterpretaGeneroNegadoComoPedido() {
        var fantasia = livro(1, "Runas", "Fantasia", 200, 40);
        var romance = livro(25, "Café na Rua do Sol", "Romance", 180, 39);
        var misterio = livro(9, "Cartas do Porto", "Mistério", 190, 40);
        var livros = List.of(fantasia, romance, misterio);
        assertEquals(2, CatalogoChat.selecionar(livros, "Quero romance ou mistério", "", List.of(),
                "Fantasia", "", List.of()).size());
        assertEquals(List.of(25L), CatalogoChat.selecionar(livros,
                "Não quero fantasia, quero romance", "", List.of(), "Fantasia", "", List.of())
                .stream().map(Produto::getId).toList());
    }

    @Test
    void consultaASinopseDaSegundaSugestaoPelaOrdemApresentadaAoCliente() {
        var fantasia = livro(1, "Runas", "Fantasia", 200, 40);
        var romance = livro(25, "Café na Rua do Sol", "Romance", 180, 39);
        String pergunta = "Do que trata o segundo?";
        assertTrue(PoliticaChat.permite(pergunta, List.of(fantasia, romance), true));
        assertEquals(List.of(25L), CatalogoChat.selecionar(List.of(fantasia, romance), pergunta,
                "O que você recomenda?", List.of(1L, 25L), "Fantasia", "", List.of())
                .stream().map(Produto::getId).toList());
    }

    @Test
    void recomendaLeituraNovaDoGeneroDoClienteERespeitaConsultaSobreLivroJaComprado() {
        var comprado = livro(9, "Cartas do Porto", "Mistério", 190, 40);
        var novo = livro(12, "O Último Quarto", "Mistério", 190, 45);
        var outro = livro(25, "Café na Rua do Sol", "Romance", 180, 39);
        var livros = List.of(comprado, novo, outro);
        assertEquals(List.of(novo), CatalogoChat.selecionar(livros, "O que você me recomenda?", "",
                List.of(), "Mistério", "", List.of(comprado)));
        assertEquals(List.of(comprado), CatalogoChat.selecionar(livros, "Qual a sinopse de Cartas do Porto?", "",
                List.of(), "Mistério", "", List.of(comprado)));
    }

    private Produto livro(long id, String titulo, String genero, int paginas, int preco) {
        var livro = new Produto();
        livro.setId(id);
        livro.setTitulo(titulo);
        livro.setAutor("Autor de teste");
        livro.setGenero(genero);
        livro.setPaginas(paginas);
        livro.setPreco(BigDecimal.valueOf(preco));
        livro.setEstoque(10);
        return livro;
    }
}
