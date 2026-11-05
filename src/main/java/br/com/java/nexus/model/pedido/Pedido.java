package br.com.java.nexus.model.pedido;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.pagamento.Pagamento;
import br.com.java.nexus.model.pedido.enums.StatusPedido;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "cliente_id",
            nullable = false
    )
    private Cliente cliente;

    @Valid
    @OneToMany(
            mappedBy = "pedido",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ItemPedido> itens =
            new ArrayList<>();

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "endereco_entrega_id",
            nullable = false
    )
    private Endereco enderecoEntrega;

    @NotNull
    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "pagamento_id",
            nullable = false,
            unique = true
    )
    private Pagamento pagamento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status =
            StatusPedido.EM_PROCESSAMENTO;

    @Column(
            nullable = false,
            updatable = false
    )
    private LocalDateTime dataCriacao =
            LocalDateTime.now();

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal subtotal =
            BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal descontoPromocional =
            BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal valorFrete =
            BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal valorTotal =
            BigDecimal.ZERO;

    @Column(length = 100)
    private String codigoCupomPromocional;


    public Pedido() {
    }


    /*
     * ==========================================================
     * ITENS
     * ==========================================================
     */

    public void adicionarItem(
            ItemPedido item) {

        if (item == null) {
            return;
        }

        itens.add(item);
        item.setPedido(this);

        recalcularValores();
    }


    public void removerItem(
            ItemPedido item) {

        if (item == null) {
            return;
        }

        itens.remove(item);
        item.setPedido(null);

        recalcularValores();
    }


    public boolean temItens() {

        return itens != null
                && !itens.isEmpty();
    }


    public int getQuantidadeTotalItens() {

        if (!temItens()) {
            return 0;
        }

        return itens.stream()
                .mapToInt(
                        ItemPedido::getQuantidade
                )
                .sum();
    }


    /*
     * ==========================================================
     * CÁLCULOS
     * ==========================================================
     */

    public BigDecimal calcularSubtotal() {

        if (!temItens()) {
            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return itens.stream()
                .map(
                        ItemPedido::getPrecoTotal
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


    public BigDecimal calcularTotal() {

        BigDecimal subtotalAtual =
                subtotal != null
                        ? subtotal
                        : BigDecimal.ZERO;

        BigDecimal desconto =
                descontoPromocional != null
                        ? descontoPromocional
                        : BigDecimal.ZERO;

        BigDecimal frete =
                valorFrete != null
                        ? valorFrete
                        : BigDecimal.ZERO;

        return subtotalAtual
                .subtract(desconto)
                .add(frete)
                .max(BigDecimal.ZERO)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }


    public void recalcularValores() {

        this.subtotal =
                calcularSubtotal();

        this.valorTotal =
                calcularTotal();
    }


    /*
     * ==========================================================
     * VALIDAÇÃO JPA
     * ==========================================================
     */

    @PrePersist
    @PreUpdate
    private void prepararPersistencia() {

        if (!temItens()) {
            throw new IllegalStateException(
                    "O pedido deve possuir pelo menos um item."
            );
        }

        if (status == null) {
            status =
                    StatusPedido.EM_PROCESSAMENTO;
        }

        if (dataCriacao == null) {
            dataCriacao =
                    LocalDateTime.now();
        }

        recalcularValores();
    }


    /*
     * ==========================================================
     * GETTERS E SETTERS
     * ==========================================================
     */

    public Long getId() {
        return id;
    }

    public void setId(
            Long id) {

        this.id = id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(
            Cliente cliente) {

        this.cliente = cliente;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(
            List<ItemPedido> itens) {

        this.itens =
                itens != null
                        ? itens
                        : new ArrayList<>();

        for (ItemPedido item :
                this.itens) {

            item.setPedido(this);
        }

        recalcularValores();
    }

    public Endereco getEnderecoEntrega() {
        return enderecoEntrega;
    }

    public void setEnderecoEntrega(
            Endereco enderecoEntrega) {

        this.enderecoEntrega =
                enderecoEntrega;
    }

    public Pagamento getPagamento() {
        return pagamento;
    }

    public void setPagamento(
            Pagamento pagamento) {

        this.pagamento = pagamento;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(
            StatusPedido status) {

        this.status = status;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(
            LocalDateTime dataCriacao) {

        this.dataCriacao =
                dataCriacao;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(
            BigDecimal subtotal) {

        this.subtotal =
                normalizarValor(
                        subtotal
                );

        this.valorTotal =
                calcularTotal();
    }

    public BigDecimal getDescontoPromocional() {
        return descontoPromocional;
    }

    public void setDescontoPromocional(
            BigDecimal descontoPromocional) {

        this.descontoPromocional =
                normalizarValor(
                        descontoPromocional
                );

        this.valorTotal =
                calcularTotal();
    }

    public BigDecimal getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(
            BigDecimal valorFrete) {

        this.valorFrete =
                normalizarValor(
                        valorFrete
                );

        this.valorTotal =
                calcularTotal();
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(
            BigDecimal valorTotal) {

        this.valorTotal =
                normalizarValor(
                        valorTotal
                );
    }

    public String getCodigoCupomPromocional() {
        return codigoCupomPromocional;
    }

    public void setCodigoCupomPromocional(
            String codigoCupomPromocional) {

        this.codigoCupomPromocional =
                codigoCupomPromocional;
    }


    private BigDecimal normalizarValor(
            BigDecimal valor) {

        if (valor == null) {
            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return valor.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}