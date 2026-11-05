package br.com.java.nexus.repository.cliente;

import br.com.java.nexus.model.cliente.Telefone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
public interface TelefoneRepository extends JpaRepository<Telefone, Long> {

    List<Telefone> findByClienteIdOrderByIdAsc(Long clienteId);
}