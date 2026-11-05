package br.com.java.nexus.service.carrinho;

import br.com.java.nexus.model.carrinho.Carrinho;
import br.com.java.nexus.model.carrinho.ItemCarrinho;
import br.com.java.nexus.model.cliente.Endereco;
import br.com.java.nexus.model.cliente.enums.UF;

import br.com.java.nexus.service.exception.ValidacaoException;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

@Service
public class FreteService {

    private static final Map<UF, BigDecimal> TARIFA_REGIONAL = new EnumMap<>(UF.class);
    private static final BigDecimal VALOR_POR_QUILO = new BigDecimal("2.00");

    static {
        //Sudeste
        TARIFA_REGIONAL.put(UF.SP, new BigDecimal("10.00"));
        TARIFA_REGIONAL.put(UF.RJ, new BigDecimal("15.00"));
        TARIFA_REGIONAL.put(UF.MG, new BigDecimal("15.00"));
        TARIFA_REGIONAL.put(UF.ES, new BigDecimal("15.00"));

        //Sul
        TARIFA_REGIONAL.put(UF.PR, new BigDecimal("18.00"));
        TARIFA_REGIONAL.put(UF.SC, new BigDecimal("18.00"));
        TARIFA_REGIONAL.put(UF.RS, new BigDecimal("18.00"));

        //Centro-Oeste
        TARIFA_REGIONAL.put(UF.DF, new BigDecimal("20.00"));
        TARIFA_REGIONAL.put(UF.GO, new BigDecimal("20.00"));
        TARIFA_REGIONAL.put(UF.MT, new BigDecimal("20.00"));
        TARIFA_REGIONAL.put(UF.MS, new BigDecimal("20.00"));

        //Nordeste
        TARIFA_REGIONAL.put(UF.BA, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.SE, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.AL, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.PE, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.PB, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.RN, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.CE, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.PI, new BigDecimal("25.00"));
        TARIFA_REGIONAL.put(UF.MA, new BigDecimal("25.00"));

        //Norte
        TARIFA_REGIONAL.put(UF.AM, new BigDecimal("30.00"));
        TARIFA_REGIONAL.put(UF.AC, new BigDecimal("30.00"));
        TARIFA_REGIONAL.put(UF.RO, new BigDecimal("30.00"));
        TARIFA_REGIONAL.put(UF.RR, new BigDecimal("30.00"));
        TARIFA_REGIONAL.put(UF.PA, new BigDecimal("30.00"));
        TARIFA_REGIONAL.put(UF.AP, new BigDecimal("30.00"));
        TARIFA_REGIONAL.put(UF.TO, new BigDecimal("30.00"));
    }

    public BigDecimal calcularFrete(Carrinho carrinho, Endereco endereco) {
        if (carrinho == null || carrinho.isVazio()) {
            throw new ValidacaoException("Não é possível calcular o frete de um carrinho vazio.");
        }

        if (endereco == null || endereco.getUf() == null) {
            throw new ValidacaoException("Endereço de entrega inválido.");
        }

        BigDecimal tarifaRegional = TARIFA_REGIONAL.get(endereco.getUf());

        if (tarifaRegional == null) {
            throw new ValidacaoException("Não foi possível calcular o frete para o estado informado.");
        }

        BigDecimal pesoTotal = calcularPesoTotal(carrinho);
        BigDecimal adicionalPeso = pesoTotal.multiply(VALOR_POR_QUILO);

        return tarifaRegional.add(adicionalPeso).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularPesoTotal(Carrinho carrinho) {
        BigDecimal pesoTotal = BigDecimal.ZERO;

        for (ItemCarrinho item : carrinho.getItens()) {
            if (item.getProduto() == null || item.getProduto().getPeso() == null) {
                continue;
            }

            BigDecimal pesoProduto = BigDecimal.valueOf(item.getProduto().getPeso());
            BigDecimal pesoItem = pesoProduto.multiply(BigDecimal.valueOf(item.getQuantidade()));

            pesoTotal = pesoTotal.add(pesoItem);
        }
        return pesoTotal;
    }
}