package br.com.java.nexus.controller;

import br.com.java.nexus.model.carrinho.Carrinho;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.cliente.enums.TipoLogradouro;
import br.com.java.nexus.model.cliente.enums.UF;
import br.com.java.nexus.model.venda.Cupom;

import br.com.java.nexus.repository.cliente.EnderecoRepository;

import br.com.java.nexus.service.carrinho.CarrinhoService;
import br.com.java.nexus.service.carrinho.FreteService;
import br.com.java.nexus.service.exception.ValidacaoException;
import br.com.java.nexus.service.venda.CupomService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Controller
@RequestMapping("/carrinho")
public class CarrinhoController {

    private static final String SESSION_CLIENTE_ATUAL = "clienteAtual";
    private static final String SESSION_ENDERECO_ENTREGA = "enderecoEntregaAtual";
    private static final String SESSION_VALOR_FRETE = "valorFreteAtual";
    private static final String SESSION_CUPOM_PROMOCIONAL = "cupomPromocionalAtual";
    private static final String SESSION_CUPONS_TROCA = "cuponsTrocaAtuais";
    private static final String SESSION_DESCONTO_PROMOCIONAL = "descontoPromocionalAtual";
    private static final String SESSION_VALOR_CUPONS_TROCA = "valorCuponsTrocaAtual";
    private static final String SESSION_VALOR_TOTAL = "valorTotalAtual";

    private final CarrinhoService carrinhoService;
    private final EnderecoRepository enderecoRepository;
    private final FreteService freteService;
    private final CupomService cupomService;

    public CarrinhoController(CarrinhoService carrinhoService, EnderecoRepository enderecoRepository, FreteService freteService, CupomService cupomService) {
        this.carrinhoService = carrinhoService;
        this.enderecoRepository = enderecoRepository;
        this.freteService = freteService;
        this.cupomService = cupomService;
    }

    @ModelAttribute
    public void adicionarOpcoesEnderecoAoModel(Model model) {
        model.addAttribute("tiposLogradouro", TipoLogradouro.values());
        model.addAttribute("ufs", UF.values());
    }

    @GetMapping
    public String visualizar(
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request, Model model) {
        try {
            Long idCliente = obterClienteId(clienteId, request);

            Carrinho carrinho = carrinhoService.obterCarrinhoDoCliente(idCliente);
            Carrinho carrinhoComItens = carrinhoService.buscarComItens(idCliente);

            if (carrinhoComItens != null) {
                carrinho = carrinhoComItens;
            }

            List<Endereco> enderecosEntrega = enderecoRepository.findByClienteIdAndEnderecoEntregaTrue(idCliente);
            List<Cupom> cuponsDisponiveis = cupomService.buscarCuponsValidosParaCliente(idCliente);
            limparCuponsInvalidosDaSessao(idCliente, request);

            Map<String, Object> resumoFinanceiro = calcularResumoFinanceiro(idCliente, carrinho, obterFreteDaSessao(request), request);

            model.addAttribute("carrinho", carrinho);
            model.addAttribute("cliente", carrinho.getCliente());
            model.addAttribute("enderecosEntrega", enderecosEntrega);
            model.addAttribute("cuponsDisponiveis", cuponsDisponiveis);
            model.addAttribute("cupomPromocionalSelecionadoId", obterCupomPromocionalIdDaSessao(request));
            model.addAttribute("cuponsTrocaSelecionadosIds", obterCuponsTrocaIdsDaSessao(request));
            adicionarResumoFinanceiroAoModel(model, resumoFinanceiro);
            model.addAttribute("enderecoEntregaAtual", obterLongDaSessao(request, SESSION_ENDERECO_ENTREGA));
        } catch (Exception e) {
            model.addAttribute("erro", obterMensagemErro(e));
        }
        return "cliente/carrinho";
    }

    @PostMapping("/adicionar")
    public String adicionar(
            @RequestParam Long produtoId,
            @RequestParam(defaultValue = "1") Integer quantidade,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        try {
            Long idCliente = obterClienteId(clienteId, request);
            carrinhoService.adicionarItem(idCliente, produtoId, quantidade);
            invalidarFreteETotal(request);
            redirectAttributes.addFlashAttribute("sucesso", "Livro adicionado ao carrinho.");
            return "redirect:/carrinho?clienteId=" + idCliente;

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", obterMensagemErro(e));
            return "redirect:/home.html";
        }
    }

