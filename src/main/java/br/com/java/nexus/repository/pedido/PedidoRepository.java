package br.com.java.nexus.repository.pedido;

import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface PedidoRepository
        extends JpaRepository<Pedido, Long> {


    /*
     * ==========================================================
     * CONSULTAS SIMPLES
     * ==========================================================
     */

    List<Pedido>
    findByClienteIdOrderByDataCriacaoDesc(
            Long clienteId
    );


    List<Pedido>
    findByStatusOrderByDataCriacaoDesc(
            StatusPedido status
    );


    List<Pedido>
    findAllByOrderByDataCriacaoDesc();


    /*
     * ==========================================================
     * PEDIDO COM DETALHES COMPLETOS
     * ==========================================================
     *
     * Utilizado pelo endpoint de detalhes do admin.
     *
     * Carrega simultaneamente:
     * - cliente
     * - itens
     * - produto de cada item
     * - endereço de entrega
     * - pagamento
     *
     * Dessa forma, o controller consegue montar a resposta
     * completa sem risco de LazyInitializationException.
     * ==========================================================
     */

    @Query("""
            SELECT DISTINCT p
            FROM Pedido p
            JOIN FETCH p.cliente c
            LEFT JOIN FETCH p.itens i
            LEFT JOIN FETCH i.produto
            LEFT JOIN FETCH p.enderecoEntrega
            LEFT JOIN FETCH p.pagamento
            WHERE p.id = :pedidoId
            """)
    Optional<Pedido>
    findByIdComDetalhes(
            @Param("pedidoId")
            Long pedidoId
    );


    /*
     * ==========================================================
     * PEDIDO COMPLETO DO CLIENTE
     * ==========================================================
     *
     * Utilizado pelo pop-up de detalhes.
     *
     * A consulta utiliza simultaneamente:
     *
     * - ID do pedido;
     * - ID do cliente.
     *
     * Dessa forma, um cliente não consegue consultar um pedido
     * pertencente a outro cliente apenas alterando o ID.
     * ==========================================================
     */

    @Query("""
            SELECT DISTINCT p
            FROM Pedido p
            JOIN FETCH p.cliente c
            LEFT JOIN FETCH p.itens i
            LEFT JOIN FETCH i.produto
            LEFT JOIN FETCH p.enderecoEntrega
            LEFT JOIN FETCH p.pagamento
            WHERE p.id = :pedidoId
              AND c.id = :clienteId
            """)
    Optional<Pedido>
    findByIdEClienteIdComDetalhes(
            @Param("pedidoId")
            Long pedidoId,

            @Param("clienteId")
            Long clienteId
    );


    /*
     * ==========================================================
     * PEDIDOS DO CLIENTE
     * ==========================================================
     *
     * Carrega os itens e os produtos para evitar problemas
     * com relacionamentos LAZY na página Thymeleaf.
     * ==========================================================
     */

    @Query("""
            SELECT DISTINCT p
            FROM Pedido p
            LEFT JOIN FETCH p.itens i
            LEFT JOIN FETCH i.produto
            WHERE p.cliente.id = :clienteId
            ORDER BY p.dataCriacao DESC
            """)
    List<Pedido>
    findByClienteIdComItens(
            @Param("clienteId")
            Long clienteId
    );


    /*
     * ==========================================================
     * LISTAGEM PAGINADA PARA O ADMIN
     * ==========================================================
     *
     * Utilizada pela tela /admin/pedidos.
     *
     * Filtros:
     * - status (opcional)
     * - busca textual por nome do cliente, e-mail ou ID do pedido
     *
     * O @EntityGraph garante que cliente, itens, produtos e
     * pagamento sejam carregados na mesma consulta, evitando
     * o problema de N+1 e LazyInitializationException.
     *
     * A contagem para paginação é feita automaticamente pelo
     * Spring Data, sem necessidade de countQuery separada,
     * porque o @EntityGraph não interfere no COUNT.
     * ==========================================================
     */

    @EntityGraph(attributePaths = {
            "cliente",
            "itens",
            "itens.produto",
            "pagamento"
    })
    @Query("""
            SELECT DISTINCT p
            FROM Pedido p
            JOIN p.cliente c
            WHERE (:status IS NULL OR p.status = :status)
              AND (:busca IS NULL OR :busca = ''
                   OR LOWER(c.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :busca, '%'))
                   OR CAST(p.id AS string) LIKE CONCAT('%', :busca, '%'))
            ORDER BY p.dataCriacao DESC
            """)
    Page<Pedido>
    buscarComFiltros(
            @Param("busca")
            String busca,

            @Param("status")
            StatusPedido status,

            Pageable pageable
    );


    /*
     * ==========================================================
     * CONTAGENS PARA KPIs
     * ==========================================================
     */

    @Query("""
            SELECT COUNT(p)
            FROM Pedido p
            WHERE p.dataCriacao >= :inicioDia
            """)
    long
    contarPedidosAPartirDe(
            @Param("inicioDia")
            LocalDateTime inicioDia
    );


    long
    countByStatus(
            StatusPedido status
    );


    /*
     * ==========================================================
     * ANÁLISE DE VENDAS (RF0055)
     * ==========================================================
     *
     * Busca todos os pedidos com um status específico dentro do
     * período informado, carregando itens e produtos.
     *
     * Usado pelo AnaliseVendasService para calcular o valor
     * das vendas por categoria e por mês.
     *
     * O JOIN FETCH evita N+1 e LazyInitializationException ao
     * acessar pedido.getItens() e item.getProduto() fora da
     * transação.
     *
     * Não filtra por categoria aqui: o rateio proporcional de
     * desconto e frete depende do total do pedido inteiro, então
     * o filtro por categoria é feito no service, item a item.
     * ==========================================================
     */

    @Query("""
            SELECT DISTINCT p
            FROM Pedido p
            LEFT JOIN FETCH p.itens i
            LEFT JOIN FETCH i.produto
            WHERE p.status = :status
              AND p.dataCriacao BETWEEN :inicio AND :fim
            ORDER BY p.dataCriacao ASC
            """)
    List<Pedido>
    buscarEntreguesPorPeriodoComItens(
            @Param("status")
            StatusPedido status,

            @Param("inicio")
            LocalDateTime inicio,

            @Param("fim")
            LocalDateTime fim
    );
}