package br.com.java.nexus.repository.pagamento;

import br.com.java.nexus.model.pagamento.Pagamento;
import br.com.java.nexus.model.pagamento.enums.StatusPagamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagamentoRepository
        extends JpaRepository<Pagamento, Long> {

    List<Pagamento>
    findByStatusOrderByDataCriacaoDesc(
            StatusPagamento status
    );

    List<Pagamento>
    findAllByOrderByDataCriacaoDesc();
}