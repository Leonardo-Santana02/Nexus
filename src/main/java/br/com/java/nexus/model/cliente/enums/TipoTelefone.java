package br.com.java.nexus.model.cliente.enums;

public enum TipoTelefone {

    FIXO("Fixo"),
    CELULAR("Celular");

    private final String descricao;

    TipoTelefone(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}