    @PostMapping("/alterar-quantidade")
    public String alterarQuantidade(
            @RequestParam Long produtoId,
            @RequestParam Integer quantidade,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        Long idCliente = null;

        try {
            idCliente = obterClienteId(clienteId, request);
            carrinhoService.alterarQuantidadeItem(idCliente, produtoId, quantidade);
            invalidarFreteETotal(request);
            redirectAttributes.addFlashAttribute("sucesso", "Quantidade atualizada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", obterMensagemErro(e));
        }
        if (idCliente != null) {
            return "redirect:/carrinho?clienteId=" + idCliente;
        }
        return "redirect:/carrinho";
    }

    @PostMapping("/remover")
    public String remover(
            @RequestParam Long produtoId,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        Long idCliente = null;
        try {
            idCliente = obterClienteId(clienteId, request);
            Carrinho carrinho = carrinhoService.removerItem(idCliente, produtoId);
            invalidarFreteETotal(request);

            if (carrinho == null || carrinho.isVazio()) {
                limparCuponsDaSessao(request);
            }

            redirectAttributes.addFlashAttribute("sucesso", "Livro removido do carrinho.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro", obterMensagemErro(e));
        }


        if (idCliente != null) {

            return "redirect:/carrinho?clienteId="
                    + idCliente;
        }


        return "redirect:/carrinho";
    }


    /*
     * ==========================================================
     * ALTERAR QUANTIDADE - AJAX
     * RF0032 / RN0031
     * ==========================================================
     */

    @PostMapping("/api/alterar-quantidade")
    @ResponseBody
    public ResponseEntity<?> alterarQuantidadeAjax(
            @RequestParam Long produtoId,
            @RequestParam Integer quantidade,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            Carrinho carrinho =
                    carrinhoService
                            .alterarQuantidadeItem(
                                    idCliente,
                                    produtoId,
                                    quantidade
                            );


            /*
             * Alterar quantidade muda:
             *
             * - subtotal;
             * - peso;
             * - frete;
             * - desconto efetivamente aplicável;
             * - total.
             */
            invalidarFreteETotal(
                    request
            );


            Map<String, Object> resposta =
                    montarResumoCarrinho(
                            carrinho
                    );


            resposta.putAll(
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            BigDecimal.ZERO,
                            request
                    )
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }


    /*
     * ==========================================================
     * REMOVER ITEM - AJAX
     * RF0032
     * ==========================================================
     */

    @PostMapping("/api/remover")
    @ResponseBody
    public ResponseEntity<?> removerAjax(
            @RequestParam Long produtoId,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            Carrinho carrinho =
                    carrinhoService
                            .removerItem(
                                    idCliente,
                                    produtoId
                            );


            invalidarFreteETotal(
                    request
            );


            if (carrinho == null
                    || carrinho.isVazio()) {

                limparCuponsDaSessao(
                        request
                );
            }


            Map<String, Object> resposta =
                    montarResumoCarrinho(
                            carrinho
                    );


            resposta.putAll(
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            BigDecimal.ZERO,
                            request
                    )
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }


    /*
     * ==========================================================
     * RESUMO DO CARRINHO - AJAX
     * ==========================================================
     */

    @GetMapping("/api/resumo")
    @ResponseBody
    public ResponseEntity<?> obterResumo(
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            Carrinho carrinho =
                    obterCarrinhoCarregado(
                            idCliente
                    );


            limparCuponsInvalidosDaSessao(
                    idCliente,
                    request
            );


            Map<String, Object> resposta =
                    montarResumoCarrinho(
                            carrinho
                    );


            resposta.putAll(
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            obterFreteDaSessao(request),
                            request
                    )
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }


    /*
     * ==========================================================
     * LISTAR CUPONS - AJAX
     * ==========================================================
     *
     * Utilizado pelo botão "Ver Cupons".
     *
     * Não existe digitação de código.
     *
     * A lista é obtida diretamente do banco através do
     * CupomService.
     * ==========================================================
     */

