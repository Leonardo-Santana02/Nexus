package br.com.java.nexus.model.pagamento.enums;

public enum FormaPagamento {
    CARTAO_CREDITO("Cartão de Crédito");

    private final String descricao;

    FormaPagamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}