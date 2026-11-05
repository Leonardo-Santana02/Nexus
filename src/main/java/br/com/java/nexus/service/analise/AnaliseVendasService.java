package br.com.java.nexus.service.analise;

import br.com.java.nexus.model.pedido.ItemPedido;
import br.com.java.nexus.model.pedido.Pedido;
import br.com.java.nexus.model.pedido.enums.StatusPedido;
import br.com.java.nexus.repository.pedido.PedidoRepository;
import br.com.java.nexus.repository.produto.ProdutoRepository;
import br.com.java.nexus.service.exception.ValidacaoException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Service
public class AnaliseVendasService {

    private static final Logger logger = LoggerFactory.getLogger(AnaliseVendasService.class);

    private static final DateTimeFormatter FORMATO_LABEL_MES =
            DateTimeFormatter.ofPattern("MMM/yyyy", new Locale("pt", "BR"));

    private static final int MESES_MINIMOS = 1;
    private static final int MESES_MAXIMOS = 24;

    private static final String[] PALETA_CORES = {
            "#1e88e5", "#26a69a", "#ff9800", "#9c27b0",
            "#f44336", "#4caf50", "#795548", "#607d8b",
            "#e91e63", "#00bcd4", "#8bc34a", "#ff5722"
    };

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;


    public AnaliseVendasService(PedidoRepository pedidoRepository,
                                ProdutoRepository produtoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
    }

    @Transactional(readOnly = true)
    public List<String> listarCategoriasDisponiveis() {
        return produtoRepository.listarGenerosDisponiveis();
    }


    @Transactional(readOnly = true)
    public Map<String, Object> analisar(LocalDate dataInicio, LocalDate dataFim, List<String> categorias) {
        validarFiltros(dataInicio, dataFim, categorias);
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(23, 59, 59);

        /*
         * Busca TODOS os pedidos entregues do período (sem filtrar
         * por categoria na query), porque o rateio de desconto e
         * frete depende do total do pedido inteiro. A filtragem
         * por categoria é feita item a item, depois.
         */
        List<Pedido> pedidos = pedidoRepository.buscarEntreguesPorPeriodoComItens(StatusPedido.ENTREGUE, inicio, fim);

        /*
         * Gera labels de mês/ano do período (inclusive).
         * Ex.: jan/2026, fev/2026, mar/2026, ...
         */
        List<YearMonth> meses = gerarMesesDoPeriodo(dataInicio, dataFim);

        List<String> labels = new ArrayList<>();
        for (YearMonth mes : meses) {
            labels.add(mes.atDay(1).format(FORMATO_LABEL_MES));
        }

        /*
         * Acumulado por categoria -> índice do mês -> valor.
         * Usa LinkedHashMap para manter a ordem de categorias
         * selecionadas pelo admin.
         */
        Map<String, BigDecimal[]> acumuladoPorCategoria = new LinkedHashMap<>();
        for (String categoria : categorias) {
            acumuladoPorCategoria.put(categoria, new BigDecimal[meses.size()]);
        }

        BigDecimal totalGeral = BigDecimal.ZERO;
        int totalItens = 0;

        for (Pedido pedido : pedidos) {
            if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
                continue;
            }

            BigDecimal subtotal = pedido.getSubtotal() != null
                    ? pedido.getSubtotal()
                    : BigDecimal.ZERO;

            BigDecimal desconto = pedido.getDescontoPromocional() != null
                    ? pedido.getDescontoPromocional()
                    : BigDecimal.ZERO;

            BigDecimal frete = pedido.getValorFrete() != null
                    ? pedido.getValorFrete()
                    : BigDecimal.ZERO;

            int indiceMes = meses.indexOf(YearMonth.from(pedido.getDataCriacao()));

            if (indiceMes < 0) {
                // Segurança: pedido fora do período esperado (não deve acontecer)
                continue;
            }

            for (ItemPedido item : pedido.getItens()) {
                if (item.getProduto() == null || item.getProduto().getGenero() == null) {
                    continue;
                }

                String categoria = item.getProduto().getGenero();

                if (!acumuladoPorCategoria.containsKey(categoria)) {
                    // Categoria não selecionada pelo admin — ignorar
                    continue;
                }

                BigDecimal precoItem = item.getPrecoTotal() != null
                        ? item.getPrecoTotal()
                        : BigDecimal.ZERO;

                BigDecimal valorLiquidoItem = aplicarRateio(
                        precoItem, subtotal, desconto, frete);

                BigDecimal[] serie = acumuladoPorCategoria.get(categoria);
                if (serie[indiceMes] == null) {
                    serie[indiceMes] = BigDecimal.ZERO;
                }
                serie[indiceMes] = serie[indiceMes].add(valorLiquidoItem);

                totalGeral = totalGeral.add(valorLiquidoItem);
                totalItens += item.getQuantidade();
            }
        }

        /*
         * Monta os datasets no formato esperado pelo Chart.js.
         */
        List<Map<String, Object>> datasets = new ArrayList<>();
        int corIndex = 0;