    @GetMapping("/api/cupons")
    @ResponseBody
    public ResponseEntity<?> listarCupons(
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            limparCuponsInvalidosDaSessao(
                    idCliente,
                    request
            );


            List<Cupom> cupons =
                    cupomService
                            .buscarCuponsValidosParaCliente(
                                    idCliente
                            );


            Long promocionalSelecionadoId =
                    obterCupomPromocionalIdDaSessao(
                            request
                    );


            List<Long> trocasSelecionadas =
                    obterCuponsTrocaIdsDaSessao(
                            request
                    );


            List<Map<String, Object>> itens =
                    new ArrayList<>();


            for (Cupom cupom : cupons) {

                Map<String, Object> item =
                        new HashMap<>();


                item.put(
                        "id",
                        cupom.getId()
                );


                item.put(
                        "codigo",
                        cupom.getCodigo()
                );


                item.put(
                        "tipo",
                        cupom.getTipo()
                );


                item.put(
                        "valor",
                        normalizarValor(
                                cupom.getValor()
                        )
                );


                item.put(
                        "descricao",
                        cupom.getDescricao()
                );


                boolean selecionado =
                        cupom.isPromocional()
                                ? cupom.getId()
                                .equals(
                                        promocionalSelecionadoId
                                )
                                : trocasSelecionadas
                                .contains(
                                        cupom.getId()
                                );


                item.put(
                        "selecionado",
                        selecionado
                );


                itens.add(
                        item
                );
            }


            Map<String, Object> resposta =
                    new HashMap<>();


            resposta.put(
                    "cupons",
                    itens
            );


            resposta.put(
                    "cupomPromocionalSelecionadoId",
                    promocionalSelecionadoId
            );


            resposta.put(
                    "cuponsTrocaSelecionadosIds",
                    trocasSelecionadas
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }


    /*
     * ==========================================================
     * SELECIONAR CUPOM - AJAX
     * RN0033 / RN0035 / RN0036
     * ==========================================================
     *
     * O cupom é selecionado pelo ID.
     *
     * Não existe entrada manual de código.
     *
     * IMPORTANTE:
     *
     * selecionar != consumir.
     *
     * O consumo somente deverá ocorrer quando a compra for
     * efetivamente finalizada.
     * ==========================================================
     */

    @PostMapping("/api/cupons/selecionar")
    @ResponseBody
    public ResponseEntity<?> selecionarCupom(
            @RequestParam Long cupomId,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            Carrinho carrinho =
                    carrinhoService
                            .validarParaCheckout(
                                    idCliente
                            );


            Cupom cupom =
                    validarCupomSelecionado(
                            cupomId,
                            idCliente
                    );


            /*
             * RN0033
             *
             * No máximo um cupom promocional.
             *
             * Ao selecionar outro promocional, ele substitui
             * automaticamente o anterior.
             */
            if (cupom.isPromocional()) {

                request
                        .getSession()
                        .setAttribute(
                                SESSION_CUPOM_PROMOCIONAL,
                                cupom.getId()
                        );


            } else if (cupom.isTroca()) {

                /*
                 * Cupons de troca podem ser múltiplos.
                 */
                List<Long> selecionados =
                        obterCuponsTrocaIdsDaSessao(
                                request
                        );


                Set<Long> idsUnicos =
                        new LinkedHashSet<>(
                                selecionados
                        );


                idsUnicos.add(
                        cupom.getId()
                );


                salvarCuponsTrocaNaSessao(
                        request,
                        new ArrayList<>(
                                idsUnicos
                        )
                );


            } else {

                throw new ValidacaoException(
                        "Tipo de cupom não suportado para esta compra."
                );
            }


            Map<String, Object> resposta =
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            obterFreteDaSessao(request),
                            request
                    );


            resposta.put(
                    "mensagem",
                    "Cupom aplicado ao carrinho."
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }


    /*
     * ==========================================================
     * REMOVER CUPOM - AJAX
     * ==========================================================
     */

    @PostMapping("/api/cupons/remover")
    @ResponseBody
    public ResponseEntity<?> removerCupom(
            @RequestParam Long cupomId,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            Carrinho carrinho =
                    carrinhoService
                            .validarParaCheckout(
                                    idCliente
                            );


            Long promocionalAtual =
                    obterCupomPromocionalIdDaSessao(
                            request
                    );


            if (cupomId.equals(
                    promocionalAtual
            )) {

                request
                        .getSession()
                        .removeAttribute(
                                SESSION_CUPOM_PROMOCIONAL
                        );
            }


            List<Long> cuponsTroca =
                    obterCuponsTrocaIdsDaSessao(
                            request
                    );


            cuponsTroca.removeIf(
                    id ->
                            cupomId.equals(id)
            );


            salvarCuponsTrocaNaSessao(
                    request,
                    cuponsTroca
            );


            Map<String, Object> resposta =
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            obterFreteDaSessao(request),
                            request
                    );


            resposta.put(
                    "mensagem",
                    "Cupom removido do carrinho."
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }

    @PostMapping("/api/enderecos")
    @ResponseBody
    public ResponseEntity<?> cadastrarEnderecoEntrega(
            @RequestParam(required = false) Long clienteId,
            @RequestParam TipoLogradouro tipoLogradouro,
            @RequestParam String logradouro,
            @RequestParam String numero,
            @RequestParam(required = false) String complemento,
            @RequestParam String bairro,
            @RequestParam String cidade,
            @RequestParam UF uf,
            @RequestParam String cep,
            @RequestParam(defaultValue = "Brasil") String pais,
            @RequestParam(required = false) String observacoes,
            @RequestParam(required = false) String apelido,
            HttpServletRequest request) {

        try {
            Long idCliente = obterClienteId(clienteId, request);

            Endereco endereco = new Endereco();

            endereco.setTipoLogradouro(tipoLogradouro);
            endereco.setLogradouro(logradouro);
            endereco.setNumero(numero);
            endereco.setComplemento(complemento);
            endereco.setBairro(bairro);
            endereco.setCidade(cidade);
            endereco.setUf(uf);
            endereco.setCep(cep);
            endereco.setPais(pais);
            endereco.setObservacoes(observacoes);
            endereco.setApelido(apelido);

            Endereco enderecoSalvo =
                    carrinhoService.cadastrarEnderecoEntrega(
                            idCliente,
                            endereco
                    );

            Map<String, Object> resposta = new HashMap<>();

            resposta.put(
                    "mensagem",
                    "Endereço de entrega cadastrado com sucesso."
            );

            resposta.put("id", enderecoSalvo.getId());
            resposta.put(
                    "tipoLogradouro",
                    enderecoSalvo.getTipoLogradouro().name()
            );
            resposta.put("logradouro", enderecoSalvo.getLogradouro());
            resposta.put("numero", enderecoSalvo.getNumero());
            resposta.put("complemento", enderecoSalvo.getComplemento());
            resposta.put("bairro", enderecoSalvo.getBairro());
            resposta.put("cidade", enderecoSalvo.getCidade());
            resposta.put(
                    "uf",
                    enderecoSalvo.getUf().name()
            );
            resposta.put("cep", enderecoSalvo.getCep());
            resposta.put("pais", enderecoSalvo.getPais());
            resposta.put("observacoes", enderecoSalvo.getObservacoes());
            resposta.put("apelido", enderecoSalvo.getApelido());
            resposta.put(
                    "enderecoEntrega",
                    enderecoSalvo.isEnderecoEntrega()
            );

            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            return respostaErro(e);
        }
    }

    @PostMapping("/api/frete")
    @ResponseBody
    public ResponseEntity<?> calcularFrete(
            @RequestParam Long enderecoId,
            @RequestParam(required = false) Long clienteId,
            HttpServletRequest request) {

        try {

            Long idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            /*
             * Confirma:
             *
             * - existência;
             * - pertencimento ao cliente;
             * - utilização como endereço de entrega.
             */
            Endereco endereco =
                    obterEnderecoEntregaDoCliente(
                            idCliente,
                            enderecoId
                    );


            Carrinho carrinho =
                    obterCarrinhoCarregado(
                            idCliente
                    );


            /*
             * Frete calculado exclusivamente pelo FreteService.
             */
            BigDecimal valorFrete =
                    freteService.calcularFrete(
                            carrinho,
                            endereco
                    );


            /*
             * Mantém o contexto temporário da entrega.
             */
            request
                    .getSession()
                    .setAttribute(
                            SESSION_ENDERECO_ENTREGA,
                            endereco.getId()
                    );


            request
                    .getSession()
                    .setAttribute(
                            SESSION_VALOR_FRETE,
                            valorFrete
                    );


            /*
             * O resumo agora considera:
             *
             * subtotal
             * - promocional
             * + frete
             * - cupons de troca
             */
            Map<String, Object> resposta =
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            valorFrete,
                            request
                    );


            resposta.put(
                    "enderecoId",
                    endereco.getId()
            );


            resposta.put(
                    "frete",
                    valorFrete
            );


            return ResponseEntity.ok(
                    resposta
            );


        } catch (Exception e) {

            return respostaErro(
                    e
            );
        }
    }


