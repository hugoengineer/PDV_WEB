package br.com.empresa.pdv;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * A aplicação com as configurações alternativas de produção: comissão sobre o valor bruto
 * (PDV_COMISSAO_BASE=BRUTO) e HTTPS obrigatório (PDV_EXIGIR_HTTPS=true).
 */
@TestPropertySource(properties = {"pdv.comissao.base=BRUTO", "pdv.seguranca.exigir-https=true"})
@DisplayName("Configurações alternativas: comissão bruta e HTTPS obrigatório (integração)")
class ConfiguracoesAlternativasIT extends TesteIntegracao {

    /** Todas as requisições do teste chegam como HTTPS (como atrás do balanceador com TLS). */
    @Override
    protected RequestPostProcessor ajusteDaRequisicao() {
        return requisicao -> {
            requisicao.setSecure(true);
            requisicao.setScheme("https");
            requisicao.setServerPort(443);
            return requisicao;
        };
    }

    @Test
    @DisplayName("CT-COM-004 Com base BRUTO, a comissão incide sobre o subtotal: 5% de R$ 250,00 = R$ 12,50")
    void comissaoSobreBruto() throws Exception {
        Operador vendedor = criarVendedor("Jean", "5.0");
        long caixa = abrirCaixa(vendedor.token(), "0");
        long produto = criarProduto("BRUTO-1", "Teclado", "250.00", 10, 0);
        criarCupom("DESCONTO10", "PERCENTUAL", "10", OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));

        postar("/vendas", vendedor.token(), """
                {"caixa_id": %d, "cupom_codigo": "DESCONTO10", "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 1}]}""".formatted(caixa, produto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor_total").value(225.00))
                .andExpect(jsonPath("$.comissao_vendedor").value(12.50));
    }

    @Test
    @DisplayName("CT-SEG-020 Com HTTPS obrigatório, requisição HTTP é redirecionada para HTTPS")
    void redirecionaParaHttps() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", Matchers.startsWith("https://")));
        obter("/actuator/health", null).andExpect(status().isOk());
    }
}
