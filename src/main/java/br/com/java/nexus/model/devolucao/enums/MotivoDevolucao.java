package br.com.java.nexus.model.devolucao.enums;

public enum MotivoDevolucao {

    ARREPENDIMENTO("Arrependimento/Desistência", 7),
    DEFEITO_IMPRESSAO("Defeito de impressão", 30),
    PRODUTO_DANIFICADO("Produto danificado no transporte", 7),
    LIVRO_DIFERENTE("Livro diferente do anunciado", 30),
    ENTREGA_ATRASADA("Entrega fora do prazo", 7),
    OUTRO("Outro motivo", 7);

    private final String descricao;
    private final int prazoDias;

    MotivoDevolucao(String descricao, int prazoDias) {
        this.descricao = descricao;
        this.prazoDias = prazoDias;
    }

    public String getDescricao() {
        return descricao;
    }

    public int getPrazoDias() {
        return prazoDias;
    }
}