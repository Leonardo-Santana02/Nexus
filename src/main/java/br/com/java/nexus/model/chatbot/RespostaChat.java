package br.com.java.nexus.model.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_respostas", indexes = @Index(name = "idx_chat_cliente_resposta", columnList = "cliente_id,id"))
public class RespostaChat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 500)
    private String pergunta;

    @Column(nullable = false, columnDefinition = "text")
    private String contexto;

    @Column(nullable = false, columnDefinition = "text")
    private String resposta;

    @Column(nullable = false, length = 160)
    private String modelo;

    @Column(nullable = false)
    private Instant criadaEm;

    private Boolean util;
    private Instant avaliadaEm;

    protected RespostaChat() {
    }

    public RespostaChat(Long clienteId, String pergunta, String contexto, String resposta, String modelo) {
        this.clienteId = clienteId;
        this.pergunta = pergunta;
        this.contexto = contexto;
        this.resposta = resposta;
        this.modelo = modelo;
        this.criadaEm = Instant.now();
    }

    public Long getId() { return id; }
    public String getPergunta() { return pergunta; }
    public String getResposta() { return resposta; }
    public String getModelo() { return modelo; }
    public Boolean getUtil() { return util; }
}
