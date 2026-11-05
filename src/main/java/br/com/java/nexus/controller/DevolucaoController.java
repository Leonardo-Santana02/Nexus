package br.com.java.nexus.controller;

import br.com.java.nexus.model.devolucao.Devolucao;
import br.com.java.nexus.model.devolucao.ItemDevolucao;
import br.com.java.nexus.model.devolucao.enums.MotivoDevolucao;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;

import br.com.java.nexus.service.cliente.ClienteService;
import br.com.java.nexus.service.devolucao.DevolucaoService;
import br.com.java.nexus.service.exception.ValidacaoException;
import br.com.java.nexus.service.pedido.PedidoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class DevolucaoController {

    private static final String SESSAO_CLIENTE = "clienteAtual";

    private final DevolucaoService devolucaoService;
    private final PedidoService pedidoService;
    private final ClienteService clienteService;

    public DevolucaoController(DevolucaoService devolucaoService,
                               PedidoService pedidoService,
                               ClienteService clienteService) {
        this.devolucaoService = devolucaoService;
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
    }


    /*
     * ==========================================================
     * PÁGINA DE SOLICITAÇÃO (CLIENTE)
     * ==========================================================
     */

    @GetMapping("/devolucoes/solicitar/{pedidoId}")
    public String formSolicitarDevolucao(@PathVariable Long pedidoId,
                                         HttpServletRequest request,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {

        try {
            HttpSession session = request.getSession();
            Long clienteId = obterLongSessao(session, SESSAO_CLIENTE);

            if (clienteId == null) {
                redirectAttributes.addFlashAttribute("erro",
                        "Selecione um cliente antes de solicitar a devolução.");
                return "redirect:/home";
            }

            Pedido pedido = pedidoService.buscarPorIdECliente(pedidoId, clienteId);

            if (pedido.getStatus() != StatusPedido.ENTREGUE) {
                redirectAttributes.addFlashAttribute("erro",
                        "Só é possível solicitar devolução de pedidos entregues. " +
                                "Status atual: '" + pedido.getStatus().getDescricao() + "'.");
                return "redirect:/pedidos";
            }

            model.addAttribute("pedido", pedido);
            model.addAttribute("motivos", MotivoDevolucao.values());
            model.addAttribute("cliente", pedido.getCliente());

            return "cliente/solicitar-troca";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível carregar o pedido para devolução."));
            return "redirect:/pedidos";
        }
    }


    /*
     * ==========================================================
     * PROCESSAR SOLICITAÇÃO (CLIENTE)
     * ==========================================================
     */

    @PostMapping("/devolucoes/solicitar")
    public String solicitarDevolucao(@RequestParam Long pedidoId,
                                     @RequestParam(required = false) List<Long> itensIds,
                                     @RequestParam MotivoDevolucao motivo,
                                     @RequestParam(required = false) String justificativa,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {

        HttpSession session = request.getSession();
        Long clienteId = obterLongSessao(session, SESSAO_CLIENTE);

        if (clienteId == null) {
            redirectAttributes.addFlashAttribute("erro",
                    "Selecione um cliente antes de solicitar a devolução.");
            return "redirect:/home";
        }

        try {
            Devolucao devolucao;

            if (itensIds == null || itensIds.isEmpty()) {
                devolucao = devolucaoService.solicitarDevolucaoTotal(
                        pedidoId, clienteId, motivo, justificativa);
            } else {
                devolucao = devolucaoService.solicitarDevolucao(
                        pedidoId, clienteId, itensIds, motivo, justificativa);
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Solicitação de devolução #" + devolucao.getId() +
                            " registrada com sucesso. Aguarde a análise do administrador.");

            return "redirect:/pedidos";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível registrar a devolução."));
            return "redirect:/devolucoes/solicitar/" + pedidoId;
        }
    }


    /*
     * ==========================================================
     * CANCELAR DEVOLUÇÃO (CLIENTE)
     * ==========================================================
     */

    @PostMapping("/devolucoes/{id}/cancelar")
    public String cancelarDevolucao(@PathVariable Long id,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {

        HttpSession session = request.getSession();
        Long clienteId = obterLongSessao(session, SESSAO_CLIENTE);

        if (clienteId == null) {
            redirectAttributes.addFlashAttribute("erro",
                    "Selecione um cliente antes de cancelar a devolução.");
            return "redirect:/home";
        }

        try {
            devolucaoService.cancelarDevolucao(id, clienteId);

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Devolução cancelada com sucesso.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível cancelar a devolução."));
        }

        return "redirect:/pedidos";
    }


    /*
     * ==========================================================
     * LISTAGEM (ADMIN)
     * ==========================================================
     */

    @GetMapping("/admin/devolucoes")
    public String listarTodasAdmin(@RequestParam(required = false) StatusPedido status,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        try {
            List<Devolucao> devolucoes;
            if (status != null) {
                devolucoes = devolucaoService.listarPorStatusDoPedido(status);
            } else {
                devolucoes = devolucaoService.listarTodas();
            }

            long pendentes = devolucaoService.contarPorStatusDoPedido(StatusPedido.EM_TROCA);
            long autorizadas = devolucaoService.contarPorStatusDoPedido(StatusPedido.TROCA_AUTORIZADA);
            long emTransporte = devolucaoService.contarPorStatusDoPedido(StatusPedido.EM_TRANSPORTE);
            long recusadas = devolucaoService.contarPorStatusDoPedido(StatusPedido.TROCA_RECUSADA);

            model.addAttribute("devolucoes", devolucoes);
            model.addAttribute("total", devolucoes.size());
            model.addAttribute("pendentes", pendentes);
            model.addAttribute("autorizadas", autorizadas);
            model.addAttribute("emTransporte", emTransporte);
            model.addAttribute("recusadas", recusadas);
            model.addAttribute("statusSelecionado", status);

            /*
             * Lista de status relevantes para o filtro.
             * Cobre todas as fases da devolução.
             */
            model.addAttribute("statusDisponiveis", List.of(
                    StatusPedido.EM_TROCA,
                    StatusPedido.TROCA_AUTORIZADA,
                    StatusPedido.EM_TRANSPORTE,
                    StatusPedido.ENTREGUE,
                    StatusPedido.TROCA_RECUSADA
            ));

            return "admin/devolucoes/listagem-devolucao";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível carregar as devoluções."));
            return "redirect:/admin/dashboard";
        }
    }


    /*
     * ==========================================================
     * DETALHES (ADMIN)
     * ==========================================================
     */

    @GetMapping("/admin/devolucoes/{id}")
    public String detalhesAdmin(@PathVariable Long id,
                                Model model,
                                RedirectAttributes redirectAttributes) {

        try {
            Devolucao devolucao = devolucaoService.buscarPorId(id);

            model.addAttribute("devolucao", devolucao);
            model.addAttribute("pedido", devolucao.getPedido());
            model.addAttribute("motivos", MotivoDevolucao.values());

            return "admin/devolucoes/detalhes";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível carregar a devolução."));
            return "redirect:/admin/devolucoes";
        }
    }


    /*
     * ==========================================================
     * AUTORIZAR DEVOLUÇÃO (ADMIN)
     * ==========================================================
     */

    @PostMapping("/admin/devolucoes/{id}/autorizar")
    public String autorizarDevolucao(@PathVariable Long id,
                                     @RequestParam(required = false) BigDecimal valorAprovado,
                                     @RequestParam(required = false) String observacao,
                                     RedirectAttributes redirectAttributes) {

        try {
            devolucaoService.autorizarDevolucao(id, valorAprovado, observacao);

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Devolução autorizada com sucesso. O cliente já pode enviar os itens.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível autorizar a devolução."));
        }

        return "redirect:/admin/devolucoes/" + id;
    }


    /*
     * ==========================================================
     * RECUSAR DEVOLUÇÃO (ADMIN)
     * ==========================================================
     */

    @PostMapping("/admin/devolucoes/{id}/recusar")
    public String recusarDevolucao(@PathVariable Long id,
                                   @RequestParam String observacao,
                                   RedirectAttributes redirectAttributes) {

        try {
            devolucaoService.recusarDevolucao(id, observacao);

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Devolução recusada.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível recusar a devolução."));
        }

        return "redirect:/admin/devolucoes/" + id;
    }


    /*
     * ==========================================================
     * AVANÇAR STATUS DA DEVOLUÇÃO (ADMIN)
     * ==========================================================
     *
     * Utilizado pelo botão "Despachar para troca".
     *
     * Fluxo: TROCA_AUTORIZADA → EM_TRANSPORTE
     *
     * A confirmação de entrega (ENTREGUE) é feita via o endpoint
     * /admin/devolucoes/{id}/receber, que também gera o cupom e
     * aplica a decisão de estoque item a item.
     * ==========================================================
     */

    @PostMapping("/admin/devolucoes/{id}/avancar")
    public String avancarStatusDevolucao(@PathVariable Long id,
                                         @RequestParam StatusPedido novoStatus,
                                         RedirectAttributes redirectAttributes) {

        try {
            devolucaoService.avancarStatusDevolucao(id, novoStatus);

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Status da devolução atualizado para '" + novoStatus.getDescricao() + "' com sucesso.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível avançar o status da devolução."));
        }

        return "redirect:/admin/devolucoes/" + id;
    }


    /*
     * ==========================================================
     * CONFIRMAR RECEBIMENTO (ADMIN)
     * ==========================================================
     *
     * Recebe:
     * - id (da devolução)
     * - devolverAoEstoque (Map<Long, Boolean>): para cada item
     *   da devolução, indica se ele volta ao estoque.
     *
     * O template envia este mapa como múltiplos parâmetros:
     *   devolverAoEstoque[123]=true
     *   devolverAoEstoque[124]=false
     *
     * Pré-requisito: pedido com status EM_TRANSPORTE.
     *
     * Ao confirmar:
     * - Aplica a decisão de estoque item a item.
     * - Registra dataRecebimento.
     * - Gera o cupom de troca.
     * - Muda o status do pedido para ENTREGUE (estado final).
     * ==========================================================
     */

    @PostMapping("/admin/devolucoes/{id}/receber")
    public String confirmarRecebimento(@PathVariable Long id,
                                       @RequestParam(required = false)
                                       Map<Long, Boolean> devolverAoEstoque,
                                       RedirectAttributes redirectAttributes) {

        try {
            Devolucao devolucao = devolucaoService.confirmarRecebimento(id, devolverAoEstoque);

            String codigoCupom = devolucao.getCupomGerado() != null
                    ? devolucao.getCupomGerado().getCodigo()
                    : null;

            String mensagem = "Recebimento confirmado com sucesso. A devolução foi concluída.";
            if (codigoCupom != null) {
                mensagem += " Cupom de troca gerado: " + codigoCupom + ".";
            }

            redirectAttributes.addFlashAttribute("mensagemSucesso", mensagem);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível confirmar o recebimento."));
        }

        return "redirect:/admin/devolucoes/" + id;
    }


    /*
     * ==========================================================
     * HELPERS
     * ==========================================================
     */

    private Long obterLongSessao(HttpSession session, String atributo) {
        if (session == null) {
            return null;
        }

        Object valor = session.getAttribute(atributo);
        if (valor == null) {
            return null;
        }

        if (valor instanceof Long) {
            return (Long) valor;
        }

        if (valor instanceof Number) {
            return ((Number) valor).longValue();
        }

        try {
            return Long.valueOf(valor.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private String obterMensagemErro(Exception e, String mensagemPadrao) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return mensagemPadrao;
        }

        return e.getMessage();
    }
}