package br.com.java.nexus.model.cliente;

import br.com.java.nexus.model.cliente.enums.Genero;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O nome é obrigatório.")
    @Size(max = 150, message = "O nome deve possuir no máximo 150 caracteres.")
    @Column(nullable = false, length = 150)
    private String nome;

    @NotBlank(message = "O CPF é obrigatório.")
    @Pattern(
            regexp = "\\d{11}",
            message = "O CPF deve possuir 11 dígitos."
    )
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @NotBlank(message = "O e-mail é obrigatório.")
    @Email(message = "Informe um e-mail válido.")
    @Size(max = 100, message = "O e-mail deve possuir no máximo 100 caracteres.")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotNull(message = "O gênero é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Genero genero;

    @NotNull(message = "A data de nascimento é obrigatória.")
    @Past(message = "A data de nascimento deve estar no passado.")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Column(nullable = false)
    private LocalDate dataNascimento;

    @Column(nullable = false)
    private Boolean inativado = false;

    @Column(nullable = false)
    private Integer ranking = 0;

    @Valid
    @OneToMany(
            mappedBy = "cliente",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Endereco> enderecos = new ArrayList<>();

    @Valid
    @OneToMany(
            mappedBy = "cliente",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Telefone> telefones = new ArrayList<>();

    @Valid
    @OneToMany(
            mappedBy = "cliente",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CartaoCredito> cartoesCredito = new ArrayList<>();

    public Cliente() {
    }

    public void adicionarEndereco(Endereco endereco) {
        if (endereco != null) {
            enderecos.add(endereco);
            endereco.setCliente(this);
        }
    }

    public void removerEndereco(Endereco endereco) {
        if (endereco != null) {
            enderecos.remove(endereco);
            endereco.setCliente(null);
        }
    }

    public void adicionarTelefone(Telefone telefone) {
        if (telefone != null) {
            telefones.add(telefone);
            telefone.setCliente(this);
        }
    }

    public void removerTelefone(Telefone telefone) {
        if (telefone != null) {
            telefones.remove(telefone);
            telefone.setCliente(null);
        }
    }

    public void adicionarCartaoCredito(CartaoCredito cartao) {
        if (cartao != null) {
            cartoesCredito.add(cartao);
            cartao.setCliente(this);
        }
    }

    public void removerCartaoCredito(CartaoCredito cartao) {
        if (cartao != null) {
            cartoesCredito.remove(cartao);
            cartao.setCliente(null);
        }
    }

    public void inativar() {
        this.inativado = true;
    }

    public void ativar() {
        this.inativado = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        if (cpf == null) {
            this.cpf = null;
            return;
        }

        this.cpf = cpf.replaceAll("\\D", "");
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Genero getGenero() {
        return genero;
    }

    public void setGenero(Genero genero) {
        this.genero = genero;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public Boolean getInativado() {
        return inativado;
    }

    public void setInativado(Boolean inativado) {
        this.inativado = inativado;
    }

    public Integer getRanking() {
        return ranking;
    }

    public void setRanking(Integer ranking) {
        this.ranking = ranking;
    }

    public List<Endereco> getEnderecos() {
        return enderecos;
    }

    public void setEnderecos(List<Endereco> enderecos) {
        this.enderecos = enderecos != null
                ? enderecos
                : new ArrayList<>();
    }

    public List<Telefone> getTelefones() {
        return telefones;
    }

    public void setTelefones(List<Telefone> telefones) {
        this.telefones = telefones != null
                ? telefones
                : new ArrayList<>();
    }

    public List<CartaoCredito> getCartoesCredito() {
        return cartoesCredito;
    }

    public void setCartoesCredito(List<CartaoCredito> cartoesCredito) {
        this.cartoesCredito = cartoesCredito != null
                ? cartoesCredito
                : new ArrayList<>();
    }
}