    /*
     * ==========================================================
     * INICIAR COMPRA
     * RF0033 / RF0034 / RF0035
     * RN0033 / RN0035 / RN0036
     * ==========================================================
     *
     * O carrinho:
     *
     * - valida itens;
     * - valida endereço;
     * - recalcula frete;
     * - revalida cupons;
     * - calcula o valor final;
     * - mantém o contexto financeiro na sessão;
     * - encaminha para pagamento.
     *
     * O pagamento não selecionará cupons novamente.
     * ==========================================================
     */

    @PostMapping("/finalizar")
    public String iniciarCompra(
            @RequestParam(required = false) Long clienteId,
            @RequestParam Long enderecoId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        Long idCliente = null;


        try {

            idCliente =
                    obterClienteId(
                            clienteId,
                            request
                    );


            /*
             * Revalida produtos e estoque.
             */
            Carrinho carrinho =
                    carrinhoService
                            .validarParaCheckout(
                                    idCliente
                            );


            /*
             * Revalida endereço.
             */
            Endereco endereco =
                    obterEnderecoEntregaDoCliente(
                            idCliente,
                            enderecoId
                    );


            /*
             * Recalcula o frete no backend.
             */
            BigDecimal valorFrete =
                    freteService.calcularFrete(
                            carrinho,
                            endereco
                    );


            /*
             * Revalida todos os cupons selecionados.
             */
            limparCuponsInvalidosDaSessao(
                    idCliente,
                    request
            );


            /*
             * Recalcula o resultado financeiro definitivo
             * desta etapa.
             */
            Map<String, Object> resumo =
                    calcularResumoFinanceiro(
                            idCliente,
                            carrinho,
                            valorFrete,
                            request
                    );


            /*
             * Salva endereço e frete efetivamente validados.
             */
            request
                    .getSession()
                    .setAttribute(
                            SESSION_ENDERECO_ENTREGA,
                            endereco.getId()
                    );


            request
                    .getSession()
                    .setAttribute(
                            SESSION_VALOR_FRETE,
                            valorFrete
                    );


            /*
             * O próprio calcularResumoFinanceiro já mantém
             * na sessão:
             *
             * - desconto promocional;
             * - valor de troca efetivamente aplicado;
             * - valor total.
             *
             * Esses dados serão utilizados na etapa de pagamento.
             */
            request
                    .getSession()
                    .setAttribute(
                            SESSION_DESCONTO_PROMOCIONAL,
                            resumo.get(
                                    "descontoPromocional"
                            )
                    );


            request
                    .getSession()
                    .setAttribute(
                            SESSION_VALOR_CUPONS_TROCA,
                            resumo.get(
                                    "valorCuponsTrocaAplicado"
                            )
                    );


            request
                    .getSession()
                    .setAttribute(
                            SESSION_VALOR_TOTAL,
                            resumo.get(
                                    "total"
                            )
                    );


            /*
             * Não existe /checkout.
             *
             * A próxima etapa é pagamento.
             */
            return "redirect:/pagamento?clienteId="
                    + idCliente;


        } catch (Exception e) {

            redirectAttributes.addFlashAttribute(
                    "erro",
                    obterMensagemErro(e)
            );


            if (idCliente != null) {

                return "redirect:/carrinho?clienteId="
                        + idCliente;
            }


            return "redirect:/carrinho";
        }
    }


