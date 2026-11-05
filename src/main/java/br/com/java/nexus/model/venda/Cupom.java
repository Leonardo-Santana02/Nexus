package br.com.java.nexus.model.venda;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.venda.enums.TipoCupom;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cupons")
public class Cupom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O código do cupom é obrigatório.")
    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @NotNull(message = "O tipo do cupom é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCupom tipo;

    @NotNull(message = "O valor do cupom é obrigatório.")
    @Positive(message = "O valor do cupom deve ser maior que zero.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private Boolean ativo = true;

    @NotNull(message = "A data de validade do cupom é obrigatória.")
    @Column(nullable = false)
    private LocalDateTime dataValidade;

    /*
     * Cupons promocionais podem ser globais,
     * portanto cliente pode ser nulo.
     *
     * Cupons de troca pertencem a um cliente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(length = 255)
    private String descricao;

    @Column(nullable = false)
    private Integer quantidadeUsos = 0;

    private Integer maximoUsos;


    /*
     * ==========================================================
     * CONSTRUTORES
     * ==========================================================
     */

    public Cupom() {
    }

    public Cupom(
            String codigo,
            TipoCupom tipo,
            BigDecimal valor,
            LocalDateTime dataValidade) {

        this.codigo = codigo;
        this.tipo = tipo;
        this.valor = valor;
        this.dataValidade = dataValidade;
    }


    /*
     * ==========================================================
     * REGRAS DE NEGÓCIO
     * ==========================================================
     */

    public boolean isValido() {

        if (!Boolean.TRUE.equals(ativo)) {
            return false;
        }

        if (dataValidade == null
                || !dataValidade.isAfter(
                LocalDateTime.now()
        )) {

            return false;
        }

        if (maximoUsos != null
                && quantidadeUsos != null
                && quantidadeUsos >= maximoUsos) {

            return false;
        }

        return valor != null
                && valor.compareTo(
                BigDecimal.ZERO
        ) > 0;
    }


    public boolean isPromocional() {
        return tipo == TipoCupom.PROMOCIONAL;
    }


    public boolean isTroca() {
        return tipo == TipoCupom.TROCA;
    }


    public boolean pertenceAoCliente(
            Long clienteId) {

        /*
         * Cupom sem cliente é global.
         */
        if (cliente == null) {
            return true;
        }

        return clienteId != null
                && cliente.getId() != null
                && cliente.getId().equals(
                clienteId
        );
    }


    public void consumir() {

        if (!isValido()) {
            throw new IllegalStateException(
                    "Cupom inválido para utilização."
            );
        }

        if (quantidadeUsos == null) {
            quantidadeUsos = 0;
        }

        quantidadeUsos++;

        if (maximoUsos != null
                && quantidadeUsos >= maximoUsos) {

            ativo = false;
        }
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

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {

        if (codigo == null) {
            this.codigo = null;
            return;
        }

        this.codigo =
                codigo.trim().toUpperCase();
    }

    public TipoCupom getTipo() {
        return tipo;
    }

    public void setTipo(TipoCupom tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDateTime getDataValidade() {
        return dataValidade;
    }

    public void setDataValidade(
            LocalDateTime dataValidade) {

        this.dataValidade = dataValidade;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(
            Cliente cliente) {

        this.cliente = cliente;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(
            String descricao) {

        this.descricao = descricao;
    }

    public Integer getQuantidadeUsos() {
        return quantidadeUsos;
    }

    public void setQuantidadeUsos(
            Integer quantidadeUsos) {

        this.quantidadeUsos =
                quantidadeUsos;
    }

    public Integer getMaximoUsos() {
        return maximoUsos;
    }

    public void setMaximoUsos(
            Integer maximoUsos) {

        this.maximoUsos = maximoUsos;
    }
}
