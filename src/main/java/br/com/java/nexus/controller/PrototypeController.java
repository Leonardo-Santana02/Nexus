package br.com.java.nexus.controller;

import br.com.java.nexus.repository.produto.ProdutoRepository;
import br.com.java.nexus.service.cliente.ClienteService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PrototypeController {

    private final ClienteService clienteService;
    private final ProdutoRepository produtoRepository;


    public PrototypeController(
            ClienteService clienteService,
            ProdutoRepository produtoRepository) {

        this.clienteService =
                clienteService;

        this.produtoRepository =
                produtoRepository;
    }


    /*
     * ==========================================================
     * LOGIN
     * ==========================================================
     */

    @GetMapping({
            "/",
            "/login",
            "/login.html"
    })
    public String login() {

        return "auth/login";
    }


    /*
     * ==========================================================
     * HOME DO CLIENTE
     * ==========================================================
     */

    @GetMapping({
            "/home",
            "/home.html"
    })
    public String home(
            Model model) {

        model.addAttribute(
                "clientes",
                clienteService.listarAtivos()
        );

        model.addAttribute(
                "produtos",
                produtoRepository
                        .findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(
                                0
                        )
        );

        return "cliente/home";
    }


    /*
     * ==========================================================
     * HOME ADMINISTRATIVA
     * ==========================================================
     */

    @GetMapping({
            "/admin",
            "/admin-home.html"
    })
    public String adminHome() {

        return "admin/home";
    }

    /*
     * ==========================================================
     * TROCA
     * ==========================================================
     */

    @GetMapping({
            "/solicitar-troca",
            "/solicitar-troca.html"
    })
    public String requestExchange() {

        return "cliente/solicitar-troca";
    }

    /*
     * ==========================================================
     * ADMINISTRAÇÃO
     * ==========================================================
     */

}