package br.com.java.nexus.repository.chatbot;

import br.com.java.nexus.model.pedido.ItemPedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;
import br.com.java.nexus.model.produto.Produto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ComprasLeituraRepository extends Repository<ItemPedido, Long> {
    @Query("""
            SELECT i.produto FROM ItemPedido i
            WHERE i.pedido.cliente.id = :cliente AND i.pedido.status IN :status
            AND NOT EXISTS (
                SELECT d.id FROM ItemDevolucao d
                WHERE d.itemPedido = i AND d.devolucao.dataRecebimento IS NOT NULL
            )
            ORDER BY i.pedido.dataCriacao DESC, i.id DESC
            """)
    List<Produto> livrosComprados(@Param("cliente") Long cliente,
                                 @Param("status") Collection<StatusPedido> status, Pageable limite);
}
