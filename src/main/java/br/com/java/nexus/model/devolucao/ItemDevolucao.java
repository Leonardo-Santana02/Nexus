package br.com.java.nexus.model.devolucao;

import br.com.java.nexus.model.pedido.ItemPedido;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "itens_devolucao")
public class ItemDevolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "devolucao_id", nullable = false)
    private Devolucao devolucao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "item_pedido_id", nullable = false)
    private ItemPedido itemPedido;

    @Column(nullable = false)
    private Integer quantidade;

    /**
     * Valor unitário congelado no momento da solicitação.
     * Garante que alterações futuras no preço do produto não afetem
     * o histórico da devolução.
     */
    @Column(name = "valor_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    /**
     * Informado pelo admin no momento da confirmação de recebimento.
     * Indica se o item deve retornar ao estoque.
     *
     * RF0044: o admin deve informar, por item, se ele volta ao estoque.
     */
    @Column(name = "devolver_ao_estoque")
    private Boolean devolverAoEstoque;

    @Column(length = 500)
    private String observacao;

    // ============================================================
    // CONSTRUTORES
    // ============================================================

    public ItemDevolucao() {
    }

    public ItemDevolucao(ItemPedido itemPedido,
                         Integer quantidade,
                         BigDecimal valorUnitario) {
        this.itemPedido = itemPedido;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
    }

    // ============================================================
    // MÉTODOS DE NEGÓCIO
    // ============================================================

    /**
     * Valor total deste item (quantidade x valor unitário).
     */
    public BigDecimal getValorTotal() {
        if (valorUnitario == null || quantidade == null) {
            return BigDecimal.ZERO;
        }
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    /**
     * Indica se este item deve voltar ao estoque.
     * Retorna false se ainda não foi decidido pelo admin.
     */
    public boolean deveVoltarAoEstoque() {
        return Boolean.TRUE.equals(devolverAoEstoque);
    }

    // ============================================================
    // GETTERS E SETTERS
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Devolucao getDevolucao() {
        return devolucao;
    }

    public void setDevolucao(Devolucao devolucao) {
        this.devolucao = devolucao;
    }

    public ItemPedido getItemPedido() {
        return itemPedido;
    }

    public void setItemPedido(ItemPedido itemPedido) {
        this.itemPedido = itemPedido;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(BigDecimal valorUnitario) {
        this.valorUnitario = valorUnitario;
    }

    public Boolean getDevolverAoEstoque() {
        return devolverAoEstoque;
    }

    public void setDevolverAoEstoque(Boolean devolverAoEstoque) {
        this.devolverAoEstoque = devolverAoEstoque;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}