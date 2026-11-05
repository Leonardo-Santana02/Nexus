package br.com.java.nexus.repository.produto;

import br.com.java.nexus.model.produto.Produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository
        extends JpaRepository<Produto, Long> {

    /*
     * ==========================================================
     * CONSULTAS
     * ==========================================================
     */

    Optional<Produto> findByIsbn(
            String isbn
    );

    List<Produto> findByAtivoTrueOrderByTituloAsc();

    List<Produto> findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(
            Integer estoque
    );

    List<Produto> findByTituloContainingIgnoreCaseAndAtivoTrueOrderByTituloAsc(
            String titulo
    );


    /*
     * ==========================================================
     * ANÁLISE DE VENDAS (RF0055)
     * ==========================================================
     *
     * Retorna a lista de gêneros (categorias) disponíveis para
     * o admin selecionar no filtro da tela de análise de vendas.
     *
     * Considera apenas gêneros preenchidos (não nulos e não vazios).
     * Não filtra por ativo, pois um produto inativo pode ter sido
     * vendido no passado e sua categoria precisa aparecer na
     * análise histórica.
     * ==========================================================
     */

    @Query("""
            SELECT DISTINCT p.genero
            FROM Produto p
            WHERE p.genero IS NOT NULL
              AND p.genero <> ''
            ORDER BY p.genero ASC
            """)
    List<String> listarGenerosDisponiveis();
}