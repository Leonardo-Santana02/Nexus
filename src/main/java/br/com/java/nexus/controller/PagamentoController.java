package br.com.java.nexus.controller;

import br.com.java.nexus.model.carrinho.Carrinho;
import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.pagamento.Pagamento;
import br.com.java.nexus.model.pagamento.enums.StatusPagamento;
import br.com.java.nexus.model.pedido.Pedido;

import br.com.java.nexus.repository.cliente.CartaoCreditoRepository;
import br.com.java.nexus.repository.cliente.EnderecoRepository;

import br.com.java.nexus.service.carrinho.CarrinhoService;
import br.com.java.nexus.service.cliente.ClienteService;
import br.com.java.nexus.service.pagamento.PagamentoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class PagamentoController {
    private final PagamentoService pagamentoService;
    private final CarrinhoService carrinhoService;
    private final ClienteService clienteService;
    private final EnderecoRepository enderecoRepository;
    private final CartaoCreditoRepository cartaoCreditoRepository;

    public PagamentoController(
            PagamentoService pagamentoService,
            CarrinhoService carrinhoService,
            ClienteService clienteService,
            EnderecoRepository enderecoRepository,
            CartaoCreditoRepository cartaoCreditoRepository) {

        this.pagamentoService = pagamentoService;
        this.carrinhoService = carrinhoService;
        this.clienteService = clienteService;
        this.enderecoRepository = enderecoRepository;
        this.cartaoCreditoRepository = cartaoCreditoRepository;
    }

    @GetMapping({"/pagamento", "/pagamento.html"})
    public String paginaPagamento(
            @RequestParam(required = false)
            Long clienteId,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            HttpSession session = request.getSession();
            Long idCliente = obterClienteId(clienteId, session);

            if (idCliente == null) {
                redirectAttributes.addFlashAttribute("erro", "Selecione um cliente antes de realizar o pagamento.");
                return "redirect:/home";
            }

            Cliente cliente = clienteService.buscarPorId(idCliente);
            Carrinho carrinho = carrinhoService.validarParaCheckout(idCliente);

            if (carrinho == null || carrinho.isVazio()) {
                redirectAttributes.addFlashAttribute("erro", "O carrinho está vazio.");

                return "redirect:/carrinho?clienteId=" + idCliente;
            }

            Long enderecoId = obterLongSessao(session, "enderecoEntregaAtual");

            if (enderecoId == null) {
                redirectAttributes.addFlashAttribute("erro", "Selecione um endereço de entrega antes de continuar para o pagamento.");

                return "redirect:/carrinho?clienteId=" + idCliente;
            }

            Endereco enderecoEntrega = buscarEnderecoEntrega(idCliente, enderecoId);
            BigDecimal valorFrete = obterBigDecimalSessao(session, "valorFreteAtual");

            if (valorFrete == null || valorFrete.compareTo(BigDecimal.ZERO) < 0) {
                redirectAttributes.addFlashAttribute("erro", "Calcule o frete antes de continuar para o pagamento.");

                return "redirect:/carrinho?clienteId=" + idCliente;
            }

            valorFrete = normalizar(valorFrete);

            BigDecimal descontoPromocional = obterBigDecimalSessao(session, "descontoPromocionalAtual");
            BigDecimal valorCuponsTroca = obterBigDecimalSessao(session, "valorCuponsTrocaAtual");
            BigDecimal valorTotal = obterBigDecimalSessao(session, "valorTotalAtual");

            if (descontoPromocional == null) {
                descontoPromocional = BigDecimal.ZERO;
            }

            if (valorCuponsTroca == null) {
                valorCuponsTroca = BigDecimal.ZERO;
            }

            if (valorTotal == null) {
                redirectAttributes.addFlashAttribute("erro", "O valor da compra não foi calculado pelo carrinho.");

                return "redirect:/carrinho?clienteId=" + idCliente;
            }

            descontoPromocional = normalizar(descontoPromocional);
            valorCuponsTroca = normalizar(valorCuponsTroca);
            valorTotal = normalizar(valorTotal);

            if (valorTotal.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("O valor final da compra não pode ser negativo.");
            }

            model.addAttribute("cliente", cliente);
            model.addAttribute("carrinho", carrinho);
            model.addAttribute("enderecoEntrega", enderecoEntrega);
            model.addAttribute("valorFrete", valorFrete);
            model.addAttribute("descontoPromocional", descontoPromocional);
            model.addAttribute("valorCuponsTroca", valorCuponsTroca);
            model.addAttribute("descontoTotal", normalizar(descontoPromocional.add(valorCuponsTroca)));
            model.addAttribute("valorTotal", valorTotal);
            model.addAttribute("cartoes", cartaoCreditoRepository.findByClienteIdOrderByIdAsc(idCliente));

            session.setAttribute("clienteAtual", idCliente);
            return "cliente/pagamento";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", obterMensagemErro(e));
            if (clienteId != null) {
                return "redirect:/carrinho?clienteId=" + clienteId;
            }

            return "redirect:/home";
        }
    }

    @PostMapping("/pagamento/finalizar")
    public String finalizarPagamento(
            @RequestParam Long clienteId,
            @RequestParam Long enderecoEntregaId,

            @RequestParam(required = false, name = "cartoesIds")
            List<Long> cartoesIds,

            @RequestParam(required = false, name = "cartoesValores")
            List<BigDecimal> cartoesValores,

            @RequestParam(required = false, name = "cartoesParcelas")
            List<Integer> cartoesParcelas,

            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        try {
            HttpSession session = request.getSession();
            Long clienteSessao = obterLongSessao(session, "clienteAtual");

            if (clienteSessao == null || !clienteSessao.equals(clienteId)) {
                throw new IllegalArgumentException("O cliente informado não corresponde " + "ao cliente atual da compra.");
            }

            Long enderecoSessao = obterLongSessao(session, "enderecoEntregaAtual");
            if (enderecoSessao == null || !enderecoSessao.equals(enderecoEntregaId)) {
                throw new IllegalArgumentException("O endereço de entrega informado não corresponde " + "ao endereço selecionado no carrinho.");
            }

            BigDecimal valorFrete = obterBigDecimalSessao(session, "valorFreteAtual");
            if (valorFrete == null) {
                throw new IllegalArgumentException("O valor do frete não foi calculado.");
            }

            Long cupomPromocionalId = obterLongSessao(session, "cupomPromocionalAtual");
            List<Long> cuponsTrocaIds = obterListaLongSessao(session, "cuponsTrocaAtuais");

            BigDecimal descontoPromocional = obterBigDecimalSessao(session, "descontoPromocionalAtual");
            BigDecimal valorCuponsTroca = obterBigDecimalSessao(session, "valorCuponsTrocaAtual");
            BigDecimal valorTotal = obterBigDecimalSessao(session, "valorTotalAtual");

            if (descontoPromocional == null) {
                descontoPromocional = BigDecimal.ZERO;
            }

            if (valorCuponsTroca == null) {
                valorCuponsTroca = BigDecimal.ZERO;
            }

            if (valorTotal == null) {
                throw new IllegalArgumentException("O valor final da compra não foi calculado.");
            }

            Pedido pedido = pagamentoService.finalizarCompra(clienteId, enderecoEntregaId, normalizar(valorFrete),
                    cupomPromocionalId, cuponsTrocaIds, normalizar(descontoPromocional),
                    normalizar(valorCuponsTroca), normalizar(valorTotal),
                    cartoesIds, cartoesValores, cartoesParcelas);


            limparContextoCheckout(session);

            redirectAttributes.addFlashAttribute("sucesso", "Pedido realizado com sucesso.");
            redirectAttributes.addAttribute("pedidoId", pedido.getId());
            return "redirect:/pedidos";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", obterMensagemErro(e));
            redirectAttributes.addAttribute("clienteId", clienteId);
            return "redirect:/pagamento";
        }
    }

    @ResponseBody
    @GetMapping("/api/pagamentos")
    public ResponseEntity<List<Pagamento>>
    listar(@RequestParam(required = false) StatusPagamento status) {
        return ResponseEntity.ok(pagamentoService.listarPorStatus(status));
    }

    @ResponseBody
    @GetMapping("/api/pagamentos/{id}")
    public ResponseEntity<Pagamento>
    buscar(@PathVariable Long id) {
        return ResponseEntity.ok(pagamentoService.buscarPorId(id));
    }

    @ResponseBody
    @PostMapping("/api/pagamentos/{id}/cartoes")
    public ResponseEntity<?>
    adicionarCartao(@PathVariable Long id, @RequestBody Map<String, Object> dados) {
        try {
            Long clienteId = obterLong(dados, "clienteId");
            Long cartaoId = obterLong(dados, "cartaoId");

            BigDecimal valor = obterBigDecimal(dados, "valor");
            Integer parcelas = obterInteger(dados, "parcelas", 1);

            Pagamento pagamento = pagamentoService.adicionarCartao(
                                    id, clienteId,
                                    cartaoId, valor, parcelas);

            return ResponseEntity.ok(montarResumo(pagamento));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                            Map.of("sucesso", false, "erro", obterMensagemErro(e)));
        }
    }

    @ResponseBody
    @PutMapping("/api/pagamentos/{id}/cartoes/{pagamentoCartaoId}")
    public ResponseEntity<?>
    alterarCartao(@PathVariable Long id, @PathVariable Long pagamentoCartaoId, @RequestBody Map<String, Object> dados) {
        try {
            BigDecimal valor = obterBigDecimal(dados, "valor");
            Integer parcelas = obterInteger(dados, "parcelas", 1);

            Pagamento pagamento = pagamentoService.alterarCartao(
                                    id, pagamentoCartaoId,
                                    valor, parcelas);

            return ResponseEntity.ok(montarResumo(pagamento));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                            Map.of("sucesso", false, "erro", obterMensagemErro(e)));
        }
    }

    @ResponseBody
    @DeleteMapping("/api/pagamentos/{id}/cartoes/{pagamentoCartaoId}")
    public ResponseEntity<?>
    removerCartao(@PathVariable Long id, @PathVariable Long pagamentoCartaoId) {
        try {
            Pagamento pagamento = pagamentoService.removerCartao(id, pagamentoCartaoId);
            return ResponseEntity.ok(montarResumo(pagamento));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                            Map.of("sucesso", false, "erro", obterMensagemErro(e)));
        }
    }

    @ResponseBody
    @PostMapping("/api/pagamentos/{id}/validar")
    public ResponseEntity<?>
    validar(@PathVariable Long id) {
        try {
            pagamentoService.validarPagamento(id);

            return ResponseEntity.ok(Map.of("valido", true));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                            Map.of("valido", false, "erro", obterMensagemErro(e)));
        }
    }

    @ResponseBody
    @PostMapping("/api/pagamentos/{id}/aprovar")
    public ResponseEntity<?> aprovar(@PathVariable Long id) {
        try {
            Pagamento pagamento = pagamentoService.aprovar(id);
            return ResponseEntity.ok(montarResumo(pagamento));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("sucesso", false, "erro", obterMensagemErro(e)));
        }
    }

    @ResponseBody
    @PostMapping("/api/pagamentos/{id}/rejeitar")
    public ResponseEntity<?>
    rejeitar(@PathVariable Long id, @RequestBody Map<String, Object> dados) {
        try {
            String motivo = dados.get("motivo") != null ? dados
                            .get("motivo").toString() : null;

            Pagamento pagamento = pagamentoService.rejeitar(id, motivo);
            return ResponseEntity.ok(montarResumo(pagamento));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("sucesso", false, "erro", obterMensagemErro(e)));
        }
    }


    @ResponseBody
    @PostMapping("/api/pagamentos/{id}/cancelar")
    public ResponseEntity<?> cancelar(@PathVariable Long id) {
        try {
            Pagamento pagamento = pagamentoService.cancelar(id);
            return ResponseEntity.ok(montarResumo(pagamento));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("sucesso", false, "erro", obterMensagemErro(e)));
        }
    }

    private Endereco buscarEnderecoEntrega(Long clienteId, Long enderecoId) {
        Endereco endereco = enderecoRepository.findById(enderecoId)
                        .orElseThrow(() -> new IllegalArgumentException("Endereço de entrega não encontrado."));

        if (endereco.getCliente() == null || endereco.getCliente().getId() == null || !clienteId.equals(endereco.getCliente().getId())) {
            throw new IllegalArgumentException("O endereço informado não pertence ao cliente.");
        }

        if (!endereco.isEnderecoEntrega()) {
            throw new IllegalArgumentException("O endereço informado não está habilitado para entrega.");
        }

        return endereco;
    }

    private Map<String, Object>
    montarResumo(Pagamento pagamento) {
        Map<String, Object> resposta = new HashMap<>();

        resposta.put("id", pagamento.getId());
        resposta.put("status", pagamento.getStatus());
        resposta.put("valorOriginal", pagamento.getValorOriginal());
        resposta.put("desconto", pagamento.getValorDesconto());
        resposta.put("valorAPagar", pagamento.getValorAPagar());
        resposta.put("totalCartoes", pagamento.getTotalCartoes());
        resposta.put("valorRestante", pagamento.getValorAPagar().subtract(pagamento.getTotalCartoes()));
        resposta.put("quantidadeCartoes", pagamento.getCartoesUsados().size());

        return resposta;
    }

    private void limparContextoCheckout(HttpSession session) {
        session.removeAttribute("enderecoEntregaAtual");
        session.removeAttribute("valorFreteAtual");
        session.removeAttribute("cupomPromocionalAtual");
        session.removeAttribute("cuponsTrocaAtuais");
        session.removeAttribute("descontoPromocionalAtual");
        session.removeAttribute("valorCuponsTrocaAtual");
        session.removeAttribute("valorTotalAtual");
    }

    private Long obterClienteId(Long clienteId, HttpSession session) {
        if (clienteId != null) {
            session.setAttribute("clienteAtual", clienteId);

            return clienteId;
        }

        return obterLongSessao(session, "clienteAtual");
    }

    private Long obterLongSessao(HttpSession session, String atributo) {
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

    private BigDecimal obterBigDecimalSessao(HttpSession session, String atributo) {
        Object valor = session.getAttribute(atributo);

        if (valor == null) {
            return null;
        }

        if (valor instanceof BigDecimal) {
            return (BigDecimal) valor;
        }

        try {
            return new BigDecimal(valor.toString());

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Long> obterListaLongSessao(HttpSession session, String atributo) {
        Object valor = session.getAttribute(atributo);

        if (!(valor instanceof List<?>)) {
            return List.of();
        }

        List<?> lista = (List<?>) valor;

        return lista.stream().filter(item -> item != null)
                .map(item -> {
                    if (item instanceof Number) {
                                return ((Number) item).longValue();
                            }
                            try {
                                return Long.valueOf(item.toString());
                            } catch (NumberFormatException e) {
                                return null;
                            }
                        }).filter(item -> item != null).distinct().toList();
    }

    private Long obterLong(Map<String, Object> dados, String campo) {
        Object valor = dados.get(campo);

        if (valor == null) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }

        return Long.valueOf(valor.toString());
    }


    private BigDecimal obterBigDecimal(Map<String, Object> dados, String campo) {
        Object valor = dados.get(campo);

        if (valor == null) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }

        return new BigDecimal(
                valor.toString()
        );
    }


    private Integer obterInteger(
            Map<String, Object> dados,
            String campo,
            Integer valorPadrao) {

        Object valor = dados.get(campo);

        if (valor == null) {
            return valorPadrao;
        }
        return Integer.valueOf(valor.toString());
    }

    private BigDecimal normalizar(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private String obterMensagemErro(Exception e) {
        if (e == null) {
            return "Não foi possível concluir a operação.";
        }

        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return "Não foi possível concluir a operação.";
        }

        return e.getMessage();
    }
}