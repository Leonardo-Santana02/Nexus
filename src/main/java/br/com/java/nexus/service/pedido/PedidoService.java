package br.com.java.nexus.service.pedido;

import br.com.java.nexus.model.carrinho.Carrinho;
import br.com.java.nexus.model.carrinho.ItemCarrinho;
import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.pagamento.Pagamento;
import br.com.java.nexus.model.pagamento.enums.StatusPagamento;
import br.com.java.nexus.model.pedido.ItemPedido;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;

import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.repository.cliente.EnderecoRepository;
import br.com.java.nexus.repository.pagamento.PagamentoRepository;
import br.com.java.nexus.repository.pedido.PedidoRepository;

import br.com.java.nexus.service.carrinho.CarrinhoService;
import br.com.java.nexus.service.exception.ValidacaoException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;


@Service
public class PedidoService {
    private static final Logger logger = LoggerFactory.getLogger(PedidoService.class);
    private final PedidoRepository pedidoRepository;
    private final CarrinhoService carrinhoService;
    private final ClienteRepository clienteRepository;
    private final EnderecoRepository enderecoRepository;
    private final PagamentoRepository pagamentoRepository;


    public PedidoService(
            PedidoRepository pedidoRepository,
            CarrinhoService carrinhoService,
            ClienteRepository clienteRepository,
            EnderecoRepository enderecoRepository,
            PagamentoRepository pagamentoRepository) {

        this.pedidoRepository = pedidoRepository;
        this.carrinhoService = carrinhoService;
        this.clienteRepository = clienteRepository;
        this.enderecoRepository = enderecoRepository;
        this.pagamentoRepository = pagamentoRepository;
    }


    @Transactional
    public Pedido finalizarCompra(
            Long clienteId,
            Long enderecoEntregaId,
            Long pagamentoId,
            BigDecimal valorFrete,
            BigDecimal descontoPromocional,
            String codigoCupomPromocional) {

        validarIdsObrigatorios(clienteId, enderecoEntregaId, pagamentoId);

        BigDecimal frete = normalizarValorNaoNegativo(valorFrete, "O valor do frete");
        BigDecimal desconto = normalizarValorNaoNegativo(descontoPromocional, "O valor do desconto promocional");

        Cliente cliente = buscarCliente(clienteId);
        Carrinho carrinho = carrinhoService.validarParaCheckout(clienteId);

        validarCarrinho(carrinho);
        validarEstoque(carrinho);

        Endereco enderecoEntrega = buscarEnderecoEntrega(clienteId, enderecoEntregaId);

        Pagamento pagamento = buscarPagamento(pagamentoId);
        BigDecimal subtotal = carrinho.getSubtotal();
        BigDecimal valorTotalPedido = calcularValorTotal(subtotal, desconto, frete);

        validarPagamento(pagamento, valorTotalPedido);

        Pedido pedido = criarPedido(
                cliente,
                carrinho,
                enderecoEntrega,
                pagamento,
                frete,
                desconto,
                codigoCupomPromocional);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        logger.info("Pedido criado. pedidoId={}, clienteId={}, "
                        + "status={}, subtotal={}, desconto={}, "
                        + "frete={}, total={}",
                pedidoSalvo.getId(),
                clienteId,
                pedidoSalvo.getStatus(),
                pedidoSalvo.getSubtotal(),
                pedidoSalvo.getDescontoPromocional(),
                pedidoSalvo.getValorFrete(),
                pedidoSalvo.getValorTotal());

        carrinhoService.limparCarrinho(clienteId);

        logger.info("Carrinho limpo após finalização da compra. " + "clienteId={}, pedidoId={}",
                clienteId,
                pedidoSalvo.getId());

        return pedidoSalvo;
    }

