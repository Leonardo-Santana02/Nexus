package br.com.java.nexus.model.cliente;

import br.com.java.nexus.model.cliente.enums.TipoTelefone;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Entity
@Table(name = "telefones")
public class Telefone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "O tipo do telefone é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTelefone tipo;

    @NotBlank(message = "O DDD é obrigatório.")
    @Pattern(regexp = "\\d{2}", message = "O DDD deve possuir 2 dígitos.")
    @Column(nullable = false, length = 2)
    private String ddd;

    @NotBlank(message = "O número do telefone é obrigatório.")
    @Column(nullable = false, length = 15)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    public Telefone() {
    }

    public Telefone(
            TipoTelefone tipo,
            String ddd,
            String numero) {

        this.tipo = tipo;
        this.ddd = ddd;
        this.numero = numero;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoTelefone getTipo() {
        return tipo;
    }

    public void setTipo(TipoTelefone tipo) {
        this.tipo = tipo;
    }

    public String getDdd() {
        return ddd;
    }

    public void setDdd(String ddd) {
        this.ddd = ddd;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }
}