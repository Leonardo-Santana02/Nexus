package br.com.java.nexus.model.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_preferencias")
public class PreferenciasLeitura {
    @Id
    private Long clienteId;

    @Column(nullable = false, length = 200)
    private String favoritos;

    @Column(nullable = false, length = 200)
    private String evitar;

    protected PreferenciasLeitura() {
    }

    public String getFavoritos() {
        return favoritos;
    }

    public String getEvitar() {
        return evitar;
    }
}