        for (Map.Entry<String, BigDecimal[]> entry : acumuladoPorCategoria.entrySet()) {
            String categoria = entry.getKey();
            BigDecimal[] serie = entry.getValue();

            List<BigDecimal> valores = new ArrayList<>(serie.length);

            for (BigDecimal valor : serie) {
                BigDecimal v = valor != null
                        ? valor.setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                valores.add(v);
            }

            String cor = PALETA_CORES[corIndex % PALETA_CORES.length];

            Map<String, Object> dataset = new HashMap<>();
            dataset.put("label", categoria);
            dataset.put("data", valores);
            dataset.put("borderColor", cor);
            dataset.put("backgroundColor", cor + "20");
            dataset.put("fill", false);
            dataset.put("tension", 0.3);
            dataset.put("pointRadius", 4);
            dataset.put("pointHoverRadius", 6);
            dataset.put("pointBackgroundColor", cor);
            dataset.put("pointBorderColor", "#fff");
            dataset.put("pointBorderWidth", 2);
            dataset.put("borderWidth", 3);

            datasets.add(dataset);
            corIndex++;
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("labels", labels);
        resultado.put("datasets", datasets);
        resultado.put("totalGeral", totalGeral.setScale(2, RoundingMode.HALF_UP));
        resultado.put("totalCategorias", categorias.size());
        resultado.put("totalMeses", meses.size());
        resultado.put("totalItens", totalItens);
        resultado.put("dataInicio", dataInicio);
        resultado.put("dataFim", dataFim);

        logger.info("Análise de vendas: período={} a {}, categorias={}, " +
                        "pedidosEntregues={}, totalGeral={}",
                dataInicio, dataFim, categorias, pedidos.size(), totalGeral);

        return resultado;
    }


    /*
     * ==========================================================
     * HELPERS
     * ==========================================================
     */

    /**
     * Aplica o rateio proporcional do desconto e do frete sobre o
     * valor bruto de um item.
     *
     * Fórmula:
     *   valorLiquido = precoItem
     *                - (desconto * precoItem / subtotal)
     *                + (frete    * precoItem / subtotal)
     *
     * Se subtotal = 0, retorna o próprio precoItem (sem rateio).
     */
    private BigDecimal aplicarRateio(BigDecimal precoItem,
                                     BigDecimal subtotal,
                                     BigDecimal desconto,
                                     BigDecimal frete) {

        if (precoItem == null) {
            return BigDecimal.ZERO;
        }

        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return precoItem;
        }

        BigDecimal proporcao = precoItem.divide(subtotal, 6, RoundingMode.HALF_UP);

        BigDecimal descontoRateado = (desconto != null ? desconto : BigDecimal.ZERO)
                .multiply(proporcao)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal freteRateado = (frete != null ? frete : BigDecimal.ZERO)
                .multiply(proporcao)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorLiquido = precoItem
                .subtract(descontoRateado)
                .add(freteRateado);

        return valorLiquido.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }


    /**
     * Gera a lista de meses (YearMonth) cobertos pelo período informado.
     * O mês inicial e o mês final são sempre incluídos, mesmo que
     * o período não comece no dia 1 ou termine no último dia do mês.
     */
    private List<YearMonth> gerarMesesDoPeriodo(LocalDate inicio, LocalDate fim) {
        YearMonth mesInicio = YearMonth.from(inicio);
        YearMonth mesFim = YearMonth.from(fim);

        List<YearMonth> meses = new ArrayList<>();
        YearMonth atual = mesInicio;

        while (!atual.isAfter(mesFim)) {
            meses.add(atual);
            atual = atual.plusMonths(1);
        }

        return meses;
    }


    /**
     * Valida os filtros informados pelo admin.
     *
     * Regras:
     * - dataInicio e dataFim obrigatórios.
     * - dataInicio <= dataFim.
     * - Período mínimo de 1 mês e máximo de 24 meses (RN0072).
     * - Ao menos 1 categoria selecionada.
     */
    private void validarFiltros(LocalDate dataInicio,
                                LocalDate dataFim,
                                List<String> categorias) {

        if (dataInicio == null) {
            throw new ValidacaoException("Data de início é obrigatória.");
        }

        if (dataFim == null) {
            throw new ValidacaoException("Data de fim é obrigatória.");
        }

        if (dataInicio.isAfter(dataFim)) {
            throw new ValidacaoException(
                    "A data de início não pode ser posterior à data de fim.");
        }

        YearMonth mesInicio = YearMonth.from(dataInicio);
        YearMonth mesFim = YearMonth.from(dataFim);
        long totalMeses = ChronoUnit.MONTHS.between(mesInicio, mesFim) + 1;

        if (totalMeses < MESES_MINIMOS) {
            throw new ValidacaoException(
                    "O período deve ter no mínimo " + MESES_MINIMOS + " mês.");
        }

        if (totalMeses > MESES_MAXIMOS) {
            throw new ValidacaoException(
                    "O período deve ter no máximo " + MESES_MAXIMOS + " meses.");
        }

        if (categorias == null || categorias.isEmpty()) {
            throw new ValidacaoException(
                    "Selecione ao menos uma categoria para análise.");
        }
    }
}