    /*
     * ==========================================================
     * RESUMO FINANCEIRO DO CARRINHO
     * ==========================================================
     *
     * Ordem:
     *
     * 1. subtotal
     * 2. cupom promocional
     * 3. frete
     * 4. cupons de troca
     * 5. total final
     *
     * Nenhum cupom é consumido aqui.
     * ==========================================================
     */

    private Map<String, Object> calcularResumoFinanceiro(
            Long clienteId,
            Carrinho carrinho,
            BigDecimal valorFrete,
            HttpServletRequest request) {

        BigDecimal subtotal =
                carrinho == null
                        ? BigDecimal.ZERO
                        : normalizarValor(
                        carrinho.getSubtotal()
                );


        BigDecimal frete =
                normalizarValor(
                        valorFrete
                );


        /*
         * ======================================================
         * CUPOM PROMOCIONAL
         * ======================================================
         */

        Cupom cupomPromocional =
                obterCupomPromocionalSelecionado(
                        clienteId,
                        request
                );


        BigDecimal descontoPromocional =
                BigDecimal.ZERO;


        if (cupomPromocional != null) {

            descontoPromocional =
                    normalizarValor(
                            cupomPromocional.getValor()
                    );


            /*
             * Um desconto promocional não pode transformar
             * o subtotal dos produtos em valor negativo.
             */
            if (descontoPromocional.compareTo(
                    subtotal
            ) > 0) {

                descontoPromocional =
                        subtotal;
            }
        }


        BigDecimal subtotalComPromocional =
                subtotal
                        .subtract(
                                descontoPromocional
                        );


        if (subtotalComPromocional.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            subtotalComPromocional =
                    BigDecimal.ZERO;
        }


        /*
         * O frete entra antes dos créditos de troca.
         */
        BigDecimal totalAntesTroca =
                subtotalComPromocional
                        .add(
                                frete
                        );


        /*
         * ======================================================
         * CUPONS DE TROCA
         * ======================================================
         */

        List<Cupom> cuponsTrocaSelecionados =
                obterCuponsTrocaSelecionadosValidos(
                        clienteId,
                        request
                );


        /*
         * RN0035 / RN0036
         *
         * Considera primeiro os cupons de maior valor.
         */
        cuponsTrocaSelecionados.sort(
                Comparator.comparing(
                        Cupom::getValor,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                ).reversed()
        );


        List<Cupom> cuponsTrocaEfetivamenteAplicados =
                new ArrayList<>();


        BigDecimal somaCuponsTroca =
                BigDecimal.ZERO;


        /*
         * Apenas os cupons necessários entram no cálculo.
         *
         * Exemplo:
         *
         * total = 80
         * cupons = 50, 40, 20
         *
         * aplica 50 + 40 = 90
         *
         * o cupom de 20 não é necessário.
         */
        for (Cupom cupom :
                cuponsTrocaSelecionados) {

            if (somaCuponsTroca.compareTo(
                    totalAntesTroca
            ) >= 0) {

                break;
            }


            cuponsTrocaEfetivamenteAplicados.add(
                    cupom
            );


            somaCuponsTroca =
                    somaCuponsTroca.add(
                            normalizarValor(
                                    cupom.getValor()
                            )
                    );
        }


        BigDecimal valorCuponsTrocaAplicado =
                somaCuponsTroca.min(
                        totalAntesTroca
                );


        BigDecimal valorExcedenteTroca =
                somaCuponsTroca
                        .subtract(
                                totalAntesTroca
                        );


        if (valorExcedenteTroca.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            valorExcedenteTroca =
                    BigDecimal.ZERO;
        }


        BigDecimal total =
                totalAntesTroca
                        .subtract(
                                valorCuponsTrocaAplicado
                        );


        if (total.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            total =
                    BigDecimal.ZERO;
        }


        /*
         * ======================================================
         * RESPOSTA
         * ======================================================
         */

        Map<String, Object> resumo =
                new HashMap<>();


        resumo.put(
                "subtotal",
                subtotal
        );


        resumo.put(
                "frete",
                frete
        );


        resumo.put(
                "descontoPromocional",
                descontoPromocional
        );


        resumo.put(
                "subtotalComPromocional",
                subtotalComPromocional
        );


        resumo.put(
                "totalAntesCuponsTroca",
                totalAntesTroca
        );


        resumo.put(
                "valorCuponsTrocaAplicado",
                valorCuponsTrocaAplicado
        );


        resumo.put(
                "valorExcedenteTroca",
                valorExcedenteTroca
        );


        resumo.put(
                "total",
                total
        );


        /*
         * Informações do promocional.
         */
        if (cupomPromocional != null) {

            resumo.put(
                    "cupomPromocionalId",
                    cupomPromocional.getId()
            );


            resumo.put(
                    "cupomPromocionalCodigo",
                    cupomPromocional.getCodigo()
            );


        } else {

            resumo.put(
                    "cupomPromocionalId",
                    null
            );


            resumo.put(
                    "cupomPromocionalCodigo",
                    null
            );
        }

        List<Long> idsTrocaAplicados =
                cuponsTrocaEfetivamenteAplicados
                        .stream()
                        .map(
                                Cupom::getId
                        )
                        .toList();


        List<String> codigosTrocaAplicados =
                cuponsTrocaEfetivamenteAplicados
                        .stream()
                        .map(
                                Cupom::getCodigo
                        )
                        .toList();


        resumo.put(
                "cuponsTrocaAplicadosIds",
                idsTrocaAplicados
        );


        resumo.put(
                "cuponsTrocaAplicadosCodigos",
                codigosTrocaAplicados
        );


        resumo.put(
                "cuponsTrocaSelecionadosIds",
                obterCuponsTrocaIdsDaSessao(
                        request
                )
        );

        request
                .getSession()
                .setAttribute(
                        SESSION_DESCONTO_PROMOCIONAL,
                        descontoPromocional
                );


        request
                .getSession()
                .setAttribute(
                        SESSION_VALOR_CUPONS_TROCA,
                        valorCuponsTrocaAplicado
                );


        request
                .getSession()
                .setAttribute(
                        SESSION_VALOR_TOTAL,
                        total
                );


        return resumo;
    }

