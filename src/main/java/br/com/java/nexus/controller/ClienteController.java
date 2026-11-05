package br.com.java.nexus.controller;

import br.com.java.nexus.model.cliente.CartaoCredito;
import br.com.java.nexus.model.cliente.Cliente;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.cliente.Telefone;

import br.com.java.nexus.model.cliente.enums.BandeiraCartao;
import br.com.java.nexus.model.cliente.enums.Genero;
import br.com.java.nexus.model.cliente.enums.TipoLogradouro;
import br.com.java.nexus.model.cliente.enums.TipoTelefone;
import br.com.java.nexus.model.cliente.enums.UF;

import br.com.java.nexus.service.cliente.ClienteService;

import br.com.java.nexus.service.exception.ClienteNaoEncontradoException;
import br.com.java.nexus.service.exception.CpfJaCadastradoException;
import br.com.java.nexus.service.exception.EmailJaCadastradoException;
import br.com.java.nexus.service.exception.ValidacaoException;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/clientes")
public class ClienteController {
    private final ClienteService clienteService;
    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    /*
     * ==========================================================
     * OPÇÕES UTILIZADAS NOS FORMULÁRIOS
     * ==========================================================
     */

    @ModelAttribute
    public void adicionarOpcoesAoModel(Model model) {
        model.addAttribute("generos", Genero.values());
        model.addAttribute("tiposTelefone", TipoTelefone.values());
        model.addAttribute("tiposLogradouro", TipoLogradouro.values());
        model.addAttribute("ufs", UF.values());
        model.addAttribute("bandeiras", BandeiraCartao.values());
    }

    /*
     * ==========================================================
     * LISTAGEM E PESQUISA
     * ==========================================================
     */

    @GetMapping
    public String listar(
            @RequestParam(required = false)
            String tipoBusca,

            @RequestParam(required = false)
            String valorBusca,

            @RequestParam(required = false)
            Genero genero,

            @RequestParam(required = false)
            Boolean inativado,

            Model model) {

        Long id = null;

        String nome = null;
        String cpf = null;
        String email = null;
        String telefone = null;

        Genero generoFiltro = null;
        Boolean statusFiltro = null;


        if (tipoBusca != null && !tipoBusca.isBlank()) {
            switch (tipoBusca) {
                case "id" -> {
                    String valor = limparFiltro(valorBusca);
                    if (valor != null) {
                        try {
                            id = Long.valueOf(valor);
                        } catch (NumberFormatException e) {
                            model.addAttribute("erro", "Informe um ID válido.");
                        }
                    }
                }

                case "nome" -> nome = limparFiltro(valorBusca);
                case "cpf" -> cpf = limparFiltro(valorBusca);
                case "email" -> email = limparFiltro(valorBusca);
                case "telefone" -> telefone = limparFiltro(valorBusca);
                case "genero" -> generoFiltro = genero;
                case "status" -> statusFiltro = inativado;

                default -> {
                    // Nenhum filtro aplicado.
                }
            }
        }


        List<Cliente> clientes =
                clienteService.pesquisar(
                        id,
                        nome,
                        email,
                        cpf,
                        generoFiltro,
                        null,
                        telefone,
                        statusFiltro
                );

        model.addAttribute("clientes", clientes);
        model.addAttribute("tipoBusca", tipoBusca);
        model.addAttribute("valorBusca", valorBusca);
        model.addAttribute("filtroGenero", genero);
        model.addAttribute("filtroInativado", inativado);

        model.addAttribute("totalClientes", clienteService.listarTodos().size());
        model.addAttribute("totalAtivos", clienteService.listarAtivos().size());
        model.addAttribute("totalInativos", clienteService.listarInativos().size());

        return "admin/clientes/lista-cliente";
    }

    /*
     * ==========================================================
     * FORMULÁRIO DE NOVO CLIENTE
     * ==========================================================
     */

    @GetMapping("/novo")
    public String novo(Model model) {
        if (!model.containsAttribute("cliente")) {
            Cliente cliente = criarClienteParaFormulario();
            model.addAttribute("cliente", cliente);
        }

        return "admin/clientes/novo-cliente";
    }

    /*
     * ==========================================================
     * CADASTRAR CLIENTE
     * ==========================================================
     */

    @PostMapping("/novo")
    public String cadastrar(
            @Valid
            @ModelAttribute("cliente")

            Cliente cliente,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        //Erros do Bean Validation.
        if (bindingResult.hasErrors()) {
            model.addAttribute("erro", obterPrimeiraMensagemErro(bindingResult));

            return "admin/clientes/novo-cliente";
        }
        try {
            clienteService.cadastrar(cliente);
            redirectAttributes.addFlashAttribute("mensagem", "Cliente cadastrado com sucesso!");

            return "redirect:/admin/clientes";

        } catch (CpfJaCadastradoException | EmailJaCadastradoException | ValidacaoException e) {
            model.addAttribute("cliente", cliente);
            model.addAttribute("erro", e.getMessage());

            return "admin/clientes/novo-cliente";
        }
    }

