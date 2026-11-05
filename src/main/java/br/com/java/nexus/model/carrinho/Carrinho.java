package br.com.java.nexus.model.carrinho;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.produto.Produto;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(
        name = "carrinhos",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_carrinho_cliente",
                        columnNames = "cliente_id"
                )
        }
)
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "cliente_id",
            nullable = false,
            unique = true
    )
    private Cliente cliente;

    @OneToMany(
            mappedBy = "carrinho",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ItemCarrinho> itens =
            new ArrayList<>();


    /*
     * ==========================================================
     * ITENS
     * ==========================================================
     */

    public void adicionarItem(
            Produto produto,
            Integer quantidade) {

        Optional<ItemCarrinho> existente =
                buscarItemPorProduto(
                        produto.getId()
                );

        if (existente.isPresent()) {

            ItemCarrinho item =
                    existente.get();

            item.setQuantidade(
                    item.getQuantidade()
                            + quantidade
            );

            return;
        }

        ItemCarrinho item =
                new ItemCarrinho(
                        produto,
                        quantidade,
                        this
                );

        itens.add(item);
    }


    public void alterarQuantidadeItem(
            Long produtoId,
            Integer novaQuantidade) {

        ItemCarrinho item =
                buscarItemPorProduto(
                        produtoId
                )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Item não encontrado no carrinho."
                                )
                        );

        item.setQuantidade(
                novaQuantidade
        );
    }


    public void removerItem(
            Long produtoId) {

        itens.removeIf(
                item ->
                        item.getProduto()
                                .getId()
                                .equals(produtoId)
        );
    }


    public Optional<ItemCarrinho>
    buscarItemPorProduto(
            Long produtoId) {

        if (produtoId == null) {
            return Optional.empty();
        }

        return itens.stream()
                .filter(
                        item ->
                                item.getProduto() != null
                                        && produtoId.equals(
                                        item.getProduto()
                                                .getId()
                                )
                )
                .findFirst();
    }


    public void limpar() {
        itens.clear();
    }


    /*
     * ==========================================================
     * CÁLCULOS
     * ==========================================================
     */

    public BigDecimal getSubtotal() {

        return itens.stream()
                .map(
                        ItemCarrinho::getSubtotal
                )
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }


    public Integer getQuantidadeItens() {

        return itens.stream()
                .mapToInt(
                        item ->
                                item.getQuantidade() != null
                                        ? item.getQuantidade()
                                        : 0
                )
                .sum();
    }


    public boolean isVazio() {
        return itens.isEmpty();
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

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(
            Cliente cliente) {

        this.cliente = cliente;
    }

    public List<ItemCarrinho> getItens() {
        return itens;
    }

    public void setItens(
            List<ItemCarrinho> itens) {

        this.itens =
                itens != null
                        ? itens
                        : new ArrayList<>();
    }
}