    private Cupom validarCupomSelecionado(
            Long cupomId,
            Long clienteId) {

        if (cupomId == null
                || cupomId <= 0) {

            throw new ValidacaoException(
                    "Cupom inválido."
            );
        }

        Cupom cupom = cupomService.buscarPorId(cupomId);
        return cupomService.validarCupom(cupom.getCodigo(), clienteId);
    }


    /*
     * ==========================================================
     * CUPOM PROMOCIONAL SELECIONADO
     * ==========================================================
     */

    private Cupom obterCupomPromocionalSelecionado(
            Long clienteId,
            HttpServletRequest request) {

        Long cupomId =
                obterCupomPromocionalIdDaSessao(
                        request
                );


        if (cupomId == null) {

            return null;
        }


        try {

            Cupom cupom =
                    validarCupomSelecionado(
                            cupomId,
                            clienteId
                    );


            if (!cupom.isPromocional()) {

                request
                        .getSession()
                        .removeAttribute(
                                SESSION_CUPOM_PROMOCIONAL
                        );


                return null;
            }


            return cupom;


        } catch (Exception e) {

            request
                    .getSession()
                    .removeAttribute(
                            SESSION_CUPOM_PROMOCIONAL
                    );


            return null;
        }
    }

    private List<Cupom> obterCuponsTrocaSelecionadosValidos(
            Long clienteId,
            HttpServletRequest request) {

        List<Long> ids =
                obterCuponsTrocaIdsDaSessao(
                        request
                );


        List<Cupom> validos =
                new ArrayList<>();


        List<Long> idsValidos =
                new ArrayList<>();


        for (Long id : ids) {

            try {

                Cupom cupom =
                        validarCupomSelecionado(
                                id,
                                clienteId
                        );


                if (!cupom.isTroca()) {

                    continue;
                }


                validos.add(
                        cupom
                );


                idsValidos.add(
                        cupom.getId()
                );


            } catch (Exception ignored) {
            }
        }
        salvarCuponsTrocaNaSessao(request, idsValidos);
        return validos;
    }
    private void limparCuponsInvalidosDaSessao(Long clienteId, HttpServletRequest request) {
        obterCupomPromocionalSelecionado(clienteId, request);
        obterCuponsTrocaSelecionadosValidos(clienteId, request);
    }


