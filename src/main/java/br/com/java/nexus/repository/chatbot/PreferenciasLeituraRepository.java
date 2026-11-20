package br.com.java.nexus.repository.chatbot;

import br.com.java.nexus.model.chatbot.PreferenciasLeitura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PreferenciasLeituraRepository extends JpaRepository<PreferenciasLeitura, Long> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO chat_preferencias (cliente_id, favoritos, evitar)
            VALUES (:cliente, :favoritos, :evitar)
            ON CONFLICT (cliente_id) DO UPDATE
            SET favoritos = EXCLUDED.favoritos, evitar = EXCLUDED.evitar
            """, nativeQuery = true)
    void gravar(@Param("cliente") Long cliente, @Param("favoritos") String favoritos,
                @Param("evitar") String evitar);
}
