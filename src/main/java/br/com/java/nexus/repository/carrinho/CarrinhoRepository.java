package br.com.java.nexus.repository.carrinho;

import br.com.java.nexus.model.carrinho.Carrinho;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarrinhoRepository
        extends JpaRepository<Carrinho, Long> {

    Optional<Carrinho> findByClienteId(
            Long clienteId
    );


    @Query("""
            SELECT DISTINCT c
            FROM Carrinho c
            LEFT JOIN FETCH c.itens i
            LEFT JOIN FETCH i.produto
            WHERE c.cliente.id = :clienteId
            """)
    Optional<Carrinho> buscarComItensPorClienteId(
            @Param("clienteId")
            Long clienteId
    );
}