    private Long obterCupomPromocionalIdDaSessao(
            HttpServletRequest request) {

        return obterLongDaSessao(
                request,
                SESSION_CUPOM_PROMOCIONAL
        );
    }

    private List<Long> obterCuponsTrocaIdsDaSessao(
            HttpServletRequest request) {

        Object valor =
                request
                        .getSession()
                        .getAttribute(
                                SESSION_CUPONS_TROCA
                        );


        List<Long> ids =
                new ArrayList<>();


        if (!(valor instanceof List<?> lista)) {

            return ids;
        }


        for (Object item : lista) {

            if (item instanceof Long id) {

                ids.add(
                        id
                );


            } else if (item instanceof Number numero) {

                ids.add(
                        numero.longValue()
                );
            }
        }


        return ids;
    }

    private void salvarCuponsTrocaNaSessao(
            HttpServletRequest request,
            List<Long> ids) {

        Set<Long> unicos =
                new LinkedHashSet<>();


        if (ids != null) {

            for (Long id : ids) {

                if (id != null
                        && id > 0) {

                    unicos.add(
                            id
                    );
                }
            }
        }
        if (unicos.isEmpty()) {
            request.getSession().removeAttribute(SESSION_CUPONS_TROCA);
            return;
        }
        request.getSession().setAttribute(SESSION_CUPONS_TROCA, new ArrayList<>(unicos));
    }

    private void limparCuponsDaSessao(
            HttpServletRequest request) {

        HttpSession session =
                request.getSession();


        session.removeAttribute(
                SESSION_CUPOM_PROMOCIONAL
        );


        session.removeAttribute(
                SESSION_CUPONS_TROCA
        );


        session.removeAttribute(
                SESSION_DESCONTO_PROMOCIONAL
        );


        session.removeAttribute(
                SESSION_VALOR_CUPONS_TROCA
        );


        session.removeAttribute(
                SESSION_VALOR_TOTAL
        );
    }

    private void invalidarFreteETotal(
            HttpServletRequest request) {

        HttpSession session =
                request.getSession();


        session.removeAttribute(
                SESSION_ENDERECO_ENTREGA
        );


        session.removeAttribute(
                SESSION_VALOR_FRETE
        );


        session.removeAttribute(
                SESSION_VALOR_TOTAL
        );
    }

    private BigDecimal obterFreteDaSessao(
            HttpServletRequest request) {

        Object valor =
                request
                        .getSession()
                        .getAttribute(
                                SESSION_VALOR_FRETE
                        );


        if (valor instanceof BigDecimal bigDecimal) {

            return normalizarValor(
                    bigDecimal
            );
        }


        if (valor instanceof Number numero) {

            return normalizarValor(
                    BigDecimal.valueOf(
                            numero.doubleValue()
                    )
            );
        }


        return BigDecimal.ZERO;
    }

    private Carrinho obterCarrinhoCarregado(
            Long clienteId) {

        Carrinho carrinho =
                carrinhoService
                        .obterCarrinhoDoCliente(
                                clienteId
                        );


        Carrinho carrinhoComItens =
                carrinhoService
                        .buscarComItens(
                                clienteId
                        );


        if (carrinhoComItens != null) {

            return carrinhoComItens;
        }


        return carrinho;
    }

