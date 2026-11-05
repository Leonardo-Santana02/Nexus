package br.com.java.nexus.service.venda;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.venda.Cupom;
import br.com.java.nexus.model.venda.enums.TipoCupom;
import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.repository.venda.CupomRepository;
import br.com.java.nexus.service.exception.ClienteNaoEncontradoException;
import br.com.java.nexus.service.exception.ValidacaoException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CupomService {
    private final CupomRepository cupomRepository;
    private final ClienteRepository clienteRepository;

    public CupomService(CupomRepository cupomRepository, ClienteRepository clienteRepository) {
        this.cupomRepository = cupomRepository;
        this.clienteRepository = clienteRepository;
    }


    @Transactional(readOnly = true)
    public Cupom buscarPorId(Long id) {

        if (id == null) {
            throw new ValidacaoException("ID do cupom é obrigatório.");
        }

        return cupomRepository
                .findById(id)
                .orElseThrow(() -> new ValidacaoException("Cupom não encontrado."));
    }

    @Transactional(readOnly = true)
    public Cupom buscarPorCodigo(String codigo) {
        String codigoNormalizado = normalizarCodigo(codigo);

        return cupomRepository.
                findByCodigoIgnoreCase(codigoNormalizado)
                .orElseThrow(() -> new ValidacaoException("Cupom não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Cupom> buscarCuponsValidosParaCliente(Long clienteId) {
        validarClienteExistente(clienteId);

        return cupomRepository.findValidosParaCliente(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Cupom> buscarCuponsTrocaValidos(Long clienteId) {
        validarClienteExistente(clienteId);

        return cupomRepository.findValidosDoClientePorTipo(clienteId, TipoCupom.TROCA);
    }

    @Transactional(readOnly = true)
    public Cupom validarCupom(String codigo, Long clienteId) {

        if (clienteId == null) {
            throw new ValidacaoException("Cliente é obrigatório para utilizar cupom.");
        }

        validarClienteExistente(clienteId);
        String codigoNormalizado = normalizarCodigo(codigo);

        Cupom cupom = cupomRepository.findValidoPorCodigoECliente(codigoNormalizado, clienteId)
                        .orElseThrow(() -> new ValidacaoException("Cupom inválido, expirado ou indisponível para este cliente."));

        if (!cupom.isValido()) {
            throw new ValidacaoException("Cupom inválido ou expirado.");
        }

        if (!cupom.pertenceAoCliente(clienteId)) {
            throw new ValidacaoException("Cupom não disponível para este cliente.");
        }
        return cupom;
    }

    @Transactional
    public void consumirCupom(Long cupomId, Long clienteId) {
        if (cupomId == null) {
            throw new ValidacaoException("Cupom é obrigatório.");
        }
        Cupom cupom = cupomRepository.findById(cupomId)
                        .orElseThrow(() -> new ValidacaoException("Cupom não encontrado."));

        if (!cupom.isValido()) {
            throw new ValidacaoException("Cupom inválido ou expirado.");
        }

        if (!cupom.pertenceAoCliente(clienteId)) {
            throw new ValidacaoException("Cupom não pertence ao cliente informado.");
        }

        cupom.consumir();
        cupomRepository.save(cupom);
    }

    @Transactional
    public Cupom gerarCupomTrocaPorExcedente(Long clienteId, BigDecimal valorExcedente) {
        if (clienteId == null) {
            throw new ValidacaoException("Cliente é obrigatório para gerar cupom de troca.");
        }

        if (valorExcedente == null || valorExcedente.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacaoException("O valor do cupom de troca deve ser maior que zero.");
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                        .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));

        Cupom cupom = new Cupom();
        cupom.setCodigo(gerarCodigoCupomTroca());
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(valorExcedente);
        cupom.setCliente(cliente);
        cupom.setAtivo(true);
        cupom.setQuantidadeUsos(0);
        cupom.setMaximoUsos(1);
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6));
        cupom.setDescricao("Crédito gerado por valor excedente de cupons em uma compra.");
        return cupomRepository.save(cupom);
    }

    @Transactional
    public Cupom gerarCupomTrocaPorDevolucao(Long clienteId,
                                             Long devolucaoId,
                                             BigDecimal valor) {

        if (clienteId == null) {
            throw new ValidacaoException("Cliente é obrigatório para gerar cupom de troca.");
        }

        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacaoException("O valor do cupom de troca deve ser maior que zero.");
        }

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));

        Cupom cupom = new Cupom();
        cupom.setCodigo(gerarCodigoCupomTroca());
        cupom.setTipo(TipoCupom.TROCA);
        cupom.setValor(valor);
        cupom.setCliente(cliente);
        cupom.setAtivo(true);
        cupom.setQuantidadeUsos(0);
        cupom.setMaximoUsos(1);
        cupom.setDataValidade(LocalDateTime.now().plusMonths(6));
        cupom.setDescricao("Cupom de troca referente à devolução #" + devolucaoId);

        return cupomRepository.save(cupom);
    }


    private Cliente validarClienteExistente(Long clienteId) {
        if (clienteId == null) {
            throw new ValidacaoException("ID do cliente é obrigatório.");
        }

        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ClienteNaoEncontradoException(clienteId));
    }

    private String normalizarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ValidacaoException("Código do cupom é obrigatório.");
        }

        return codigo.trim().toUpperCase();
    }

    private String gerarCodigoCupomTroca() {
        return "TROCA-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase();
    }
}