package br.com.java.nexus.model.pagamento.enums;

public enum StatusPagamento {

    PENDENTE("Aguardando confirmação"),
    APROVADO("Pagamento aprovado"),
    REJEITADO("Pagamento rejeitado"),
    CANCELADO("Pagamento cancelado");

    private final String descricao;

    StatusPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}