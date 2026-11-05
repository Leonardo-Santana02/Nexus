package br.com.java.nexus.controller;

import br.com.java.nexus.service.analise.AnaliseVendasService;
import br.com.java.nexus.service.exception.ValidacaoException;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
public class AnaliseVendasController {

    private final AnaliseVendasService analiseVendasService;

    public AnaliseVendasController(AnaliseVendasService analiseVendasService) {
        this.analiseVendasService = analiseVendasService;
    }


    /*
     * ==========================================================
     * TELA DE ANÁLISE DE VENDAS
     * ==========================================================
     *
     * Renderiza o template admin/dashboard.html.
     *
     * Aceita tanto /admin/dashboard (link do sidebar) quanto
     * /admin/analise-vendas (rota semântica).
     *
     * Quando os filtros são informados, executa a análise e
     * passa o resultado via model. Quando não são informados,
     * mostra a tela com os campos vazios (ou datas padrão).
     * ==========================================================
     */

    @GetMapping({"/admin/dashboard", "/admin/analise-vendas"})
    public String analiseVendas(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate dataInicio,

            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd")
            LocalDate dataFim,

            @RequestParam(required = false)
            List<String> categorias,

            Model model,
            RedirectAttributes redirectAttributes) {

        List<String> categoriasDisponiveis = new ArrayList<>();
        Map<String, Object> resultado = new HashMap<>();

        try {
            categoriasDisponiveis = analiseVendasService.listarCategoriasDisponiveis();

            /*
             * Sem filtros: apenas exibe a tela com os campos vazios
             * e as categorias disponíveis.
             */
            if (dataInicio == null && dataFim == null
                    && (categorias == null || categorias.isEmpty())) {

                /*
                 * Datas padrão sugeridas (últimos 6 meses) — apenas
                 * para o formulário começar preenchido.
                 */
                dataFim = LocalDate.now();
                dataInicio = dataFim.minusMonths(5).withDayOfMonth(1);

                model.addAttribute("dataInicio", dataInicio);
                model.addAttribute("dataFim", dataFim);
                model.addAttribute("categoriasSelecionadas", new ArrayList<String>());
                model.addAttribute("categoriasDisponiveis", categoriasDisponiveis);
                model.addAttribute("resultado", resultado);
                model.addAttribute("temResultado", false);

                return "admin/dashboard";
            }

            /*
             * Com filtros: executa a análise.
             */
            resultado = analiseVendasService.analisar(
                    dataInicio, dataFim, categorias);

            model.addAttribute("dataInicio", dataInicio);
            model.addAttribute("dataFim", dataFim);
            model.addAttribute("categoriasSelecionadas",
                    categorias != null ? categorias : new ArrayList<String>());
            model.addAttribute("categoriasDisponiveis", categoriasDisponiveis);
            model.addAttribute("resultado", resultado);
            model.addAttribute("temResultado", true);

            return "admin/dashboard";

        } catch (ValidacaoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());

            model.addAttribute("dataInicio", dataInicio);
            model.addAttribute("dataFim", dataFim);
            model.addAttribute("categoriasSelecionadas",
                    categorias != null ? categorias : new ArrayList<String>());
            model.addAttribute("categoriasDisponiveis", categoriasDisponiveis);
            model.addAttribute("resultado", new HashMap<>());
            model.addAttribute("temResultado", false);

            return "admin/dashboard";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("erro",
                    "Não foi possível executar a análise: " + e.getMessage());

            model.addAttribute("dataInicio", dataInicio);
            model.addAttribute("dataFim", dataFim);
            model.addAttribute("categoriasSelecionadas",
                    categorias != null ? categorias : new ArrayList<String>());
            model.addAttribute("categoriasDisponiveis", categoriasDisponiveis);
            model.addAttribute("resultado", new HashMap<>());
            model.addAttribute("temResultado", false);

            return "admin/dashboard";
        }
    }
}