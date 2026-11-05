package br.com.java.nexus.repository.cliente;

import br.com.java.nexus.model.cliente.Endereco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    List<Endereco> findByClienteIdOrderByIdAsc(Long clienteId);

    List<Endereco> findByClienteIdAndEnderecoEntregaTrue(Long clienteId);

    List<Endereco> findByClienteIdAndEnderecoCobrancaTrue(Long clienteId);

    boolean existsByClienteIdAndEnderecoEntregaTrue(Long clienteId);

    boolean existsByClienteIdAndEnderecoCobrancaTrue(Long clienteId);
}