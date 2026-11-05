package br.com.java.nexus.repository.cliente;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.enums.Genero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository
        extends JpaRepository<Cliente, Long> {

    /*
     * ==========================================================
     * VALIDAÇÃO DE UNICIDADE
     * ==========================================================
     */

    boolean existsByCpf(String cpf);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCpfAndIdNot(
            String cpf,
            Long id
    );

    boolean existsByEmailIgnoreCaseAndIdNot(
            String email,
            Long id
    );


    /*
     * ==========================================================
     * CONSULTAS DIRETAS
     * ==========================================================
     */

    Optional<Cliente> findByCpf(String cpf);

    Optional<Cliente> findByEmailIgnoreCase(String email);

    List<Cliente> findByInativadoFalseOrderByNomeAsc();

    List<Cliente> findByInativadoTrueOrderByNomeAsc();


    /*
     * ==========================================================
     * PESQUISA COM FILTROS
     * ==========================================================
     *
     * inativado:
     *
     * null  -> todos
     * false -> ativos
     * true  -> inativos
     */

    @Query("""
        SELECT DISTINCT c
        FROM Cliente c

        WHERE (:id IS NULL OR c.id = :id)

          AND (
                :nome = ''
                OR LOWER(c.nome)
                    LIKE CONCAT('%', :nome, '%')
          )

          AND (
                :email = ''
                OR LOWER(c.email)
                    LIKE CONCAT('%', :email, '%')
          )

          AND (
                :cpf = ''
                OR c.cpf = :cpf
          )

          AND (
                :genero IS NULL
                OR c.genero = :genero
          )

          AND (
                :dataNascimento IS NULL
                OR c.dataNascimento = :dataNascimento
          )

          AND (
                :inativado IS NULL
                OR c.inativado = :inativado
          )

          AND (
                :telefone = ''
                OR EXISTS (
                    SELECT t.id
                    FROM Telefone t
                    WHERE t.cliente = c
                      AND CONCAT(t.ddd, t.numero)
                          LIKE CONCAT('%', :telefone, '%')
                )
          )

        ORDER BY c.nome ASC
        """)
    List<Cliente> pesquisar(
            @Param("id") Long id,
            @Param("nome") String nome,
            @Param("email") String email,
            @Param("cpf") String cpf,
            @Param("genero") Genero genero,
            @Param("dataNascimento") LocalDate dataNascimento,
            @Param("telefone") String telefone,
            @Param("inativado") Boolean inativado
    );
}