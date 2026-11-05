package br.com.java.nexus.controller;

import br.com.java.nexus.model.produto.Produto;
import br.com.java.nexus.repository.produto.ProdutoRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/produtos")
public class ProdutoController {

    private final ProdutoRepository produtoRepository;

    public ProdutoController(
            ProdutoRepository produtoRepository) {

        this.produtoRepository =
                produtoRepository;
    }


    /*
     * ==========================================================
     * LISTAGEM DE PRODUTOS
     * ==========================================================
     */

    @GetMapping
    public String listar(
            @RequestParam(required = false)
            String busca,

            Model model) {

        List<Produto> produtos;

        if (busca != null
                && !busca.isBlank()) {

            produtos =
                    produtoRepository
                            .findByTituloContainingIgnoreCaseAndAtivoTrueOrderByTituloAsc(
                                    busca.trim()
                            );

        } else {

            produtos =
                    produtoRepository
                            .findByAtivoTrueAndEstoqueGreaterThanOrderByTituloAsc(
                                    0
                            );
        }

        model.addAttribute(
                "produtos",
                produtos
        );

        model.addAttribute(
                "busca",
                busca
        );

        return "produtos/lista-produtos";
    }
}