    private Pedido criarPedido(
            Cliente cliente,
            Carrinho carrinho,
            Endereco enderecoEntrega,
            Pagamento pagamento,
            BigDecimal valorFrete,
            BigDecimal descontoPromocional,
            String codigoCupomPromocional) {

        Pedido pedido = new Pedido();

        pedido.setCliente(cliente);
        pedido.setEnderecoEntrega(enderecoEntrega);
        pedido.setPagamento(pagamento);
        pedido.setStatus(StatusPedido.EM_PROCESSAMENTO);
        pedido.setDataCriacao(LocalDateTime.now());

        List<ItemPedido> itensPedido = criarItensPedido(carrinho);
        pedido.setItens(itensPedido);
        pedido.setValorFrete(valorFrete);
        pedido.setDescontoPromocional(descontoPromocional);

        if (codigoCupomPromocional != null && !codigoCupomPromocional.isBlank()) {
            pedido.setCodigoCupomPromocional(codigoCupomPromocional.trim());
        }

        pedido.recalcularValores();
        return pedido;
    }

    private List<ItemPedido> criarItensPedido(Carrinho carrinho) {
        List<ItemPedido> itensPedido = new ArrayList<>();

        for (ItemCarrinho itemCarrinho : carrinho.getItens()) {
            if (itemCarrinho == null || itemCarrinho.getProduto() == null) {
                throw new ValidacaoException("Existe um item inválido no carrinho.");
            }

            if (itemCarrinho.getQuantidade() == null || itemCarrinho.getQuantidade() <= 0) {
                throw new ValidacaoException("Existe um item com quantidade inválida no carrinho.");
            }

            if (itemCarrinho.getProduto().getPreco() == null || itemCarrinho.getProduto().getPreco().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidacaoException("O produto " + itemCarrinho.getProduto().getTitulo() + " possui preço inválido.");
            }

            ItemPedido itemPedido = new ItemPedido(
                    itemCarrinho.getProduto(),
                    itemCarrinho.getQuantidade(),
                    itemCarrinho.getProduto().getPreco());

            itensPedido.add(itemPedido);
        }

        return itensPedido;
    }

    private void validarCarrinho(Carrinho carrinho) {
        if (carrinho == null) {
            throw new ValidacaoException("Carrinho não encontrado.");
        }

        if (carrinho.isVazio()) {
            throw new ValidacaoException("O carrinho está vazio.");
        }

        if (carrinho.getItens() == null || carrinho.getItens().isEmpty()) {
            throw new ValidacaoException("O carrinho deve possuir pelo menos um item.");
        }
    }

