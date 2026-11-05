package br.com.java.nexus.model.cliente;

import br.com.java.nexus.model.cliente.enums.TipoLogradouro;
import br.com.java.nexus.model.cliente.enums.UF;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "enderecos")
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "O tipo de logradouro é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoLogradouro tipoLogradouro;

    @NotBlank(message = "O logradouro é obrigatório.")
    @Column(nullable = false, length = 100)
    private String logradouro;

    @NotBlank(message = "O número é obrigatório.")
    @Column(nullable = false, length = 20)
    private String numero;

    @Column(length = 50)
    private String complemento;

    @NotBlank(message = "O bairro é obrigatório.")
    @Column(nullable = false, length = 50)
    private String bairro;

    @NotBlank(message = "A cidade é obrigatória.")
    @Column(nullable = false, length = 50)
    private String cidade;

    @NotNull(message = "O estado é obrigatório.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private UF uf;

    @NotBlank(message = "O CEP é obrigatório.")
    @Column(nullable = false, length = 9)
    private String cep;

    @NotBlank(message = "O país é obrigatório.")
    @Column(nullable = false, length = 50)
    private String pais;

    @Column(length = 255)
    private String observacoes;

    @Column(length = 50)
    private String apelido;

    @Column(nullable = false)
    private boolean enderecoEntrega;

    @Column(nullable = false)
    private boolean enderecoCobranca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    public Endereco() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TipoLogradouro getTipoLogradouro() {
        return tipoLogradouro;
    }

    public void setTipoLogradouro(TipoLogradouro tipoLogradouro) {
        this.tipoLogradouro = tipoLogradouro;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public UF getUf() {
        return uf;
    }

    public void setUf(UF uf) {
        this.uf = uf;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        this.apelido = apelido;
    }

    public boolean isEnderecoEntrega() {
        return enderecoEntrega;
    }

    public void setEnderecoEntrega(boolean enderecoEntrega) {
        this.enderecoEntrega = enderecoEntrega;
    }

    public boolean isEnderecoCobranca() {
        return enderecoCobranca;
    }

    public void setEnderecoCobranca(boolean enderecoCobranca) {
        this.enderecoCobranca = enderecoCobranca;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }
}