package br.com.java.nexus.model.carrinho;

import br.com.java.nexus.model.produto.Produto;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "itens_carrinho")
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Min(
            value = 1,
            message = "A quantidade deve ser maior que zero."
    )
    @Column(nullable = false)
    private Integer quantidade;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;


    /*
     * ==========================================================
     * CONSTRUTORES
     * ==========================================================
     */

    public ItemCarrinho() {
    }

    public ItemCarrinho(
            Produto produto,
            Integer quantidade,
            Carrinho carrinho) {

        this.produto = produto;
        this.quantidade = quantidade;
        this.carrinho = carrinho;
    }


    /*
     * ==========================================================
     * CÁLCULOS
     * ==========================================================
     */

    public BigDecimal getSubtotal() {

        if (produto == null
                || produto.getPreco() == null
                || quantidade == null) {

            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return produto
                .getPreco()
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


    /*
     * ==========================================================
     * GETTERS E SETTERS
     * ==========================================================
     */

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Produto getProduto() {
        return produto;
    }

    public void setProduto(
            Produto produto) {

        this.produto = produto;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(
            Integer quantidade) {

        this.quantidade = quantidade;
    }

    public Carrinho getCarrinho() {
        return carrinho;
    }

    public void setCarrinho(
            Carrinho carrinho) {

        this.carrinho = carrinho;
    }
}