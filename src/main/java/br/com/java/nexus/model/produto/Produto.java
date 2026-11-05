package br.com.java.nexus.model.produto;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "produtos")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "O ISBN é obrigatório.")
    @Column(nullable = false, unique = true, length = 20)
    private String isbn;

    @NotBlank(message = "O título é obrigatório.")
    @Column(nullable = false, length = 200)
    private String titulo;

    @NotBlank(message = "O autor é obrigatório.")
    @Column(nullable = false, length = 100)
    private String autor;

    @NotBlank(message = "A editora é obrigatória.")
    @Column(nullable = false, length = 100)
    private String editora;

    @NotNull(message = "O ano de publicação é obrigatório.")
    @Min(value = 1000, message = "O ano de publicação é inválido.")
    @Max(value = 2100, message = "O ano de publicação é inválido.")
    @Column(nullable = false)
    private Integer anoPublicacao;

    @NotNull(message = "O número de páginas é obrigatório.")
    @Min(value = 1, message = "O número de páginas deve ser maior que zero.")
    @Column(nullable = false)
    private Integer paginas;

    @NotBlank(message = "O idioma é obrigatório.")
    @Column(nullable = false, length = 50)
    private String idioma;

    @Column(length = 500)
    private String sinopse;

    @NotNull(message = "O preço é obrigatório.")
    @Positive(message = "O preço deve ser maior que zero.")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @NotNull(message = "A quantidade em estoque é obrigatória.")
    @PositiveOrZero(message = "O estoque não pode ser negativo.")
    @Column(nullable = false)
    private Integer estoque;

    @NotBlank(message = "O gênero é obrigatório.")
    @Column(nullable = false, length = 50)
    private String genero;

    @Column(length = 50)
    private String subGenero;

    @Positive(message = "O peso deve ser maior que zero.")
    @Column
    private Double peso;

    @Positive(message = "A altura deve ser maior que zero.")
    @Column
    private Double altura;

    @Positive(message = "A largura deve ser maior que zero.")
    @Column
    private Double largura;

    @Positive(message = "A profundidade deve ser maior que zero.")
    @Column
    private Double profundidade;

    @Column(length = 500)
    private String urlCapa;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false, updatable = false)
    private LocalDate dataCadastro;

    private LocalDate dataAtualizacao;


    /*
     * ==========================================================
     * CICLO DE VIDA
     * ==========================================================
     */

    @PrePersist
    protected void prePersist() {

        if (dataCadastro == null) {
            dataCadastro = LocalDate.now();
        }
    }

    @PreUpdate
    protected void preUpdate() {
        dataAtualizacao = LocalDate.now();
    }


    /*
     * ==========================================================
     * ESTOQUE
     * ==========================================================
     */

    public boolean temEstoque(Integer quantidade) {

        return quantidade != null
                && quantidade > 0
                && estoque != null
                && estoque >= quantidade;
    }

    public boolean estaDisponivel() {

        return ativo
                && estoque != null
                && estoque > 0;
    }


    /*
     * ==========================================================
     * FRETE
     * ==========================================================
     */

    public Double getVolume() {

        if (altura == null
                || largura == null
                || profundidade == null) {

            return 0.0;
        }

        return altura
                * largura
                * profundidade;
    }

    public Double getPesoTotal(
            Integer quantidade) {

        if (peso == null
                || quantidade == null
                || quantidade <= 0) {

            return 0.0;
        }

        return peso * quantidade;
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

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public String getEditora() {
        return editora;
    }

    public void setEditora(String editora) {
        this.editora = editora;
    }

    public Integer getAnoPublicacao() {
        return anoPublicacao;
    }

    public void setAnoPublicacao(
            Integer anoPublicacao) {

        this.anoPublicacao = anoPublicacao;
    }

    public Integer getPaginas() {
        return paginas;
    }

    public void setPaginas(
            Integer paginas) {

        this.paginas = paginas;
    }

    public String getIdioma() {
        return idioma;
    }

    public void setIdioma(
            String idioma) {

        this.idioma = idioma;
    }

    public String getSinopse() {
        return sinopse;
    }

    public void setSinopse(
            String sinopse) {

        this.sinopse = sinopse;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(
            BigDecimal preco) {

        this.preco = preco;
    }

    public Integer getEstoque() {
        return estoque;
    }

    public void setEstoque(
            Integer estoque) {

        this.estoque = estoque;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(
            String genero) {

        this.genero = genero;
    }

    public String getSubGenero() {
        return subGenero;
    }

    public void setSubGenero(
            String subGenero) {

        this.subGenero = subGenero;
    }

    public Double getPeso() {
        return peso;
    }

    public void setPeso(
            Double peso) {

        this.peso = peso;
    }

    public Double getAltura() {
        return altura;
    }

    public void setAltura(
            Double altura) {

        this.altura = altura;
    }

    public Double getLargura() {
        return largura;
    }

    public void setLargura(
            Double largura) {

        this.largura = largura;
    }

    public Double getProfundidade() {
        return profundidade;
    }

    public void setProfundidade(
            Double profundidade) {

        this.profundidade = profundidade;
    }

    public String getUrlCapa() {
        return urlCapa;
    }

    public void setUrlCapa(
            String urlCapa) {

        this.urlCapa = urlCapa;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(
            boolean ativo) {

        this.ativo = ativo;
    }

    public LocalDate getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(
            LocalDate dataCadastro) {

        this.dataCadastro = dataCadastro;
    }

    public LocalDate getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(
            LocalDate dataAtualizacao) {

        this.dataAtualizacao =
                dataAtualizacao;
    }
}