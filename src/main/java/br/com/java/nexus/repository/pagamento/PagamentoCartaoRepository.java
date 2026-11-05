package br.com.java.nexus.repository.pagamento;

import br.com.java.nexus.model.pagamento.PagamentoCartao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagamentoCartaoRepository
        extends JpaRepository<PagamentoCartao, Long> {

    List<PagamentoCartao>
    findByPagamentoId(
            Long pagamentoId
    );

    boolean existsByCartaoCreditoId(
            Long cartaoId
    );
}