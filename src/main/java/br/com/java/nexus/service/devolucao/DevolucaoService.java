package br.com.java.nexus.service.devolucao;

import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.devolucao.Devolucao;
import br.com.java.nexus.model.devolucao.ItemDevolucao;
import br.com.java.nexus.model.devolucao.enums.MotivoDevolucao;
import br.com.java.nexus.model.pedido.ItemPedido;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;
import br.com.java.nexus.model.venda.Cupom;
import br.com.java.nexus.repository.cliente.ClienteRepository;
import br.com.java.nexus.repository.devolucao.DevolucaoRepository;
import br.com.java.nexus.repository.pedido.PedidoRepository;
import br.com.java.nexus.service.exception.ValidacaoException;
import br.com.java.nexus.service.pedido.PedidoService;
import br.com.java.nexus.service.venda.CupomService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class DevolucaoService {

    private static final Logger logger = LoggerFactory.getLogger(DevolucaoService.class);

    /**
     * Status do pedido que indicam devolução em andamento.
     * Usados para validar se um item já está em outra devolução ativa.
     *
     * Inclui EM_TRANSPORTE, pois a devolução passa por essa fase
     * logística antes de ser concluída em ENTREGUE.
     */
    private static final List<StatusPedido> STATUS_DEVOLUCAO_ATIVA = List.of(
            StatusPedido.EM_TROCA,
            StatusPedido.TROCA_AUTORIZADA,
            StatusPedido.EM_TRANSPORTE
    );

    private final DevolucaoRepository devolucaoRepository;
    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final PedidoService pedidoService;
    private final CupomService cupomService;


    public DevolucaoService(DevolucaoRepository devolucaoRepository,
                            PedidoRepository pedidoRepository,
                            ClienteRepository clienteRepository,
                            PedidoService pedidoService,
                            CupomService cupomService) {

        this.devolucaoRepository = devolucaoRepository;
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.pedidoService = pedidoService;
        this.cupomService = cupomService;
    }


    /*
     * ==========================================================
     * SOLICITAÇÃO DE DEVOLUÇÃO (CLIENTE)
     * ==========================================================
     */

    @Transactional
    public Devolucao solicitarDevolucao(Long pedidoId,
                                        Long clienteId,
                                        List<Long> itensPedidoIds,
                                        MotivoDevolucao motivo,
                                        String justificativa) {

        validarIdsObrigatorios(pedidoId, clienteId, motivo);

        if (itensPedidoIds == null || itensPedidoIds.isEmpty()) {
            throw new ValidacaoException("Selecione ao menos um item para devolução.");
        }

        Pedido pedido = buscarPedido(pedidoId);
        Cliente cliente = validarPosseDoPedido(pedido, clienteId);

        validarPedidoEntregue(pedido);
        validarSemDevolucaoAtiva(pedido);

        List<ItemPedido> itensSelecionados = filtrarItensDoPedido(pedido, itensPedidoIds);
        validarItensNaoDevolvidos(itensSelecionados);

        BigDecimal valorItens = calcularValorItens(itensSelecionados);
        BigDecimal parcelaFrete = calcularParcelaFrete(pedido, itensSelecionados);
        BigDecimal valorTotal = valorItens.add(parcelaFrete)
                .setScale(2, RoundingMode.HALF_UP);

        Devolucao devolucao = new Devolucao();
        devolucao.setPedido(pedido);
        devolucao.setCliente(cliente);
        devolucao.setMotivo(motivo);
        devolucao.setJustificativa(justificativa);
        devolucao.setValorSolicitado(valorTotal);
        devolucao.setDataSolicitacao(LocalDateTime.now());

        for (ItemPedido item : itensSelecionados) {
            ItemDevolucao itemDevolucao = new ItemDevolucao(
                    item,
                    item.getQuantidade(),
                    item.getPrecoUnitario()
            );
            devolucao.adicionarItem(itemDevolucao);
        }

        Devolucao salva = devolucaoRepository.save(devolucao);

        pedidoService.atualizarStatus(pedidoId, StatusPedido.EM_TROCA);

        logger.info("Devolução solicitada. devolucaoId={}, pedidoId={}, clienteId={}, " +
                        "itens={}, valorSolicitado={}",
                salva.getId(), pedidoId, clienteId,
                itensSelecionados.size(), valorTotal);

        return salva;
    }


    @Transactional
    public Devolucao solicitarDevolucaoTotal(Long pedidoId,
                                             Long clienteId,
                                             MotivoDevolucao motivo,
                                             String justificativa) {

        validarIdsObrigatorios(pedidoId, clienteId, motivo);

        Pedido pedido = buscarPedido(pedidoId);
        Cliente cliente = validarPosseDoPedido(pedido, clienteId);

        validarPedidoEntregue(pedido);
        validarSemDevolucaoAtiva(pedido);

        List<ItemPedido> todosItens = new ArrayList<>(pedido.getItens());

        if (todosItens.isEmpty()) {
            throw new ValidacaoException("O pedido não possui itens para devolução.");
        }

        validarItensNaoDevolvidos(todosItens);

        BigDecimal valorTotal = pedido.getValorTotal()
                .setScale(2, RoundingMode.HALF_UP);

        Devolucao devolucao = new Devolucao();
        devolucao.setPedido(pedido);
        devolucao.setCliente(cliente);
        devolucao.setMotivo(motivo);
        devolucao.setJustificativa(justificativa);
        devolucao.setValorSolicitado(valorTotal);
        devolucao.setDataSolicitacao(LocalDateTime.now());

        for (ItemPedido item : todosItens) {
            ItemDevolucao itemDevolucao = new ItemDevolucao(
                    item,
                    item.getQuantidade(),
                    item.getPrecoUnitario()
            );
            devolucao.adicionarItem(itemDevolucao);
        }

        Devolucao salva = devolucaoRepository.save(devolucao);

        pedidoService.atualizarStatus(pedidoId, StatusPedido.EM_TROCA);

        logger.info("Devolução total solicitada. devolucaoId={}, pedidoId={}, " +
                        "clienteId={}, valorSolicitado={}",
                salva.getId(), pedidoId, clienteId, valorTotal);

        return salva;
    }


    /*
     * ==========================================================
     * AÇÕES DO ADMIN
     * ==========================================================
     */

    @Transactional
    public Devolucao autorizarDevolucao(Long devolucaoId,
                                        BigDecimal valorAprovado,
                                        String observacaoAdmin) {

        validarDevolucaoId(devolucaoId);

        Devolucao devolucao = buscarPorId(devolucaoId);
        Pedido pedido = devolucao.getPedido();

        if (pedido.getStatus() != StatusPedido.EM_TROCA) {
            throw new ValidacaoException(
                    "Só é possível autorizar devoluções de pedidos com status 'Em troca'. " +
                            "Status atual: '" + pedido.getStatus().getDescricao() + "'.");
        }

        BigDecimal valorFinal = definirValorAprovado(devolucao, valorAprovado);

        devolucao.autorizar(valorFinal, observacaoAdmin);
        Devolucao salva = devolucaoRepository.save(devolucao);

        pedidoService.atualizarStatus(pedido.getId(), StatusPedido.TROCA_AUTORIZADA);

        logger.info("Devolução autorizada. devolucaoId={}, pedidoId={}, valorAprovado={}",
                devolucaoId, pedido.getId(), valorFinal);

        return salva;
    }


    @Transactional
    public Devolucao recusarDevolucao(Long devolucaoId,
                                      String observacaoAdmin) {

        validarDevolucaoId(devolucaoId);

        Devolucao devolucao = buscarPorId(devolucaoId);
        Pedido pedido = devolucao.getPedido();

        if (pedido.getStatus() != StatusPedido.EM_TROCA) {
            throw new ValidacaoException(
                    "Só é possível recusar devoluções de pedidos com status 'Em troca'. " +
                            "Status atual: '" + pedido.getStatus().getDescricao() + "'.");
        }

        devolucao.recusar(observacaoAdmin);
        Devolucao salva = devolucaoRepository.save(devolucao);

        pedidoService.atualizarStatus(pedido.getId(), StatusPedido.TROCA_RECUSADA);

        logger.info("Devolução recusada. devolucaoId={}, pedidoId={}",
                devolucaoId, pedido.getId());

        return salva;
    }


    /**
     * Admin avança o status de uma devolução já autorizada.
     *
     * Fluxo: TROCA_AUTORIZADA → EM_TRANSPORTE → ENTREGUE
     *
     * Delega a validação de transição ao PedidoService, que já
     * conhece todas as regras.
     *
     * A confirmação de entrega (ENTREGUE) é feita via
     * confirmarRecebimento(), que também gera o cupom e aplica a
     * decisão de estoque item a item.
     *
     * Este método serve para avançar de TROCA_AUTORIZADA para
     * EM_TRANSPORTE.
     */
    @Transactional
    public Devolucao avancarStatusDevolucao(Long devolucaoId,
                                            StatusPedido novoStatus) {

        validarDevolucaoId(devolucaoId);

        if (novoStatus == null) {
            throw new ValidacaoException("Novo status é obrigatório.");
        }

        Devolucao devolucao = buscarPorId(devolucaoId);
        Pedido pedido = devolucao.getPedido();

        StatusPedido statusAtual = pedido.getStatus();

        /*
         * Só permite avançar quando o pedido está em TROCA_AUTORIZADA.
         * O avanço para ENTREGUE é feito por confirmarRecebimento(),
         * pois envolve checklist de estoque + geração de cupom.
         */
        if (statusAtual != StatusPedido.TROCA_AUTORIZADA) {
            throw new ValidacaoException(
                    "Só é possível avançar devoluções com status 'Troca autorizada'. " +
                            "Status atual: '" + statusAtual.getDescricao() + "'.");
        }

        if (novoStatus != StatusPedido.EM_TRANSPORTE) {
            throw new ValidacaoException(
                    "O próximo status da devolução é 'Em transporte'. " +
                            "Use a confirmação de recebimento para concluir.");
        }

        pedidoService.atualizarStatus(pedido.getId(), novoStatus);

        logger.info("Status da devolução avançado. devolucaoId={}, pedidoId={}, de={}, para={}",
                devolucaoId, pedido.getId(), statusAtual, novoStatus);

        return buscarPorId(devolucaoId);
    }


    /**
     * Admin confirma o recebimento dos itens devolvidos.
     *
     * Pré-requisito: pedido com status EM_TRANSPORTE (a troca já
     * foi autorizada e despachada).
     *
     * Efeitos:
     * - Aplica a decisão de estoque item a item.
     * - Registra dataRecebimento.
     * - Gera o cupom de troca.
     * - Muda o status do pedido para ENTREGUE (estado final).
     */
    @Transactional
    public Devolucao confirmarRecebimento(Long devolucaoId,
                                          Map<Long, Boolean> itensDevolverAoEstoque) {

        validarDevolucaoId(devolucaoId);

        Devolucao devolucao = buscarPorId(devolucaoId);
        Pedido pedido = devolucao.getPedido();

        if (pedido.getStatus() != StatusPedido.EM_TRANSPORTE) {
            throw new ValidacaoException(
                    "Só é possível confirmar recebimento de devoluções com status 'Em transporte'. " +
                            "Status atual: '" + pedido.getStatus().getDescricao() + "'.");
        }

        if (devolucao.getDataRecebimento() != null) {
            throw new ValidacaoException("O recebimento desta devolução já foi confirmado.");
        }

        if (devolucao.getItens() == null || devolucao.getItens().isEmpty()) {
            throw new ValidacaoException("A devolução não possui itens.");
        }

        aplicarDecisaoEstoque(devolucao, itensDevolverAoEstoque);
        devolucao.registrarRecebimento();
        devolucaoRepository.save(devolucao);

        Cupom cupom = cupomService.gerarCupomTrocaPorDevolucao(
                devolucao.getCliente().getId(),
                devolucao.getId(),
                devolucao.getValorEfetivo()
        );

        devolucao.setCupomGerado(cupom);
        Devolucao salva = devolucaoRepository.save(devolucao);

        /*
         * Estado final: a troca foi concluída.
         * O pedido passa para ENTREGUE.
         */
        pedidoService.atualizarStatus(pedido.getId(), StatusPedido.ENTREGUE);

        logger.info("Recebimento confirmado e cupom gerado. devolucaoId={}, " +
                        "pedidoId={}, cupom={}, valorCupom={}",
                devolucaoId, pedido.getId(), cupom.getCodigo(), cupom.getValor());

        return salva;
    }


    /**
     * Cliente cancela a própria devolução (só antes de autorizada).
     *
     * Efeitos:
     * - Remove a Devolucao.
     * - Volta o status do pedido para ENTREGUE.
     *
     * Observação: para isso funcionar, o PedidoService precisa
     * permitir a transição EM_TROCA -> ENTREGUE no
     * transicaoPermitida.
     */
    @Transactional
    public void cancelarDevolucao(Long devolucaoId, Long clienteId) {

        validarDevolucaoId(devolucaoId);
        validarClienteId(clienteId);

        Devolucao devolucao = buscarPorId(devolucaoId);

        if (!devolucao.getCliente().getId().equals(clienteId)) {
            throw new ValidacaoException("Esta devolução não pertence a este cliente.");
        }

        Pedido pedido = devolucao.getPedido();

        if (pedido.getStatus() != StatusPedido.EM_TROCA) {
            throw new ValidacaoException(
                    "Só é possível cancelar devoluções com status 'Em troca'. " +
                            "Status atual: '" + pedido.getStatus().getDescricao() + "'.");
        }

        devolucaoRepository.delete(devolucao);

        pedidoService.atualizarStatus(pedido.getId(), StatusPedido.ENTREGUE);

        logger.info("Devolução cancelada pelo cliente. devolucaoId={}, pedidoId={}",
                devolucaoId, pedido.getId());
    }


    /*
     * ==========================================================
     * CONSULTAS
     * ==========================================================
     */

    @Transactional(readOnly = true)
    public Devolucao buscarPorId(Long id) {
        validarDevolucaoId(id);

        return devolucaoRepository.findByIdComDetalhes(id)
                .orElseThrow(() -> new ValidacaoException("Devolução não encontrada."));
    }


    @Transactional(readOnly = true)
    public List<Devolucao> listarTodas() {
        return devolucaoRepository.findAllComDetalhes();
    }


    @Transactional(readOnly = true)
    public List<Devolucao> listarPorCliente(Long clienteId) {
        validarClienteId(clienteId);
        return devolucaoRepository.findByClienteIdComDetalhes(clienteId);
    }


    @Transactional(readOnly = true)
    public List<Devolucao> listarPorStatusDoPedido(StatusPedido status) {
        if (status == null) {
            return listarTodas();
        }
        return devolucaoRepository.findByPedidoStatusComDetalhes(status);
    }


    @Transactional(readOnly = true)
    public long contarPorStatusDoPedido(StatusPedido status) {
        if (status == null) {
            return 0L;
        }
        return devolucaoRepository.countByPedidoStatus(status);
    }


    /*
     * ==========================================================
     * VALIDAÇÕES PRIVADAS
     * ==========================================================
     */

    private void validarIdsObrigatorios(Long pedidoId, Long clienteId, MotivoDevolucao motivo) {
        validarPedidoId(pedidoId);
        validarClienteId(clienteId);

        if (motivo == null) {
            throw new ValidacaoException("O motivo da devolução é obrigatório.");
        }
    }


    private void validarPedidoId(Long pedidoId) {
        if (pedidoId == null || pedidoId <= 0) {
            throw new ValidacaoException("Pedido é obrigatório.");
        }
    }


    private void validarClienteId(Long clienteId) {
        if (clienteId == null || clienteId <= 0) {
            throw new ValidacaoException("Cliente é obrigatório.");
        }
    }


    private void validarDevolucaoId(Long devolucaoId) {
        if (devolucaoId == null || devolucaoId <= 0) {
            throw new ValidacaoException("Devolução é obrigatória.");
        }
    }


    private Pedido buscarPedido(Long pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ValidacaoException("Pedido não encontrado."));
    }


    private Cliente validarPosseDoPedido(Pedido pedido, Long clienteId) {
        if (pedido.getCliente() == null
                || pedido.getCliente().getId() == null
                || !pedido.getCliente().getId().equals(clienteId)) {

            throw new ValidacaoException("Este pedido não pertence ao cliente informado.");
        }

        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ValidacaoException("Cliente não encontrado."));
    }


    private void validarPedidoEntregue(Pedido pedido) {
        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new ValidacaoException(
                    "A devolução só pode ser solicitada para pedidos com status 'Entregue'. " +
                            "Status atual: '" + pedido.getStatus().getDescricao() + "'.");
        }
    }


    private void validarSemDevolucaoAtiva(Pedido pedido) {
        if (devolucaoRepository.existsByPedidoId(pedido.getId())) {
            throw new ValidacaoException(
                    "Já existe uma devolução em andamento para este pedido.");
        }
    }


    /**
     * Filtra os itens do pedido de acordo com os IDs informados.
     *
     * Como o ItemPedido não tem referência direta ao Pedido, iteramos
     * pedido.getItens() e cruzamos com os IDs informados. Dessa
     * forma, garantimos que os itens selecionados pertencem ao
     * pedido e evitamos que um cliente mal-intencionado passe IDs
     * de itens de outro pedido.
     */
    private List<ItemPedido> filtrarItensDoPedido(Pedido pedido, List<Long> itensIds) {
        Set<Long> idsSelecionados = itensIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ItemPedido> itens = pedido.getItens().stream()
                .filter(item -> item.getId() != null
                        && idsSelecionados.contains(item.getId()))
                .collect(Collectors.toList());

        if (itens.isEmpty()) {
            throw new ValidacaoException("Nenhum item válido foi selecionado.");
        }

        if (itens.size() != idsSelecionados.size()) {
            throw new ValidacaoException(
                    "Um ou mais itens selecionados não pertencem a este pedido.");
        }

        return itens;
    }


    private void validarItensNaoDevolvidos(List<ItemPedido> itens) {
        for (ItemPedido item : itens) {
            if (devolucaoRepository.existsItemEmDevolucaoAtiva(
                    item.getId(), STATUS_DEVOLUCAO_ATIVA)) {

                String titulo = item.getProduto() != null
                        ? item.getProduto().getTitulo()
                        : "produto";

                throw new ValidacaoException(
                        "O item '" + titulo + "' já está em uma devolução ativa.");
            }
        }
    }


    private BigDecimal calcularValorItens(List<ItemPedido> itens) {
        return itens.stream()
                .map(ItemPedido::getPrecoTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }


    /**
     * Calcula a parcela proporcional do frete para os itens selecionados.
     *
     * Fórmula: (frete_total * subtotal_selecionados) / subtotal_total
     */
    private BigDecimal calcularParcelaFrete(Pedido pedido, List<ItemPedido> itensSelecionados) {
        BigDecimal freteTotal = pedido.getValorFrete();

        if (freteTotal == null || freteTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal subtotalTotal = pedido.getSubtotal();

        if (subtotalTotal == null || subtotalTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal subtotalSelecionados = itensSelecionados.stream()
                .map(ItemPedido::getPrecoTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (subtotalSelecionados.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return freteTotal
                .multiply(subtotalSelecionados)
                .divide(subtotalTotal, 2, RoundingMode.HALF_EVEN);
    }


    private BigDecimal definirValorAprovado(Devolucao devolucao, BigDecimal valorAprovado) {
        BigDecimal solicitado = devolucao.getValorSolicitado();

        if (valorAprovado == null || valorAprovado.compareTo(BigDecimal.ZERO) <= 0) {
            return solicitado;
        }

        return valorAprovado.min(solicitado).setScale(2, RoundingMode.HALF_UP);
    }


    /**
     * Aplica a decisão do admin sobre quais itens voltam ao estoque.
     * Se o mapa for nulo, todos voltam (comportamento padrão).
     *
     * Quando um item volta ao estoque, incrementa produto.estoque.
     */
    private void aplicarDecisaoEstoque(Devolucao devolucao,
                                       Map<Long, Boolean> itensDevolverAoEstoque) {

        for (ItemDevolucao item : devolucao.getItens()) {

            Boolean devolver = null;

            if (itensDevolverAoEstoque != null && item.getId() != null) {
                devolver = itensDevolverAoEstoque.get(item.getId());
            }

            /*
             * Se o admin não decidiu, mantemos o padrão:
             * todos os itens voltam ao estoque.
             */
            if (devolver == null) {
                devolver = Boolean.TRUE;
            }

            item.setDevolverAoEstoque(devolver);

            if (devolver && item.getItemPedido() != null
                    && item.getItemPedido().getProduto() != null) {

                var produto = item.getItemPedido().getProduto();
                Integer estoqueAtual = produto.getEstoque();

                if (estoqueAtual == null) {
                    estoqueAtual = 0;
                }

                produto.setEstoque(estoqueAtual + item.getQuantidade());
            }
        }
    }
}