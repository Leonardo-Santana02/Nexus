package br.com.java.nexus.repository.chatbot;

import br.com.java.nexus.model.chatbot.RespostaChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface RespostaChatRepository extends JpaRepository<RespostaChat, Long> {
    List<RespostaChat> findTop12ByClienteIdOrderByIdDesc(Long clienteId);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RespostaChat r SET r.util = :util, r.avaliadaEm = :agora
            WHERE r.id = :id AND r.clienteId = :cliente
            """)
    int avaliar(@Param("cliente") Long cliente, @Param("id") Long id, @Param("util") boolean util,
               @Param("agora") Instant agora);
}
