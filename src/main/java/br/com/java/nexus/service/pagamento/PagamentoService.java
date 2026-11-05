package br.com.java.nexus.service.pagamento;

import br.com.java.nexus.model.carrinho.Carrinho;
import br.com.java.nexus.model.cliente.CartaoCredito;
import br.com.java.nexus.model.pagamento.Pagamento;
import br.com.java.nexus.model.pagamento.PagamentoCartao;
import br.com.java.nexus.model.pagamento.enums.FormaPagamento;
import br.com.java.nexus.model.pagamento.enums.StatusPagamento;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.venda.Cupom;

import br.com.java.nexus.repository.cliente.CartaoCreditoRepository;
import br.com.java.nexus.repository.pagamento.PagamentoRepository;

import br.com.java.nexus.service.carrinho.CarrinhoService;
import br.com.java.nexus.service.exception.ValidacaoException;
import br.com.java.nexus.service.pedido.PedidoService;
import br.com.java.nexus.service.venda.CupomService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
public class PagamentoService {

    private static final Logger logger = LoggerFactory.getLogger(PagamentoService.class);

    private static final BigDecimal VALOR_MINIMO_CARTAO = new BigDecimal("10.00");

    private final PagamentoRepository pagamentoRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;
    private final CarrinhoService carrinhoService;
    private final CupomService cupomService;
    private final PedidoService pedidoService;

    public PagamentoService(
            PagamentoRepository pagamentoRepository,
            CartaoCreditoRepository cartaoCreditoRepository,
            CarrinhoService carrinhoService,
            CupomService cupomService,
            PedidoService pedidoService) {

        this.pagamentoRepository = pagamentoRepository;
        this.cartaoCreditoRepository = cartaoCreditoRepository;
        this.carrinhoService = carrinhoService;
        this.cupomService = cupomService;
        this.pedidoService = pedidoService;
    }

    @Transactional
    public Pagamento criarPagamento(
            BigDecimal valorOriginal,
            BigDecimal valorDesconto) {

        BigDecimal original = normalizarValorNaoNegativo(valorOriginal, "Valor original");
        BigDecimal desconto = normalizarValorNaoNegativo(valorDesconto, "Valor de desconto");

        if (desconto.compareTo(original) > 0) {
            throw new ValidacaoException("O valor coberto por cupons não pode ser maior " + "que o valor da compra.");
        }

        Pagamento pagamento = new Pagamento();

        pagamento.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setValorOriginal(original);
        pagamento.setValorDesconto(desconto);
        pagamento.setValorPago(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        );

        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);
        logger.info("Pagamento criado. pagamentoId={}, valorOriginal={}, valorCuponsTroca={}",
                pagamentoSalvo.getId(),
                pagamentoSalvo.getValorOriginal(),
                pagamentoSalvo.getValorDesconto());

