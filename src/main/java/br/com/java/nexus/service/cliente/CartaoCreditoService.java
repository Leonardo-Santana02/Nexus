package br.com.java.nexus.service.cliente;

import br.com.java.nexus.model.cliente.CartaoCredito;
import br.com.java.nexus.repository.cliente.CartaoCreditoRepository;
import br.com.java.nexus.service.exception.ValidacaoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartaoCreditoService {
    private final CartaoCreditoRepository cartaoCreditoRepository;

    public CartaoCreditoService(CartaoCreditoRepository cartaoCreditoRepository) {
        this.cartaoCreditoRepository = cartaoCreditoRepository;
    }

    @Transactional(readOnly = true)
    public List<CartaoCredito> buscarPorCliente(Long clienteId) {
        if (clienteId == null) {
            throw new ValidacaoException("ID do cliente é obrigatório.");
        }
        return cartaoCreditoRepository.findByClienteIdOrderByIdAsc(clienteId);
    }

    @Transactional(readOnly = true)
    public Optional<CartaoCredito> buscarPreferencial(Long clienteId) {
        if (clienteId == null) {
            throw new ValidacaoException("ID do cliente é obrigatório.");
        }
        return cartaoCreditoRepository.findByClienteIdAndPreferencialTrue(clienteId);
    }

    public void validarCartao(CartaoCredito cartao) {
        if (cartao == null) {
            throw new ValidacaoException("Cartão de crédito é obrigatório.");
        }

        String numero = somenteNumeros(cartao.getNumeroCartao());

        if (numero.length() != 16) {
            throw new ValidacaoException("Número do cartão deve possuir 16 dígitos.");
        }

        if (cartao.getNomeImpresso() == null || cartao.getNomeImpresso().isBlank()) {
            throw new ValidacaoException("Nome impresso no cartão é obrigatório.");
        }

        if (cartao.getBandeira() == null) {
            throw new ValidacaoException("Bandeira do cartão é obrigatória.");
        }

        String codigo = somenteNumeros(cartao.getCodigoSeguranca());

        if (codigo.length() != 3 && codigo.length() != 4) {
            throw new ValidacaoException("Código de segurança deve possuir 3 ou 4 dígitos.");
        }
    }

    private String somenteNumeros(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replaceAll("\\D", "");
    }
}