    private void validarEstoque(Carrinho carrinho) {
        for (ItemCarrinho item : carrinho.getItens()) {
            if (item == null) {
                throw new ValidacaoException("Existe um item inválido no carrinho.");
            }

            if (item.getProduto() == null) {
                throw new ValidacaoException("Existe um item sem produto associado no carrinho.");
            }

            if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
                throw new ValidacaoException("A quantidade do produto " + item.getProduto().getTitulo() + " deve ser maior que zero.");
            }

            if (!item.getProduto().isAtivo()) {
                throw new ValidacaoException("O livro " + item.getProduto().getTitulo() + " não está disponível para compra.");
            }

            if (!item.getProduto().temEstoque(item.getQuantidade())) {
                throw new ValidacaoException("Estoque insuficiente para o livro " + item.getProduto().getTitulo()
                        + ". Quantidade solicitada: " + item.getQuantidade() + ". Estoque disponível: " + item.getProduto().getEstoque() + ".");
            }
        }
    }

    private void validarPagamento(Pagamento pagamento, BigDecimal valorTotalPedido) {
        if (pagamento == null) {
            throw new ValidacaoException("Pagamento é obrigatório.");
        }

        if (pagamento.getStatus() == null) {
            throw new ValidacaoException("O pagamento possui status inválido.");
        }

        if (pagamento.getStatus() == StatusPagamento.CANCELADO) {
            throw new ValidacaoException("O pagamento informado foi cancelado.");
        }

        if (pagamento.getStatus() == StatusPagamento.REJEITADO) {
            throw new ValidacaoException("O pagamento informado foi rejeitado.");
        }

        BigDecimal valorPagamento = pagamento.getValorOriginal();

        if (valorPagamento == null) {
            throw new ValidacaoException("O pagamento não possui valor definido.");
        }

        BigDecimal valorPagamentoNormalizado = valorPagamento.setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorPedidoNormalizado = valorTotalPedido.setScale(2, RoundingMode.HALF_UP);

        if (valorPagamentoNormalizado.compareTo(valorPedidoNormalizado) != 0) {
            throw new ValidacaoException("O valor do pagamento não corresponde " + "ao valor total da compra. "
                    + "Valor da compra: R$ " + valorPedidoNormalizado + ". Valor do pagamento: R$ " + valorPagamentoNormalizado + ".");
        }
    }

    private BigDecimal calcularValorTotal(BigDecimal subtotal, BigDecimal descontoPromocional, BigDecimal valorFrete) {
        BigDecimal subtotalNormalizado = subtotal != null ? subtotal : BigDecimal.ZERO;
        BigDecimal descontoNormalizado = descontoPromocional != null ? descontoPromocional : BigDecimal.ZERO;
        BigDecimal freteNormalizado = valorFrete != null ? valorFrete : BigDecimal.ZERO;

        if (descontoNormalizado.compareTo(subtotalNormalizado) > 0) {
            descontoNormalizado = subtotalNormalizado;
        }

        return subtotalNormalizado.subtract(descontoNormalizado)
                .add(freteNormalizado).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validarIdsObrigatorios(Long clienteId, Long enderecoEntregaId, Long pagamentoId) {
        validarClienteId(clienteId);

        if (enderecoEntregaId == null || enderecoEntregaId <= 0) {
            throw new ValidacaoException("Endereço de entrega é obrigatório.");
        }

        if (pagamentoId == null || pagamentoId <= 0) {
            throw new ValidacaoException("Pagamento é obrigatório.");
        }
    }

    private void validarClienteId(Long clienteId) {
        if (clienteId == null || clienteId <= 0) {
            throw new ValidacaoException("Cliente é obrigatório.");
        }
    }

    private void validarPedidoId(Long pedidoId) {
        if (pedidoId == null || pedidoId <= 0) {
            throw new ValidacaoException("Pedido é obrigatório.");
        }
    }

    private Cliente buscarCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ValidacaoException("Cliente não encontrado."));
    }

    private Endereco buscarEnderecoEntrega(Long clienteId, Long enderecoId) {
        Endereco endereco = enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new ValidacaoException("Endereço de entrega não encontrado."));

        if (endereco.getCliente() == null || endereco.getCliente().getId() == null || !clienteId.equals(endereco.getCliente().getId())) {
            throw new ValidacaoException("O endereço informado não pertence ao cliente.");
        }

        if (!endereco.isEnderecoEntrega()) {
            throw new ValidacaoException("O endereço informado não está habilitado para entrega.");
        }

        return endereco;
    }

    private Pagamento buscarPagamento(Long pagamentoId) {
        return pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new ValidacaoException("Pagamento não encontrado."));
    }

    @Transactional(readOnly = true)
    public Pedido buscarPorId(Long pedidoId) {
        validarPedidoId(pedidoId);

        return pedidoRepository.findByIdComDetalhes(pedidoId)
                .orElseThrow(() -> new ValidacaoException("Pedido não encontrado."));
    }

    @Transactional(readOnly = true)
    public Pedido buscarPorIdECliente(Long pedidoId, Long clienteId) {
        validarPedidoId(pedidoId);
        validarClienteId(clienteId);

        return pedidoRepository.findByIdEClienteIdComDetalhes(pedidoId, clienteId)
                .orElseThrow(() -> new ValidacaoException("Pedido não encontrado para o cliente selecionado."));
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarTodos() {

        return pedidoRepository.findAllByOrderByDataCriacaoDesc();
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarPorCliente(Long clienteId) {
        validarClienteId(clienteId);
        buscarCliente(clienteId);

        return pedidoRepository.findByClienteIdComItens(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarPorStatus(StatusPedido status) {
        if (status == null) {
            return listarTodos();
        }

        return pedidoRepository.findByStatusOrderByDataCriacaoDesc(status);
    }

    /*
     * ==========================================================
     * LISTAGEM PAGINADA PARA O ADMIN
     * ==========================================================
     *
     * Utilizada pela tela /admin/pedidos.
     *
     * Normaliza a busca textual: se for nula ou vazia, passa
     * null para o repository, ativando o filtro "sem busca".
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public Page<Pedido> listarPaginado(String busca, StatusPedido status, Pageable pageable) {
        String buscaNormalizada = (busca == null || busca.isBlank()) ? null : busca.trim();

        return pedidoRepository.buscarComFiltros(buscaNormalizada, status, pageable);
    }

    /*
     * ==========================================================
     * CONTAGENS PARA KPIs DO ADMIN
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public long contarPedidosHoje() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();

        return pedidoRepository.contarPedidosAPartirDe(inicioDia);
    }

    @Transactional(readOnly = true)
    public long contarPorStatus(StatusPedido status) {
        if (status == null) {
            return 0L;
        }

        return pedidoRepository.countByStatus(status);
    }

    /*
     * ==========================================================
     * ATUALIZAÇÃO DE STATUS
     * ==========================================================
     *
     * Altera o status de um pedido, validando se a transição é
     * permitida pelas regras de negócio.
     *
     * Transições válidas:
     * - EM_PROCESSAMENTO -> APROVADO | CANCELADO
     * - APROVADO         -> EM_TRANSPORTE | CANCELADO
     * - EM_TRANSPORTE    -> ENTREGUE
     * - ENTREGUE         -> EM_TROCA
     * - EM_TROCA         -> TROCA_AUTORIZADA | TROCA_RECUSADA | ENTREGUE
     * - TROCA_AUTORIZADA -> EM_TRANSPORTE
     * - TROCA_RECUSADA   -> (estado final)
     * - CANCELADO        -> (estado final)
     * ==========================================================
     */

    @Transactional
    public Pedido atualizarStatus(Long pedidoId, StatusPedido novoStatus) {
        validarPedidoId(pedidoId);

        if (novoStatus == null) {
            throw new ValidacaoException("Novo status é obrigatório.");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ValidacaoException("Pedido não encontrado."));

        StatusPedido statusAtual = pedido.getStatus();

        if (statusAtual == null) {
            throw new ValidacaoException("O pedido não possui status definido.");
        }

        if (statusAtual == novoStatus) {
            return pedido;
        }

        if (!transicaoPermitida(statusAtual, novoStatus)) {
            throw new ValidacaoException(
                    "Não é possível alterar o status de '"
                            + statusAtual.getDescricao()
                            + "' para '"
                            + novoStatus.getDescricao()
                            + "'.");
        }

        pedido.setStatus(novoStatus);

        Pedido pedidoSalvo = pedidoRepository.save(pedido);

        logger.info("Status do pedido alterado. pedidoId={}, de={}, para={}",
                pedidoId,
                statusAtual,
                novoStatus);

        return pedidoSalvo;
    }

    private boolean transicaoPermitida(StatusPedido atual, StatusPedido novo) {
        if (atual == null || novo == null) {
            return false;
        }

        return switch (atual) {
            case EM_PROCESSAMENTO ->
                    novo == StatusPedido.APROVADO
                            || novo == StatusPedido.CANCELADO;

            case APROVADO ->
                    novo == StatusPedido.EM_TRANSPORTE
                            || novo == StatusPedido.CANCELADO;

            case EM_TRANSPORTE ->
                    novo == StatusPedido.ENTREGUE;

            case ENTREGUE ->
                    novo == StatusPedido.EM_TROCA;

            case EM_TROCA ->
                    novo == StatusPedido.TROCA_AUTORIZADA
                            || novo == StatusPedido.TROCA_RECUSADA
                            || novo == StatusPedido.ENTREGUE;

            case TROCA_AUTORIZADA ->
                    novo == StatusPedido.EM_TRANSPORTE;

            case TROCA_RECUSADA ->
                    false;   // estado final

            case CANCELADO ->
                    false;   // estado final
        };
    }

    private BigDecimal normalizarValorNaoNegativo(BigDecimal valor, String nomeCampo) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidacaoException(nomeCampo + " não pode ser negativo.");
        }

        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}