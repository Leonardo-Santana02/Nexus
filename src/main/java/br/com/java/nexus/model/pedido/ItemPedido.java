package br.com.java.nexus.model.pedido;

import br.com.java.nexus.model.produto.Produto;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "itens_pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "pedido_id",
            nullable = false
    )
    private Pedido pedido;

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "produto_id",
            nullable = false
    )
    private Produto produto;

    @Min(
            value = 1,
            message = "A quantidade deve ser maior que zero."
    )
    @Column(nullable = false)
    private int quantidade;

    @NotNull
    @DecimalMin(
            value = "0.01",
            message = "O preço unitário deve ser maior que zero."
    )
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal precoUnitario;


    public ItemPedido() {
    }


    public ItemPedido(
            Produto produto,
            int quantidade,
            BigDecimal precoUnitario) {

        this.produto = produto;
        this.quantidade = quantidade;
        this.precoUnitario =
                normalizarValor(precoUnitario);
    }


    public BigDecimal getPrecoTotal() {

        if (precoUnitario == null
                || quantidade <= 0) {

            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return precoUnitario
                .multiply(
                        BigDecimal.valueOf(
                                quantidade
                        )
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }


    public BigDecimal getPrecoFinal() {
        return getPrecoTotal();
    }


    private BigDecimal normalizarValor(
            BigDecimal valor) {

        if (valor == null) {
            return null;
        }

        return valor.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(
            Pedido pedido) {

        this.pedido = pedido;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(
            Produto produto) {

        this.produto = produto;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(
            int quantidade) {

        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(
            BigDecimal precoUnitario) {

        this.precoUnitario =
                normalizarValor(
                        precoUnitario
                );
    }
}