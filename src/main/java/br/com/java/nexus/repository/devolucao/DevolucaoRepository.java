package br.com.java.nexus.repository.devolucao;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.devolucao.Devolucao;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface DevolucaoRepository
        extends JpaRepository<Devolucao, Long> {


    /*
     * ==========================================================
     * BUSCAS SIMPLES
     * ==========================================================
     */

    List<Devolucao>
    findByClienteOrderByDataSolicitacaoDesc(
            Cliente cliente
    );


    List<Devolucao>
    findByClienteIdOrderByDataSolicitacaoDesc(
            Long clienteId
    );


    Optional<Devolucao>
    findFirstByPedidoOrderByDataSolicitacaoDesc(
            Pedido pedido
    );


    boolean
    existsByPedido(
            Pedido pedido
    );


    boolean
    existsByPedidoId(
            Long pedidoId
    );


    /*
     * ==========================================================
     * VALIDAÇÃO DE ITEM JÁ DEVOLVIDO
     * ==========================================================
     *
     * Um mesmo ItemPedido não pode aparecer em duas devoluções
     * ativas (ou seja, cujo pedido esteja em EM_TROCA ou
     * TROCA_AUTORIZADA).
     *
     * Como o status vive no Pedido, filtramos pelos status
     * que indicam devolução em andamento. A lista é passada
     * via parâmetro para evitar strings mágicas no JPQL.
     * ==========================================================
     */

    @Query("""
            SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END
            FROM Devolucao d
            JOIN d.itens i
            WHERE i.itemPedido.id = :itemPedidoId
              AND d.pedido.status IN :statusAtivos
            """)
    boolean
    existsItemEmDevolucaoAtiva(
            @Param("itemPedidoId")
            Long itemPedidoId,

            @Param("statusAtivos")
            List<StatusPedido> statusAtivos
    );


    /*
     * ==========================================================
     * CONSULTAS COM DETALHES (JOIN FETCH)
     * ==========================================================
     */

    /**
     * Busca uma devolução pelo ID, carregando itens, item de pedido,
     * produto, cliente, pedido e cupom gerado.
     */
    @Query("""
            SELECT DISTINCT d
            FROM Devolucao d
            JOIN FETCH d.pedido p
            JOIN FETCH d.cliente c
            LEFT JOIN FETCH d.itens i
            LEFT JOIN FETCH i.itemPedido ip
            LEFT JOIN FETCH ip.produto
            LEFT JOIN FETCH d.cupomGerado
            WHERE d.id = :id
            """)
    Optional<Devolucao>
    findByIdComDetalhes(
            @Param("id")
            Long id
    );


    /**
     * Lista todas as devoluções com itens carregados,
     * ordenadas por data de solicitação decrescente.
     * Usado pelo admin.
     */
    @Query("""
            SELECT DISTINCT d
            FROM Devolucao d
            JOIN FETCH d.pedido p
            JOIN FETCH d.cliente c
            LEFT JOIN FETCH d.itens i
            LEFT JOIN FETCH i.itemPedido ip
            LEFT JOIN FETCH ip.produto
            ORDER BY d.dataSolicitacao DESC
            """)
    List<Devolucao>
    findAllComDetalhes();


    /**
     * Lista as devoluções de um cliente, com itens carregados.
     * Usado pela página do cliente.
     */
    @Query("""
            SELECT DISTINCT d
            FROM Devolucao d
            JOIN FETCH d.pedido p
            JOIN FETCH d.cliente c
            LEFT JOIN FETCH d.itens i
            LEFT JOIN FETCH i.itemPedido ip
            LEFT JOIN FETCH ip.produto
            WHERE c.id = :clienteId
            ORDER BY d.dataSolicitacao DESC
            """)
    List<Devolucao>
    findByClienteIdComDetalhes(
            @Param("clienteId")
            Long clienteId
    );


    /*
     * ==========================================================
     * CONSULTAS POR STATUS DO PEDIDO
     * ==========================================================
     *
     * Como o status vive no Pedido, filtramos por status do
     * pedido para listar devoluções em cada etapa do fluxo.
     * ==========================================================
     */

    @Query("""
            SELECT DISTINCT d
            FROM Devolucao d
            JOIN FETCH d.pedido p
            JOIN FETCH d.cliente c
            LEFT JOIN FETCH d.itens i
            LEFT JOIN FETCH i.itemPedido ip
            LEFT JOIN FETCH ip.produto
            WHERE p.status = :status
            ORDER BY d.dataSolicitacao DESC
            """)
    List<Devolucao>
    findByPedidoStatusComDetalhes(
            @Param("status")
            StatusPedido status
    );


    long
    countByPedidoStatus(
            StatusPedido status
    );


    /*
     * ==========================================================
     * ESTATÍSTICAS PARA O DASHBOARD
     * ==========================================================
     */

    @Query("""
            SELECT p.status, COUNT(d)
            FROM Devolucao d
            JOIN d.pedido p
            GROUP BY p.status
            """)
    List<Object[]>
    contarPorStatusDoPedido();
}