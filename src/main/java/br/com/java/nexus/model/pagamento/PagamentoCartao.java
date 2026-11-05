package br.com.java.nexus.model.pagamento;

import br.com.java.nexus.model.cliente.CartaoCredito;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "pagamento_cartoes")
public class PagamentoCartao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "pagamento_id",
            nullable = false
    )
    private Pagamento pagamento;

    @NotNull
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "cartao_id",
            nullable = false
    )
    private CartaoCredito cartaoCredito;

    @NotNull
    @DecimalMin(
            value = "0.01",
            message = "O valor pago deve ser maior que zero."
    )
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal valorPago;

    @NotNull
    @Min(
            value = 1,
            message = "O número de parcelas deve ser maior que zero."
    )
    @Column(nullable = false)
    private Integer parcelas = 1;


    public PagamentoCartao() {
    }


    public PagamentoCartao(
            Pagamento pagamento,
            CartaoCredito cartaoCredito,
            BigDecimal valorPago,
            Integer parcelas) {

        this.pagamento = pagamento;
        this.cartaoCredito = cartaoCredito;
        this.valorPago = normalizarValor(valorPago);
        this.parcelas =
                parcelas != null
                        ? parcelas
                        : 1;
    }


    public BigDecimal getValorParcela() {

        if (valorPago == null
                || parcelas == null
                || parcelas <= 0) {

            return BigDecimal.ZERO
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return valorPago.divide(
                BigDecimal.valueOf(parcelas),
                2,
                RoundingMode.HALF_UP
        );
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

    public Pagamento getPagamento() {
        return pagamento;
    }

    public void setPagamento(
            Pagamento pagamento) {

        this.pagamento = pagamento;
    }

    public CartaoCredito getCartaoCredito() {
        return cartaoCredito;
    }

    public void setCartaoCredito(
            CartaoCredito cartaoCredito) {

        this.cartaoCredito = cartaoCredito;
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public void setValorPago(
            BigDecimal valorPago) {

        this.valorPago =
                normalizarValor(valorPago);
    }

    public Integer getParcelas() {
        return parcelas;
    }

    public void setParcelas(
            Integer parcelas) {

        this.parcelas = parcelas;
    }
}