package br.com.java.nexus.model.cliente;

import br.com.java.nexus.model.cliente.enums.BandeiraCartao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "cartoes_credito")
public class CartaoCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O número do cartão é obrigatório.")
    @Column(nullable = false, length = 19)
    private String numeroCartao;

    @NotBlank(message = "O nome impresso no cartão é obrigatório.")
    @Column(nullable = false, length = 100)
    private String nomeImpresso;

    @NotNull(message = "A bandeira é obrigatória.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BandeiraCartao bandeira;

    @NotBlank(message = "O código de segurança é obrigatório.")
    @Pattern(
            regexp = "\\d{3,4}",
            message = "O código de segurança deve possuir 3 ou 4 dígitos."
    )
    @Column(nullable = false, length = 4)
    private String codigoSeguranca;

    @Column(nullable = false)
    private boolean preferencial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    public CartaoCredito() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNumeroCartao() {
        return numeroCartao;
    }

    public void setNumeroCartao(String numeroCartao) {
        this.numeroCartao = numeroCartao;
    }

    public String getNomeImpresso() {
        return nomeImpresso;
    }

    public void setNomeImpresso(String nomeImpresso) {
        this.nomeImpresso = nomeImpresso;
    }

    public BandeiraCartao getBandeira() {
        return bandeira;
    }

    public void setBandeira(BandeiraCartao bandeira) {
        this.bandeira = bandeira;
    }

    public String getCodigoSeguranca() {
        return codigoSeguranca;
    }

    public void setCodigoSeguranca(String codigoSeguranca) {
        this.codigoSeguranca = codigoSeguranca;
    }

    public boolean isPreferencial() {
        return preferencial;
    }

    public void setPreferencial(boolean preferencial) {
        this.preferencial = preferencial;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }
}