package br.com.java.nexus.repository.venda;

import br.com.java.nexus.model.venda.Cupom;
import br.com.java.nexus.model.venda.enums.TipoCupom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CupomRepository
        extends JpaRepository<Cupom, Long> {


    /*
     * ==========================================================
     * BUSCA POR CÓDIGO
     * ==========================================================
     */

    Optional<Cupom> findByCodigoIgnoreCase(
            String codigo
    );


    /*
     * ==========================================================
     * CUPOM VÁLIDO PARA O CLIENTE
     * ==========================================================
     */

    @Query("""
            SELECT c
            FROM Cupom c
            WHERE UPPER(c.codigo) = UPPER(:codigo)
              AND c.ativo = true
              AND c.dataValidade > CURRENT_TIMESTAMP
              AND (
                    c.maximoUsos IS NULL
                    OR c.quantidadeUsos < c.maximoUsos
                  )
              AND (
                    c.cliente IS NULL
                    OR c.cliente.id = :clienteId
                  )
            """)
    Optional<Cupom> findValidoPorCodigoECliente(
            @Param("codigo")
            String codigo,

            @Param("clienteId")
            Long clienteId
    );


    /*
     * ==========================================================
     * CUPONS VÁLIDOS DISPONÍVEIS PARA O CLIENTE
     * ==========================================================
     */

    @Query("""
            SELECT c
            FROM Cupom c
            WHERE c.ativo = true
              AND c.dataValidade > CURRENT_TIMESTAMP
              AND (
                    c.maximoUsos IS NULL
                    OR c.quantidadeUsos < c.maximoUsos
                  )
              AND (
                    c.cliente IS NULL
                    OR c.cliente.id = :clienteId
                  )
            ORDER BY c.dataValidade ASC
            """)
    List<Cupom> findValidosParaCliente(
            @Param("clienteId")
            Long clienteId
    );


    /*
     * ==========================================================
     * CUPONS DE TROCA DO CLIENTE
     * ==========================================================
     */

    @Query("""
            SELECT c
            FROM Cupom c
            WHERE c.cliente.id = :clienteId
              AND c.tipo = :tipo
              AND c.ativo = true
              AND c.dataValidade > CURRENT_TIMESTAMP
              AND (
                    c.maximoUsos IS NULL
                    OR c.quantidadeUsos < c.maximoUsos
                  )
            ORDER BY c.dataValidade ASC
            """)
    List<Cupom> findValidosDoClientePorTipo(
            @Param("clienteId")
            Long clienteId,

            @Param("tipo")
            TipoCupom tipo
    );
}
