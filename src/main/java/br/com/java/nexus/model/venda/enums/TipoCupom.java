package br.com.java.nexus.model.venda.enums;

public enum TipoCupom {
    PROMOCIONAL("Cupom promocional"),
    TROCA("Cupom de troca");

    private final String descricao;

    TipoCupom(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}