    private Endereco obterEnderecoEntregaDoCliente(
            Long clienteId,
            Long enderecoId) {

        if (clienteId == null) {

            throw new ValidacaoException(
                    "Cliente é obrigatório."
            );
        }


        if (enderecoId == null
                || enderecoId <= 0) {

            throw new ValidacaoException(
                    "Selecione um endereço de entrega."
            );
        }


        List<Endereco> enderecosEntrega =
                enderecoRepository
                        .findByClienteIdAndEnderecoEntregaTrue(
                                clienteId
                        );


        return enderecosEntrega
                .stream()
                .filter(
                        endereco ->
                                enderecoId.equals(
                                        endereco.getId()
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new ValidacaoException(
                                        "O endereço selecionado não pertence ao cliente "
                                                + "ou não é um endereço de entrega."
                                )
                );
    }

    private Map<String, Object> montarResumoCarrinho(
            Carrinho carrinho) {

        Map<String, Object> resumo =
                new HashMap<>();


        if (carrinho == null) {

            resumo.put(
                    "subtotal",
                    BigDecimal.ZERO
            );


            resumo.put(
                    "quantidadeItens",
                    0
            );


            resumo.put(
                    "vazio",
                    true
            );


            return resumo;
        }


        resumo.put(
                "subtotal",
                normalizarValor(
                        carrinho.getSubtotal()
                )
        );


        resumo.put(
                "quantidadeItens",
                carrinho.getQuantidadeItens()
        );


        resumo.put(
                "vazio",
                carrinho.isVazio()
        );


        return resumo;
    }

    private void adicionarResumoFinanceiroAoModel(Model model, Map<String, Object> resumo) {
        model.addAttribute("subtotal", resumo.get("subtotal"));
        model.addAttribute("valorFrete", resumo.get("frete"));
        model.addAttribute("descontoPromocional", resumo.get("descontoPromocional"));
        model.addAttribute("valorCuponsTrocaAplicado", resumo.get("valorCuponsTrocaAplicado"));
        model.addAttribute("valorExcedenteTroca", resumo.get("valorExcedenteTroca"));
        model.addAttribute("valorTotal", resumo.get("total"));
        model.addAttribute("cupomPromocionalCodigo", resumo.get("cupomPromocionalCodigo"));
        model.addAttribute("cuponsTrocaAplicadosIds", resumo.get("cuponsTrocaAplicadosIds"));
        model.addAttribute("cuponsTrocaAplicadosCodigos", resumo.get("cuponsTrocaAplicadosCodigos"));
    }

    private Long obterClienteId(Long clienteIdInformado, HttpServletRequest request) {
        if (clienteIdInformado != null) {
            if (clienteIdInformado <= 0) {
                throw new ValidacaoException("Cliente inválido.");
            }

            Object clienteAnterior = request.getSession().getAttribute(SESSION_CLIENTE_ATUAL);
            if (clienteAnterior instanceof Long idAnterior && !idAnterior.equals(clienteIdInformado)) {
                limparContextoCompra(request);
            }
            request.getSession().setAttribute(SESSION_CLIENTE_ATUAL, clienteIdInformado);
            return clienteIdInformado;
        }

        Object clienteSessao = request.getSession().getAttribute(SESSION_CLIENTE_ATUAL);
        if (clienteSessao instanceof Long id) {
            return id;
        }
        if (clienteSessao instanceof Number numero) {
            return numero.longValue();}

        throw new ValidacaoException("Nenhum cliente foi selecionado.");
    }

    private void limparContextoCompra(HttpServletRequest request) {

        HttpSession session = request.getSession();
        session.removeAttribute(SESSION_ENDERECO_ENTREGA);
        session.removeAttribute(SESSION_VALOR_FRETE);
        session.removeAttribute(SESSION_CUPOM_PROMOCIONAL);
        session.removeAttribute(SESSION_CUPONS_TROCA);
        session.removeAttribute(SESSION_DESCONTO_PROMOCIONAL);
        session.removeAttribute(SESSION_VALOR_CUPONS_TROCA);
        session.removeAttribute(SESSION_VALOR_TOTAL);
    }

    private Long obterLongDaSessao(HttpServletRequest request, String atributo) {
        Object valor = request.getSession().getAttribute(atributo);

        if (valor instanceof Long id) {
            return id;
        }
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        return null;
    }

    private BigDecimal normalizarValor(BigDecimal valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return valor.setScale(2, RoundingMode.HALF_UP
        );
    }

    private ResponseEntity<Map<String, String>> respostaErro(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("erro", obterMensagemErro(e)));
    }

    private String obterMensagemErro(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return "Não foi possível concluir a operação.";
        }
        return e.getMessage();
    }
}