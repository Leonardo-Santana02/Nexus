package br.com.java.nexus.repository.cliente;

import br.com.java.nexus.model.cliente.CartaoCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartaoCreditoRepository
        extends JpaRepository<CartaoCredito, Long> {

    List<CartaoCredito> findByClienteIdOrderByIdAsc(Long clienteId);

    Optional<CartaoCredito>
    findByClienteIdAndPreferencialTrue(Long clienteId);

    boolean existsByClienteIdAndPreferencialTrue(Long clienteId);
}