    /*
     * ==========================================================
     * DETALHES DO CLIENTE
     * ==========================================================
     */

    @GetMapping("/{id}")
    public String detalhes(
            @PathVariable

            Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Cliente cliente = clienteService.buscarDetalhes(id);
            model.addAttribute("cliente", cliente);

            return "admin/clientes/detalhes-cliente";

        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());

            return "redirect:/admin/clientes";
        }
    }

    /*
     * ==========================================================
     * FORMULÁRIO DE EDIÇÃO
     * ==========================================================
     */

    @GetMapping("/{id}/editar")
    public String editar(
            @PathVariable

            Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            Cliente cliente = clienteService.buscarDetalhes(id);
            model.addAttribute("cliente", cliente);

            return "admin/clientes/editar-cliente";

        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());

            return "redirect:/admin/clientes";
        }
    }

    /*
     * ==========================================================
     * ATUALIZAR CLIENTE
     * ==========================================================
     */

    @PostMapping("/{id}/editar")
    public String atualizar(
            @PathVariable
            Long id,

            @Valid
            @ModelAttribute("cliente")
            Cliente cliente,

            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        //O ID utilizado na atualização sempre vem da URL.
        cliente.setId(id);

        // Erros do Bean Validation.
        if (bindingResult.hasErrors()) {
            model.addAttribute("erro", obterPrimeiraMensagemErro(bindingResult));

            return "admin/clientes/editar-cliente";
        }

        try {
            clienteService.atualizar(id, cliente);
            redirectAttributes.addFlashAttribute("mensagem", "Cliente atualizado com sucesso!");

            return "redirect:/admin/clientes/" + id;

        } catch (CpfJaCadastradoException | EmailJaCadastradoException | ValidacaoException e) {
            model.addAttribute("cliente", cliente);
            model.addAttribute("erro", e.getMessage());

            return "admin/clientes/editar-cliente";

        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());

            return "redirect:/admin/clientes";
        }
    }


    /*
     * ==========================================================
     * INATIVAR CLIENTE
     * ==========================================================
     */

    @PostMapping("/{id}/inativar")
    public String inativar(
            @PathVariable
            Long id,

            RedirectAttributes redirectAttributes) {

        try {
            clienteService.inativar(id);
            redirectAttributes.addFlashAttribute("mensagem", "Cliente inativado com sucesso!");

        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }

        return "redirect:/admin/clientes";
    }


    /*
     * ==========================================================
     * REATIVAR CLIENTE
     * ==========================================================
     */

    @PostMapping("/{id}/reativar")
    public String reativar(
            @PathVariable
            Long id,

            RedirectAttributes redirectAttributes) {

        try {
            clienteService.reativar(id);

            redirectAttributes.addFlashAttribute("mensagem", "Cliente reativado com sucesso!");
        } catch (ClienteNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }


        return "redirect:/admin/clientes";
    }


    /*
     * ==========================================================
     * MÉTODOS AUXILIARES
     * ==========================================================
     */

    private Cliente criarClienteParaFormulario() {
        Cliente cliente = new Cliente();

        Endereco endereco = new Endereco();
        endereco.setCliente(cliente);
        endereco.setEnderecoEntrega(false);
        endereco.setEnderecoCobranca(false);
        cliente.getEnderecos().add(endereco);

        Telefone telefone = new Telefone();
        telefone.setCliente(cliente);
        cliente.getTelefones().add(telefone);

        CartaoCredito cartao = new CartaoCredito();
        cartao.setCliente(cliente);
        cartao.setPreferencial(true);
        cliente.getCartoesCredito().add(cartao);

        return cliente;
    }

    private String limparFiltro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }

    /*
     * ==========================================================
     * PRIMEIRA MENSAGEM DE ERRO
     * ==========================================================
     */

    private String obterPrimeiraMensagemErro(BindingResult bindingResult) {
        if (bindingResult == null || !bindingResult.hasErrors()) {
            return null;
        }
        if (!bindingResult.getFieldErrors().isEmpty()) {
            FieldError primeiroErro = bindingResult.getFieldErrors().getFirst();
            if (primeiroErro.getDefaultMessage() != null && !primeiroErro.getDefaultMessage().isBlank()) {
                return primeiroErro.getDefaultMessage();
            }
        }

        if (!bindingResult.getGlobalErrors().isEmpty()) {
            String mensagem = bindingResult.getGlobalErrors().getFirst().getDefaultMessage();

            if (mensagem != null && !mensagem.isBlank()) {
                return mensagem;
            }
        }
        return "Verifique os dados informados.";
    }
}