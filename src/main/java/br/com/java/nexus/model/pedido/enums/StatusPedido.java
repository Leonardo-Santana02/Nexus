package br.com.java.nexus.model.pedido.enums;

public enum StatusPedido {

    EM_PROCESSAMENTO("Em processamento"),
    APROVADO("Aprovado"),
    EM_TRANSPORTE("Em transporte"),
    ENTREGUE("Entregue"),
    EM_TROCA("Em troca"),
    TROCA_AUTORIZADA("Troca autorizada"),
    TROCA_RECUSADA("Troca recusada"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}