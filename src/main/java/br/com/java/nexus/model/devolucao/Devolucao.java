package br.com.java.nexus.model.devolucao;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.devolucao.enums.MotivoDevolucao;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.venda.Cupom;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devolucoes")
public class Devolucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoDevolucao motivo;

    @Column(length = 500)
    private String justificativa;

    @Column(name = "observacao_admin", length = 1000)
    private String observacaoAdmin;

    @Column(name = "valor_solicitado", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorSolicitado;

    @Column(name = "valor_aprovado", precision = 10, scale = 2)
    private BigDecimal valorAprovado;

    @Column(name = "data_solicitacao", nullable = false)
    private LocalDateTime dataSolicitacao = LocalDateTime.now();

    @Column(name = "data_autorizacao")
    private LocalDateTime dataAutorizacao;

    @Column(name = "data_recebimento")
    private LocalDateTime dataRecebimento;

    @Column(name = "data_recusa")
    private LocalDateTime dataRecusa;

    @OneToMany(mappedBy = "devolucao",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ItemDevolucao> itens = new ArrayList<>();

    /**
     * Cupom de troca gerado quando o admin confirma o recebimento
     * dos itens devolvidos (RF0045).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cupom_id")
    private Cupom cupomGerado;

    /*
     * ==========================================================
     * Observação importante
     * ==========================================================
     *
     * NÃO existe campo "status" nesta entidade.
     *
     * O estado da devolução é lido diretamente de pedido.getStatus():
     *
     *   EM_TROCA          -> devolução solicitada, aguardando análise
     *   TROCA_AUTORIZADA  -> admin autorizou; aguardando envio/recebimento;
     *                        após receber, gera cupom e permanece aqui
     *   TROCA_RECUSADA    -> admin recusou a devolução
     *   ENTREGUE          -> cliente cancelou a devolução (voltou ao normal)
     * ==========================================================
     */


    // ============================================================
    // CONSTRUTORES
    // ============================================================

    public Devolucao() {
    }

    public Devolucao(Pedido pedido,
                     Cliente cliente,
                     MotivoDevolucao motivo,
                     String justificativa,
                     BigDecimal valorSolicitado) {
        this.pedido = pedido;
        this.cliente = cliente;
        this.motivo = motivo;
        this.justificativa = justificativa;
        this.valorSolicitado = valorSolicitado;
        this.dataSolicitacao = LocalDateTime.now();
    }


    // ============================================================
    // MÉTODOS DE NEGÓCIO
    // ============================================================

    /**
     * Registra a autorização da devolução pelo admin.
     */
    public void autorizar(BigDecimal valorAprovado, String observacaoAdmin) {
        this.valorAprovado = valorAprovado;
        this.observacaoAdmin = observacaoAdmin;
        this.dataAutorizacao = LocalDateTime.now();
    }

    /**
     * Registra a recusa da devolução pelo admin.
     */
    public void recusar(String observacaoAdmin) {
        this.observacaoAdmin = observacaoAdmin;
        this.dataRecusa = LocalDateTime.now();
    }

    /**
     * Registra o recebimento dos itens devolvidos.
     */
    public void registrarRecebimento() {
        this.dataRecebimento = LocalDateTime.now();
    }

    /**
     * Calcula o valor total dos itens devolvidos, com base nos
     * valores unitários congelados no momento da solicitação.
     */
    public BigDecimal calcularValorTotalItens() {
        return itens.stream()
                .map(ItemDevolucao::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Indica se a devolução é parcial (menos itens do que o pedido)
     * ou total (todos os itens do pedido).
     */
    public boolean isParcial() {
        if (pedido == null || pedido.getItens() == null) {
            return false;
        }
        return itens.size() < pedido.getItens().size();
    }

    /**
     * Valor efetivo a ser reembolsado: o aprovado, se houver;
     * caso contrário, o solicitado.
     */
    public BigDecimal getValorEfetivo() {
        return valorAprovado != null ? valorAprovado : valorSolicitado;
    }

    /**
     * Indica se o recebimento já foi confirmado.
     */
    public boolean isRecebida() {
        return dataRecebimento != null;
    }

    /**
     * Indica se o cupom já foi gerado.
     */
    public boolean isConcluida() {
        return cupomGerado != null;
    }


    // ============================================================
    // GETTERS E SETTERS
    // ============================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public MotivoDevolucao getMotivo() {
        return motivo;
    }

    public void setMotivo(MotivoDevolucao motivo) {
        this.motivo = motivo;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }

    public String getObservacaoAdmin() {
        return observacaoAdmin;
    }

    public void setObservacaoAdmin(String observacaoAdmin) {
        this.observacaoAdmin = observacaoAdmin;
    }

    public BigDecimal getValorSolicitado() {
        return valorSolicitado;
    }

    public void setValorSolicitado(BigDecimal valorSolicitado) {
        this.valorSolicitado = valorSolicitado;
    }

    public BigDecimal getValorAprovado() {
        return valorAprovado;
    }

    public void setValorAprovado(BigDecimal valorAprovado) {
        this.valorAprovado = valorAprovado;
    }

    public LocalDateTime getDataSolicitacao() {
        return dataSolicitacao;
    }

    public void setDataSolicitacao(LocalDateTime dataSolicitacao) {
        this.dataSolicitacao = dataSolicitacao;
    }

    public LocalDateTime getDataAutorizacao() {
        return dataAutorizacao;
    }

    public void setDataAutorizacao(LocalDateTime dataAutorizacao) {
        this.dataAutorizacao = dataAutorizacao;
    }

    public LocalDateTime getDataRecebimento() {
        return dataRecebimento;
    }

    public void setDataRecebimento(LocalDateTime dataRecebimento) {
        this.dataRecebimento = dataRecebimento;
    }

    public LocalDateTime getDataRecusa() {
        return dataRecusa;
    }

    public void setDataRecusa(LocalDateTime dataRecusa) {
        this.dataRecusa = dataRecusa;
    }

    public List<ItemDevolucao> getItens() {
        return itens;
    }

    public void setItens(List<ItemDevolucao> itens) {
        this.itens = itens;
    }

    public Cupom getCupomGerado() {
        return cupomGerado;
    }

    public void setCupomGerado(Cupom cupomGerado) {
        this.cupomGerado = cupomGerado;
    }


    // ============================================================
    // HELPERS DE COLEÇÃO
    // ============================================================

    public void adicionarItem(ItemDevolucao item) {
        item.setDevolucao(this);
        this.itens.add(item);
    }

    public void removerItem(ItemDevolucao item) {
        item.setDevolucao(null);
        this.itens.remove(item);
    }
}