        return pagamentoSalvo;
    }

    @Transactional
    public Pedido finalizarCompra(
            Long clienteId,
            Long enderecoEntregaId,
            BigDecimal valorFrete,
            Long cupomPromocionalId,
            List<Long> cuponsTrocaIds,
            BigDecimal descontoPromocionalCarrinho,
            BigDecimal valorCuponsTrocaCarrinho,
            BigDecimal valorTotalCarrinho,
            List<Long> cartoesIds,
            List<BigDecimal> cartoesValores,
            List<Integer> cartoesParcelas) {

        validarIdentificadoresCompra(clienteId, enderecoEntregaId);

        Carrinho carrinho = carrinhoService.validarParaCheckout(clienteId);

        if (carrinho == null || carrinho.isVazio()) {
            throw new ValidacaoException("O carrinho está vazio.");
        }

        BigDecimal subtotal = normalizar(carrinho.getSubtotal());
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacaoException("O carrinho não possui valor válido para pagamento.");
        }

        BigDecimal frete = normalizarValorNaoNegativo(valorFrete, "Valor do frete");
        Cupom cupomPromocional = null;
        BigDecimal descontoPromocional = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        if (cupomPromocionalId != null) {
            cupomPromocional = cupomService.buscarPorId(cupomPromocionalId);
            cupomPromocional = cupomService.validarCupom(cupomPromocional.getCodigo(), clienteId);

            if (!cupomPromocional.isPromocional()) {
                throw new ValidacaoException("O cupom selecionado não é promocional.");
            }

            descontoPromocional = normalizar(cupomPromocional.getValor());

            if (descontoPromocional.compareTo(subtotal) > 0) {
                descontoPromocional = subtotal;
            }
        }

        BigDecimal descontoPromocionalInformado = normalizarValorNaoNegativo(descontoPromocionalCarrinho, "Desconto promocional");

        if (descontoPromocional.compareTo(descontoPromocionalInformado) != 0) {
            throw new ValidacaoException("O desconto promocional da compra foi alterado. " + "Retorne ao carrinho e revise os cupons.");
        }

        BigDecimal valorTotalPedido = subtotal.subtract(descontoPromocional)
                    .add(frete).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);


        List<Cupom> cuponsTrocaUtilizados = selecionarCuponsTrocaNecessarios(clienteId, cuponsTrocaIds, valorTotalPedido);
        BigDecimal totalCuponsTroca = somarCupons(cuponsTrocaUtilizados);

        BigDecimal valorCuponsAplicado = totalCuponsTroca.min(valorTotalPedido)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorCuponsInformado = normalizarValorNaoNegativo(valorCuponsTrocaCarrinho, "Valor dos cupons de troca");

        if (valorCuponsAplicado.compareTo(valorCuponsInformado) != 0) {
            throw new ValidacaoException("O valor dos cupons de troca foi alterado. " + "Retorne ao carrinho e revise os cupons.");
        }

        BigDecimal valorExcedente = totalCuponsTroca.subtract(valorTotalPedido)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);


        BigDecimal valorRestante = valorTotalPedido.subtract(valorCuponsAplicado)
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorTotalInformado = normalizarValorNaoNegativo(valorTotalCarrinho, "Valor final da compra");

        if (valorRestante.compareTo(valorTotalInformado) != 0) {
            throw new ValidacaoException("O valor final da compra foi alterado. " + "Retorne ao carrinho e revise o pedido.");
        }

        Pagamento pagamento = criarPagamento(valorTotalPedido, valorCuponsAplicado);

        adicionarCartoesDaCompra(pagamento, clienteId, cartoesIds, cartoesValores, cartoesParcelas);
        validarPagamento(pagamento.getId());

        Pedido pedido = pedidoService.finalizarCompra(
                        clienteId, enderecoEntregaId,
                        pagamento.getId(), frete,
                        descontoPromocional, cupomPromocional != null
                        ? cupomPromocional.getCodigo() : null);

        if (cupomPromocional != null) {
            cupomService.consumirCupom(cupomPromocional.getId(), clienteId);
        }

        for (Cupom cupom : cuponsTrocaUtilizados) {
            cupomService.consumirCupom(cupom.getId(), clienteId);
        }


        if (valorExcedente.compareTo(BigDecimal.ZERO) > 0) {
            cupomService.gerarCupomTrocaPorExcedente(clienteId, valorExcedente);
        }

        logger.info("Compra finalizada. clienteId={}, pagamentoId={}, pedidoId={}, "
                        + "subtotal={}, descontoPromocional={}, frete={}, "
                        + "cuponsTroca={}, valorCartoes={}, excedente={}",
                clienteId,
                pagamento.getId(),
                pedido.getId(),
                subtotal,
                descontoPromocional,
                frete,
                valorCuponsAplicado,
                valorRestante,
                valorExcedente);

        return pedido;
    }

    private List<Cupom> selecionarCuponsTrocaNecessarios(
            Long clienteId,
            List<Long> cuponsTrocaIds,
            BigDecimal valorCompra) {

        List<Cupom> cupons = new ArrayList<>();
        if (cuponsTrocaIds == null || cuponsTrocaIds.isEmpty() || valorCompra.compareTo(BigDecimal.ZERO) <= 0) {
            return cupons;
        }

        Set<Long> idsUnicos = new HashSet<>();

        for (Long cupomId : cuponsTrocaIds) {
            if (cupomId == null || !idsUnicos.add(cupomId)) {
                continue;
            }

            Cupom cupom = cupomService.buscarPorId(cupomId);

            cupom = cupomService.validarCupom(cupom.getCodigo(), clienteId);
            if (!cupom.isTroca()) {
                throw new ValidacaoException("Somente cupons de troca podem ser utilizados " + "como meio de pagamento.");
            }
            cupons.add(cupom);
        }

        cupons.sort(Comparator.comparing(Cupom::getValor).reversed());

        List<Cupom> utilizados = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;

        for (Cupom cupom : cupons) {
            if (acumulado.compareTo(valorCompra) >= 0) {
                break;
            }

            utilizados.add(cupom);
            acumulado = acumulado.add(normalizar(cupom.getValor()));
        }

        return utilizados;
    }

    private BigDecimal somarCupons(List<Cupom> cupons) {
        if (cupons == null || cupons.isEmpty()) {

            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return cupons.stream().map(Cupom::getValor).filter(valor -> valor != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void adicionarCartoesDaCompra(
            Pagamento pagamento,
            Long clienteId,
            List<Long> cartoesIds,
            List<BigDecimal> cartoesValores,
            List<Integer> cartoesParcelas) {

        BigDecimal valorRestante = normalizar(pagamento.getValorAPagar());

        if (valorRestante.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (cartoesIds == null || cartoesIds.isEmpty()) {
            throw new ValidacaoException("Selecione pelo menos um cartão para pagar o valor restante.");
        }

        if (cartoesValores == null || cartoesValores.size() != cartoesIds.size()) {
            throw new ValidacaoException("Os valores informados para os cartões são inválidos.");
        }

        if (cartoesParcelas == null || cartoesParcelas.size() != cartoesIds.size()) {
            throw new ValidacaoException("As parcelas informadas para os cartões são inválidas.");
        }

        Set<Long> cartoesUnicos = new HashSet<>();

        for (int i = 0; i < cartoesIds.size(); i++) {
            Long cartaoId = cartoesIds.get(i);
            BigDecimal valor = cartoesValores.get(i);
            Integer parcelas = cartoesParcelas.get(i);

            if (cartaoId == null || !cartoesUnicos.add(cartaoId)) {
                throw new ValidacaoException("Um mesmo cartão não pode ser utilizado " + "mais de uma vez no pagamento.");
            }

            adicionarCartao(pagamento.getId(), clienteId, cartaoId, valor, parcelas);
        }
    }

    @Transactional
    public Pagamento adicionarCartao(
            Long pagamentoId,
            Long clienteId,
            Long cartaoId,
            BigDecimal valor,
            Integer parcelas) {

        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);

        BigDecimal valorNormalizado = normalizarValorPositivo(valor, "Valor do cartão");
        validarParcelas(parcelas);

        CartaoCredito cartao = buscarCartaoDoCliente(cartaoId, clienteId);

        boolean cartaoJaUtilizado = pagamento
                        .getCartoesUsados()
                        .stream().anyMatch(pagamentoCartao -> pagamentoCartao
                        .getCartaoCredito().getId().equals(cartaoId));

        if (cartaoJaUtilizado) {
            throw new ValidacaoException("O cartão já foi adicionado ao pagamento.");
        }

        pagamento.adicionarCartao(cartao, valorNormalizado, parcelas);

        atualizarValorPago(pagamento);
        validarValorCartoesNaoExcedeTotal(pagamento);

        return pagamentoRepository.save(pagamento);
    }

    @Transactional
    public Pagamento alterarCartao(
            Long pagamentoId,
            Long pagamentoCartaoId,
            BigDecimal valor,
            Integer parcelas) {

        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);

        BigDecimal valorNormalizado = normalizarValorPositivo(valor, "Valor do cartão");
        validarParcelas(parcelas);

        PagamentoCartao pagamentoCartao = buscarPagamentoCartao(pagamento, pagamentoCartaoId);
        pagamentoCartao.setValorPago(valorNormalizado);
        pagamentoCartao.setParcelas(parcelas);

        atualizarValorPago(pagamento);
        validarValorCartoesNaoExcedeTotal(pagamento);

        return pagamentoRepository.save(pagamento);
    }

    @Transactional
    public Pagamento removerCartao(Long pagamentoId, Long pagamentoCartaoId) {

        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);

        PagamentoCartao pagamentoCartao = buscarPagamentoCartao(pagamento, pagamentoCartaoId);
        pagamento.removerCartao(pagamentoCartao);

        atualizarValorPago(pagamento);

        return pagamentoRepository.save(pagamento
        );
    }

    @Transactional(readOnly = true)
    public void validarPagamento(Long pagamentoId) {

        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);

        BigDecimal valorAPagar = normalizar(pagamento.getValorAPagar());

        if (valorAPagar.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        List<PagamentoCartao> cartoes = pagamento.getCartoesUsados();

        if (cartoes == null || cartoes.isEmpty()) {
            throw new ValidacaoException("Informe pelo menos um cartão para realizar o pagamento.");
        }


        BigDecimal totalCartoes = normalizar(pagamento.getTotalCartoes());

        if (totalCartoes.compareTo(valorAPagar) != 0) {
            throw new ValidacaoException("A soma dos valores pagos com os cartões " + "deve ser igual ao valor restante da compra.");
        }

        validarValorMinimoCartoes(pagamento);
    }


    private void validarValorMinimoCartoes(Pagamento pagamento) {
        List<PagamentoCartao> cartoes = pagamento.getCartoesUsados();

        if (cartoes == null || cartoes.size() <= 1) {
            return;
        }

        for (PagamentoCartao pagamentoCartao : cartoes) {

            BigDecimal valor = pagamentoCartao.getValorPago();

            if (valor == null || valor.compareTo(VALOR_MINIMO_CARTAO) < 0) {
                throw new ValidacaoException("Ao utilizar mais de um cartão, " + "cada cartão deve receber " + "pelo menos R$ 10,00.");
            }
        }
    }

    @Transactional
    public Pagamento aprovar(Long pagamentoId) {
        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);
        validarPagamento(pagamentoId);
        pagamento.aprovar();

        return pagamentoRepository.save(pagamento);
    }

    @Transactional
    public Pagamento rejeitar(Long pagamentoId, String motivo) {
        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);

        if (motivo == null || motivo.isBlank()) {
            throw new ValidacaoException("O motivo da rejeição é obrigatório.");
        }
        pagamento.rejeitar(motivo.trim());

        return pagamentoRepository.save(pagamento);
    }

    @Transactional
    public Pagamento cancelar(Long pagamentoId) {
        Pagamento pagamento = buscarPorId(pagamentoId);
        validarPagamentoPendente(pagamento);
        pagamento.cancelar();

        return pagamentoRepository.save(pagamento);
    }

    @Transactional(readOnly = true)
    public Pagamento buscarPorId(Long pagamentoId) {

        if (pagamentoId == null) {
            throw new ValidacaoException("Pagamento é obrigatório.");
        }

        return pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ValidacaoException("Pagamento não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Pagamento> listarTodos() {

        return pagamentoRepository.findAllByOrderByDataCriacaoDesc();
    }

    @Transactional(readOnly = true)
    public List<Pagamento> listarPorStatus(StatusPagamento status) {
        if (status == null) {
            return listarTodos();
        }

        return pagamentoRepository.findByStatusOrderByDataCriacaoDesc(status);
    }

    private CartaoCredito buscarCartaoDoCliente(Long cartaoId, Long clienteId) {
        if (cartaoId == null) {
            throw new ValidacaoException("Cartão é obrigatório.");
        }

        if (clienteId == null) {
            throw new ValidacaoException("Cliente é obrigatório.");
        }

        CartaoCredito cartao = cartaoCreditoRepository.findById(cartaoId)
                        .orElseThrow(() -> new ValidacaoException("Cartão não encontrado."));

        if (cartao.getCliente() == null || cartao.getCliente().getId() == null || !clienteId.equals(cartao.getCliente().getId())) {
            throw new ValidacaoException("O cartão informado não pertence ao cliente.");
        }

        return cartao;
    }

    private PagamentoCartao buscarPagamentoCartao(Pagamento pagamento, Long pagamentoCartaoId) {
        if (pagamentoCartaoId == null) {
            throw new ValidacaoException("Cartão do pagamento é obrigatório.");
        }

        return pagamento.getCartoesUsados().stream()
                .filter(item -> pagamentoCartaoId.equals(item.getId()))
                .findFirst().orElseThrow(() -> new ValidacaoException("Cartão não encontrado neste pagamento."));
    }

    private void atualizarValorPago(Pagamento pagamento) {
        pagamento.setValorPago(normalizar(pagamento.getTotalCartoes()));
    }

    private void validarValorCartoesNaoExcedeTotal(Pagamento pagamento) {
        BigDecimal totalCartoes = normalizar(pagamento.getTotalCartoes());
        BigDecimal valorAPagar = normalizar(pagamento.getValorAPagar());

        if (totalCartoes.compareTo(valorAPagar) > 0) {
            throw new ValidacaoException("O valor distribuído entre os cartões " + "não pode ultrapassar o valor restante da compra.");
        }
    }
    private void validarPagamentoPendente(Pagamento pagamento) {
        if (pagamento == null) {
            throw new ValidacaoException("Pagamento não encontrado.");
        }

        if (!pagamento.isPendente()) {
            throw new ValidacaoException("Somente pagamentos pendentes podem ser alterados.");
        }
    }

    private void validarParcelas(Integer parcelas) {
        if (parcelas == null || parcelas < 1) {
            throw new ValidacaoException("O número de parcelas deve ser maior que zero.");
        }

        if (parcelas > 2) {
            throw new ValidacaoException("O pagamento pode ser dividido " + "em no máximo 2 parcelas."
            );
        }
    }

    private void validarIdentificadoresCompra(Long clienteId, Long enderecoEntregaId) {
        if (clienteId == null || clienteId <= 0) {
            throw new ValidacaoException("Cliente é obrigatório.");
        }

        if (enderecoEntregaId == null || enderecoEntregaId <= 0) {
            throw new ValidacaoException("Endereço de entrega é obrigatório.");
        }
    }

    private BigDecimal normalizarValorPositivo(BigDecimal valor, String campo) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacaoException(campo + " deve ser maior que zero.");
        }

        return normalizar(valor);
    }

    private BigDecimal normalizarValorNaoNegativo(BigDecimal valor, String campo) {
        if (valor == null) {
            throw new ValidacaoException(campo + " é obrigatório.");
        }

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidacaoException(campo + " não pode ser negativo.");
        }
        return normalizar(valor);
    }

    private BigDecimal normalizar(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}