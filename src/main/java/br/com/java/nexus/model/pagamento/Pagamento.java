package br.com.java.nexus.model.pagamento;

import br.com.java.nexus.model.cliente.CartaoCredito;
import br.com.java.nexus.model.pagamento.enums.FormaPagamento;
import br.com.java.nexus.model.pagamento.enums.StatusPagamento;

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
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPagamento formaPagamento =
            FormaPagamento.CARTAO_CREDITO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status =
            StatusPagamento.PENDENTE;

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal valorOriginal =
            BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal valorDesconto =
            BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal valorPago =
            BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime dataCriacao =
            LocalDateTime.now();

    private LocalDateTime dataConfirmacao;

    @Column(length = 500)
    private String motivoRejeicao;

    @Valid
    @OneToMany(
            mappedBy = "pagamento",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PagamentoCartao> cartoesUsados =
            new ArrayList<>();


    public Pagamento() {
    }


    /*
     * ==========================================================
     * CARTÕES
     * ==========================================================
     */

    public void adicionarCartao(
            CartaoCredito cartao,
            BigDecimal valor,
            Integer parcelas) {

        PagamentoCartao pagamentoCartao =
                new PagamentoCartao(
                        this,
                        cartao,
                        valor,
                        parcelas
                );

        cartoesUsados.add(
                pagamentoCartao
        );
    }


    public void removerCartao(
            PagamentoCartao pagamentoCartao) {

        if (pagamentoCartao == null) {
            return;
        }

        cartoesUsados.remove(
                pagamentoCartao
        );

        pagamentoCartao.setPagamento(
                null
        );
    }


    public void limparCartoes() {

        for (PagamentoCartao cartao :
                cartoesUsados) {

            cartao.setPagamento(
                    null
            );
        }

        cartoesUsados.clear();
    }


    /*
     * ==========================================================
     * CÁLCULOS
     * ==========================================================
     */

    public BigDecimal getTotalCartoes() {

        return cartoesUsados.stream()
                .map(
                        PagamentoCartao::getValorPago
                )
                .filter(
                        valor -> valor != null
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


    public BigDecimal getValorAPagar() {

        BigDecimal original =
                valorOriginal != null
                        ? valorOriginal
                        : BigDecimal.ZERO;

        BigDecimal desconto =
                valorDesconto != null
                        ? valorDesconto
                        : BigDecimal.ZERO;

        BigDecimal resultado =
                original.subtract(
                        desconto
                );

        if (resultado.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            return BigDecimal.ZERO
                    .setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        return resultado.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }


    /*
     * ==========================================================
     * STATUS
     * ==========================================================
     */

    public void aprovar() {

        this.status =
                StatusPagamento.APROVADO;

        this.dataConfirmacao =
                LocalDateTime.now();

        this.motivoRejeicao = null;
    }


    public void rejeitar(
            String motivo) {

        this.status =
                StatusPagamento.REJEITADO;

        this.motivoRejeicao =
                motivo;

        this.dataConfirmacao =
                LocalDateTime.now();
    }


    public void cancelar() {

        this.status =
                StatusPagamento.CANCELADO;

        this.dataConfirmacao =
                LocalDateTime.now();
    }


    public boolean isPendente() {

        return status
                == StatusPagamento.PENDENTE;
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

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public void setFormaPagamento(
            FormaPagamento formaPagamento) {

        this.formaPagamento =
                formaPagamento;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public void setStatus(
            StatusPagamento status) {

        this.status = status;
    }

    public BigDecimal getValorOriginal() {
        return valorOriginal;
    }

    public void setValorOriginal(
            BigDecimal valorOriginal) {

        this.valorOriginal =
                normalizarValor(
                        valorOriginal
                );
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public void setValorDesconto(
            BigDecimal valorDesconto) {

        this.valorDesconto =
                normalizarValor(
                        valorDesconto
                );
    }

    public BigDecimal getValorPago() {
        return valorPago;
    }

    public void setValorPago(
            BigDecimal valorPago) {

        this.valorPago =
                normalizarValor(
                        valorPago
                );
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(
            LocalDateTime dataCriacao) {

        this.dataCriacao =
                dataCriacao;
    }

    public LocalDateTime getDataConfirmacao() {
        return dataConfirmacao;
    }

    public void setDataConfirmacao(
            LocalDateTime dataConfirmacao) {

        this.dataConfirmacao =
                dataConfirmacao;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public void setMotivoRejeicao(
            String motivoRejeicao) {

        this.motivoRejeicao =
                motivoRejeicao;
    }

    public List<PagamentoCartao>
    getCartoesUsados() {

        return cartoesUsados;
    }

    public void setCartoesUsados(
            List<PagamentoCartao> cartoesUsados) {

        this.cartoesUsados =
                cartoesUsados != null
                        ? cartoesUsados
                        : new ArrayList<>();
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