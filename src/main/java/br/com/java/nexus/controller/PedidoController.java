package br.com.java.nexus.controller;

import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;

import br.com.java.nexus.service.cliente.ClienteService;
import br.com.java.nexus.service.pedido.PedidoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class PedidoController {
    private static final String SESSAO_CLIENTE = "clienteAtual";

    private static final int TAMANHO_PAGINA_PADRAO = 10;
    private static final int TAMANHO_PAGINA_MAXIMO = 50;

    private final PedidoService pedidoService;
    private final ClienteService clienteService;

    public PedidoController(PedidoService pedidoService, ClienteService clienteService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
    }

    /*
     * ==========================================================
     * PÁGINA DE PEDIDOS DO CLIENTE
     * ==========================================================
     */

    @GetMapping({"/pedidos", "/pedidos.html"})
    public String paginaPedidos(@RequestParam(required = false) Long clienteId, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        try {
            HttpSession session = request.getSession();
            Long idCliente = obterClienteIdAtual(clienteId, session);

            if (idCliente == null) {
                redirectAttributes.addFlashAttribute("erro", "Selecione um cliente antes de visualizar os pedidos.");
                return "redirect:/home";
            }

            var cliente = clienteService.buscarPorId(idCliente);

            List<Pedido> pedidos = pedidoService.listarPorCliente(idCliente);

            model.addAttribute("cliente", cliente);
            model.addAttribute("pedidos", pedidos);
            model.addAttribute("quantidadePedidos", pedidos.size());

            session.setAttribute(SESSAO_CLIENTE, idCliente);

            return "cliente/pedidos";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", obterMensagemErro(e, "Não foi possível carregar os pedidos."));

            return "redirect:/home";
        }
    }

    /*
     * ==========================================================
     * PÁGINA DE PEDIDOS DO ADMIN
     * ==========================================================
     *
     * Renderiza admin/pedidos/lista-pedidos.html com:
     * - lista paginada de pedidos;
     * - filtros de busca e status;
     * - KPIs;
     * - metadados de paginação.
     *
     * Parâmetros aceitos via query string:
     * - page  (default 0)
     * - size  (default 10, máximo 50)
     * - busca (opcional)
     * - status (opcional)
     * ==========================================================
     */

    @GetMapping({"/admin/pedidos", "/admin/pedidos/lista"})
    public String paginaPedidosAdmin(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusPedido status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Pageable pageable = montarPageable(page, size);

            Page<Pedido> paginaPedidos = pedidoService.listarPaginado(busca, status, pageable);

            model.addAttribute("pedidos", paginaPedidos.getContent());
            model.addAttribute("paginaAtual", paginaPedidos.getNumber());
            model.addAttribute("totalPaginas", paginaPedidos.getTotalPages());
            model.addAttribute("totalElementos", paginaPedidos.getTotalElements());
            model.addAttribute("statusSelecionado", status);
            model.addAttribute("busca", busca);

            model.addAttribute("statusDisponiveis", StatusPedido.values());

            /*
             * KPIs do painel admin.
             *
             * Agora que o domínio possui o status EM_TROCA, o
             * KPI "trocas pendentes" passa a ser real, contando
             * os pedidos aguardando análise de troca.
             */
            model.addAttribute("pedidosHoje", pedidoService.contarPedidosHoje());
            model.addAttribute("emTransporte", pedidoService.contarPorStatus(StatusPedido.EM_TRANSPORTE));
            model.addAttribute("trocasPendentes", pedidoService.contarPorStatus(StatusPedido.EM_TROCA));

            return "admin/pedidos/lista-pedidos";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível carregar a lista de pedidos."));

            return "redirect:/admin/dashboard";
        }
    }

    /*
     * ==========================================================
     * DETALHES DO PEDIDO (ADMIN)
     * ==========================================================
     *
     * Renderiza a página de detalhes do pedido no painel admin.
     * O template é admin/pedidos/lista-pedidos-detalhes.html.
     * ==========================================================
     */

    @GetMapping("/admin/pedidos/{pedidoId}")
    public String detalhesPedidoAdmin(
            @PathVariable Long pedidoId,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Pedido pedido = pedidoService.buscarPorId(pedidoId);

            model.addAttribute("pedido", pedido);

            return "admin/pedidos/lista-pedidos-detalhes";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível carregar o pedido."));

            return "redirect:/admin/pedidos";
        }
    }

    /*
     * ==========================================================
     * ATUALIZAÇÃO DE STATUS (ADMIN)
     * ==========================================================
     *
     * Endpoint utilizado pelo formulário da tela de detalhes.
     *
     * Recebe o novo status via parâmetro de formulário,
     * delega ao service (que valida a transição) e redireciona
     * de volta para a tela de detalhes com mensagem flash.
     *
     * Padrão PRG (Post/Redirect/Get) para evitar reenvio ao
     * atualizar a página.
     * ==========================================================
     */

    @PostMapping("/admin/pedidos/{pedidoId}/status")
    public String atualizarStatusPedido(
            @PathVariable Long pedidoId,
            @RequestParam StatusPedido novoStatus,
            RedirectAttributes redirectAttributes) {

        try {
            pedidoService.atualizarStatus(pedidoId, novoStatus);

            redirectAttributes.addFlashAttribute("mensagemSucesso",
                    "Status atualizado para '" + novoStatus.getDescricao() + "' com sucesso.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    obterMensagemErro(e, "Não foi possível atualizar o status do pedido."));
        }

        return "redirect:/admin/pedidos/" + pedidoId;
    }

    /*
     * ==========================================================
     * ENDPOINTS JSON (API)
     * ==========================================================
     */

    @ResponseBody
    @GetMapping("/api/pedidos/{pedidoId}/detalhes")
    public ResponseEntity<?>
    detalhesPedido(@PathVariable Long pedidoId, HttpSession session) {
        try {
            Long clienteId = obterLongSessao(session, SESSAO_CLIENTE);

            if (clienteId == null) {
                throw new IllegalArgumentException("Nenhum cliente está selecionado.");
            }

            Pedido pedido = pedidoService.buscarPorIdECliente(pedidoId, clienteId);

            return ResponseEntity.ok(montarDetalhes(pedido));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("sucesso", false, "erro", obterMensagemErro(e, "Não foi possível localizar o pedido.")));
        }
    }

    @ResponseBody
    @PostMapping("/api/pedidos/finalizar")
    public ResponseEntity<?>
    finalizarCompra(@RequestBody Map<String, Object> dados) {
        try {
            Long clienteId = obterLong(dados, "clienteId");
            Long enderecoEntregaId = obterLong(dados, "enderecoEntregaId");
            Long pagamentoId = obterLong(dados, "pagamentoId");

            BigDecimal valorFrete = obterBigDecimalOpcional(dados, "valorFrete", BigDecimal.ZERO);
            BigDecimal descontoPromocional = obterBigDecimalOpcional(dados, "descontoPromocional", BigDecimal.ZERO);

            String codigoCupomPromocional = obterStringOpcional(dados, "codigoCupomPromocional");

            Pedido pedido = pedidoService.finalizarCompra(
                    clienteId,
                    enderecoEntregaId,
                    pagamentoId,
                    valorFrete,
                    descontoPromocional,
                    codigoCupomPromocional);

            return ResponseEntity.ok(montarResumo(pedido));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("sucesso", false, "erro", obterMensagemErro(e, "Não foi possível finalizar a compra.")));
        }
    }

    @ResponseBody
    @GetMapping("/api/pedidos")
    public ResponseEntity<List<Pedido>>
    listar(@RequestParam(required = false) StatusPedido status) {
        return ResponseEntity.ok(pedidoService.listarPorStatus(status));
    }

    @ResponseBody
    @GetMapping("/api/pedidos/{id}")
    public ResponseEntity<?>
    buscarPorId(@PathVariable Long id) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id);
            return ResponseEntity.ok(montarDetalhes(pedido));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("sucesso", false, "erro", obterMensagemErro(e, "Não foi possível localizar o pedido.")));
        }
    }

    @ResponseBody
    @GetMapping("/api/pedidos/cliente/{clienteId}")
    public ResponseEntity<?>
    listarPorCliente(@PathVariable Long clienteId) {
        try {
            List<Pedido> pedidos = pedidoService.listarPorCliente(clienteId);
            List<Map<String, Object>> resposta = pedidos.stream().map(this::montarResumo).toList();

            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("sucesso", false, "erro", obterMensagemErro(e, "Não foi possível listar os pedidos do cliente.")));
        }
    }

    /*
     * ==========================================================
     * HELPERS DE SERIALIZAÇÃO
     * ==========================================================
     */

    private Map<String, Object> montarResumo(Pedido pedido) {
        Map<String, Object> resposta = new HashMap<>();

        resposta.put("sucesso", true);
        resposta.put("pedidoId", pedido.getId());
        resposta.put("status", pedido.getStatus());
        resposta.put("dataCriacao", pedido.getDataCriacao());
        resposta.put("subtotal", pedido.getSubtotal());
        resposta.put("descontoPromocional", pedido.getDescontoPromocional());
        resposta.put("valorFrete", pedido.getValorFrete());
        resposta.put("valorTotal", pedido.getValorTotal());
        resposta.put("quantidadeItens", pedido.getQuantidadeTotalItens());

        return resposta;
    }

    private Map<String, Object> montarDetalhes(Pedido pedido) {
        Map<String, Object> resposta = montarResumo(pedido);

        if (pedido.getCliente() != null) {
            resposta.put("clienteId", pedido.getCliente().getId());
            resposta.put("clienteNome", pedido.getCliente().getNome());
        }

        if (pedido.getEnderecoEntrega() != null) {
            Endereco endereco = pedido.getEnderecoEntrega();

            resposta.put("enderecoEntregaId", endereco.getId());
            resposta.put("tipoLogradouro", endereco.getTipoLogradouro());
            resposta.put("logradouro", endereco.getLogradouro());
            resposta.put("numero", endereco.getNumero());
            resposta.put("complemento", endereco.getComplemento());
            resposta.put("bairro", endereco.getBairro());
            resposta.put("cidade", endereco.getCidade());
            resposta.put("uf", endereco.getUf());
            resposta.put("cep", endereco.getCep());
            resposta.put("pais", endereco.getPais());
            resposta.put("apelidoEndereco", endereco.getApelido());
        }

        if (pedido.getPagamento() != null) {
            resposta.put("pagamentoId", pedido.getPagamento().getId());
            resposta.put("statusPagamento", pedido.getPagamento().getStatus());
            resposta.put("formaPagamento", pedido.getPagamento().getFormaPagamento());
            resposta.put("valorOriginalPagamento", pedido.getPagamento().getValorOriginal());
            resposta.put("valorDescontoPagamento", pedido.getPagamento().getValorDesconto());
            resposta.put("valorPago", pedido.getPagamento().getValorPago());
        }

        resposta.put("codigoCupomPromocional", pedido.getCodigoCupomPromocional());
        resposta.put("itens", pedido
                .getItens().stream()
                .map(item -> {Map<String, Object> itemResposta = new HashMap<>();
                    itemResposta.put("id", item.getId());
                    itemResposta.put("quantidade", item.getQuantidade());
                    itemResposta.put("precoUnitario", item.getPrecoUnitario());
                    itemResposta.put("precoTotal", item.getPrecoTotal());

                    if (item.getProduto() != null) {
                        itemResposta.put("produtoId", item.getProduto().getId());
                        itemResposta.put("titulo", item.getProduto().getTitulo());
                        itemResposta.put("autor", item.getProduto().getAutor());
                        itemResposta.put("isbn", item.getProduto().getIsbn());

                        itemResposta.put("urlCapa", item.getProduto().getUrlCapa());
                    }
                    return itemResposta;
                }).toList());
        return resposta;
    }

    /*
     * ==========================================================
     * HELPERS DE PAGINAÇÃO
     * ==========================================================
     */

    private Pageable montarPageable(int page, int size) {
        int paginaNormalizada = Math.max(page, 0);
        int tamanhoNormalizado = (size <= 0 || size > TAMANHO_PAGINA_MAXIMO)
                ? TAMANHO_PAGINA_PADRAO
                : size;

        return PageRequest.of(
                paginaNormalizada,
                tamanhoNormalizado,
                Sort.by(Sort.Direction.DESC, "dataCriacao"));
    }

    /*
     * ==========================================================
     * HELPERS DE SESSÃO / VALIDAÇÃO / CONVERSÃO
     * ==========================================================
     */

    private Long obterClienteIdAtual(Long clienteId, HttpSession session) {
        if (clienteId != null) {
            if (clienteId <= 0) {
                throw new IllegalArgumentException("Cliente inválido.");
            }

            session.setAttribute(SESSAO_CLIENTE, clienteId);
            return clienteId;
        }
        return obterLongSessao(session, SESSAO_CLIENTE);
    }

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

    private Long obterLong(Map<String, Object> dados, String campo) {
        Object valor = dados.get(campo);

        if (valor == null) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }
        try {
            Long resultado = Long.valueOf(valor.toString());

            if (resultado <= 0) {
                throw new IllegalArgumentException(campo + " deve ser maior que zero.");
            }
            return resultado;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(campo + " possui um valor inválido."
            );
        }
    }

    private BigDecimal obterBigDecimalOpcional(Map<String, Object> dados, String campo, BigDecimal valorPadrao) {
        Object valor = dados.get(campo);
        if (valor == null || valor.toString().isBlank()) {
            return valorPadrao;
        }
        try {
            String texto = valor.toString().trim().replace(",", ".");
            BigDecimal resultado = new BigDecimal(texto);

            if (resultado.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(campo + " não pode ser negativo.");
            }

            return resultado;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(campo + " possui um valor inválido.");
        }
    }

    private String obterStringOpcional(Map<String, Object> dados, String campo) {
        Object valor = dados.get(campo);

        if (valor == null) {
            return null;
        }

        String texto = valor.toString().trim();

        if (texto.isEmpty()) {
            return null;
        }

        return texto;
    }

    private String obterMensagemErro(Exception e, String mensagemPadrao) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return mensagemPadrao;
        }

        return e.getMessage();
    }
}