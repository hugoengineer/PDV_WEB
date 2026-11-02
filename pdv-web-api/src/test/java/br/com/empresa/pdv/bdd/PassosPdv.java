package br.com.empresa.pdv.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.cucumber.spring.CucumberContextConfiguration;

import org.springframework.test.web.servlet.ResultActions;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * Passos dos cenários Gherkin em src/test/resources/features.
 * Cada cenário roda contra a API completa e o PostgreSQL embarcado, com o banco limpo.
 */
@CucumberContextConfiguration
public class PassosPdv extends TesteIntegracao {

    private final Map<String, Long> produtos = new HashMap<>();
    private Operador vendedor;
    private Long caixaVendedor;
    private Long caixaGerente;
    private Long ultimaVenda;
    private ResultActions resposta;

    @Before
    public void limparCenario() throws Exception {
        prepararBanco();
        produtos.clear();
        caixaVendedor = null;
        caixaGerente = null;
        ultimaVenda = null;
    }

    // ------------------------------------------------------------------ Dado

    @Dado("que o vendedor {string} está autenticado com o caixa aberto")
    public void vendedorComCaixa(String nome) throws Exception {
        vendedor = criarVendedor(nome, "5.00");
        caixaVendedor = abrirCaixa(vendedor.token(), "100.00");
    }

    @Dado("que o vendedor {string} está autenticado sem caixa aberto")
    public void vendedorSemCaixa(String nome) throws Exception {
        vendedor = criarVendedor(nome, "5.00");
    }

    @Dado("que o produto {string} {string} tem {int} unidades em estoque e mínimo de {int}")
    public void produto(String codigo, String nome, int estoque, int minimo) throws Exception {
        produtos.put(codigo, criarProduto(codigo, nome, codigo.startsWith("PROD-ELE") ? "250.00" : "50.00", estoque, minimo));
    }

    @Dado("que existe o cupom vigente {string} de {int} por cento")
    public void cupomVigente(String codigo, int percentual) throws Exception {
        criarCupom(codigo, "PERCENTUAL", String.valueOf(percentual), OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(1));
    }

    @Dado("o vendedor registrou a venda de {int} unidades do produto {string}")
    public void vendaRegistrada(int quantidade, String codigo) throws Exception {
        vendaDoVendedor(quantidade, codigo);
        aApiDeveResponder(201);
    }

    @Dado("que o gerente cancelou a venda com o motivo {string}")
    public void gerenteCancelou(String motivo) throws Exception {
        gerenteCancela(motivo);
        aApiDeveResponder(200);
    }

    @Dado("que o vendedor abriu o caixa com suprimento inicial de {string}")
    public void vendedorAbriuCaixa(String valor) throws Exception {
        caixaVendedor = abrirCaixa(vendedor.token(), valor);
    }

    @Dado("o vendedor fechou o caixa informando {string} em dinheiro")
    public void vendedorFechouCaixa(String valor) throws Exception {
        resposta = postar("/caixas/" + caixaVendedor + "/fechamento", vendedor.token(),
                "{\"valor_final_dinheiro\": " + valor + "}");
        aApiDeveResponder(200);
    }

    // ------------------------------------------------------------------ Quando

    @Quando("o vendedor registra a venda de {int} unidades do produto {string}")
    public void vendaDoVendedor(int quantidade, String codigo) throws Exception {
        long caixa = caixaVendedor == null ? 0 : caixaVendedor;
        resposta = postar("/vendas", vendedor.token(), vendaSimples(caixa, produtos.get(codigo), quantidade));
        if (resposta.andReturn().getResponse().getStatus() == 201) {
            ultimaVenda = lerId(resposta, "$.venda_id");
        }
    }

    // O desconto chega como texto: {bigdecimal} usa o locale da feature (pt), em que "10.00" vira 1000 (DEF-TST-001)
    @Quando("o {word} registra a venda de 1 unidade do produto {string} com cupom {string}, desconto direto de {word} e autorização {string}")
    public void vendaComDescontos(String operador, String codigo, String cupom, String descontoTexto,
                                  String autorizacao) throws Exception {
        BigDecimal desconto = new BigDecimal(descontoTexto);
        boolean gerente = operador.equals("gerente");
        String token = gerente ? tokenGerente : vendedor.token();
        long caixa = gerente ? caixaDoGerente() : caixaVendedor;
        String senha = switch (autorizacao) {
            case "senha do gerente" -> "\"senha_autorizacao_gerente\": \"" + SENHA_GERENTE + "\",";
            case "senha incorreta" -> "\"senha_autorizacao_gerente\": \"chute123\",";
            default -> "";
        };
        String campoCupom = cupom.isBlank() ? "" : "\"cupom_codigo\": \"" + cupom + "\",";
        resposta = postar("/vendas", token, """
                {"caixa_id": %d, %s "desconto_direto_gerente": %s, %s "modalidade_pagamento": "PIX",
                 "itens": [{"produto_id": %d, "quantidade": 1}]}"""
                .formatted(caixa, campoCupom, desconto.toPlainString(), senha, produtos.get(codigo)));
    }

    @Quando("o vendedor tenta cancelar a venda com o motivo {string}")
    public void vendedorTentaCancelar(String motivo) throws Exception {
        resposta = postar("/vendas/" + ultimaVenda + "/cancelar", vendedor.token(), motivoJson(motivo));
    }

    @Quando("o gerente cancela a venda com o motivo {string}")
    public void gerenteCancela(String motivo) throws Exception {
        resposta = postar("/vendas/" + ultimaVenda + "/cancelar", tokenGerente, motivoJson(motivo));
    }

    @Quando("o vendedor abre o caixa com suprimento inicial de {string}")
    public void vendedorAbreCaixa(String valor) throws Exception {
        caixaVendedor = abrirCaixa(vendedor.token(), valor);
    }

    @Quando("o gerente realiza uma sangria de {string} no caixa do vendedor")
    public void sangria(String valor) throws Exception {
        resposta = postar("/caixas/" + caixaVendedor + "/sangria", tokenGerente,
                "{\"valor\": " + valor + ", \"observacao\": \"Sangria BDD\"}");
    }

    // ------------------------------------------------------------------ Então

    @Então("a API deve responder com o status {int}")
    public void aApiDeveResponder(int status) throws Exception {
        assertThat(resposta.andReturn().getResponse().getStatus())
                .as(resposta.andReturn().getResponse().getContentAsString())
                .isEqualTo(status);
    }

    @E("o estoque do produto {string} deve ser de {int} unidades")
    public void estoque(String codigo, int esperado) {
        assertThat(estoqueDe(produtos.get(codigo))).isEqualTo(esperado);
    }

    @E("o alerta de estoque crítico do produto {string} deve estar {string}")
    public void alerta(String codigo, String estado) throws Exception {
        boolean critico = ler(obter("/produtos/" + produtos.get(codigo), vendedor.token()), "$.alerta_estoque_critico");
        assertThat(critico).isEqualTo(estado.equals("ligado"));
    }

    @E("o motivo do erro deve ser {string}")
    public void motivoDoErro(String motivo) throws Exception {
        String obtido = ler(resposta, "$.invalid_params[0].reason");
        assertThat(obtido).isEqualTo(motivo);
    }

    @E("o título do erro deve ser {string}")
    public void tituloDoErro(String titulo) throws Exception {
        String obtido = ler(resposta, "$.title");
        assertThat(obtido).isEqualTo(titulo);
    }

    @E("o tipo do erro deve ser {string}")
    public void tipoDoErro(String tipo) throws Exception {
        String obtido = ler(resposta, "$.type");
        assertThat(obtido).isEqualTo("https://api.pdvweb.com/erros/" + tipo);
    }

    @E("nenhuma venda deve ter sido gravada")
    public void nenhumaVenda() {
        assertThat(contar("vendas")).isZero();
    }

    @E("a venda deve estar com o status {string}")
    public void statusDaVenda(String status) throws Exception {
        String obtido = ler(obter("/vendas/" + ultimaVenda, tokenGerente), "$.status");
        assertThat(obtido).isEqualTo(status);
    }

    @E("deve existir {int} registro de auditoria do tipo {string}")
    public void auditoria(int quantidade, String tipo) {
        assertThat(contarAuditoria(tipo)).isEqualTo(quantidade);
    }

    @E("o valor total da venda deve ser {string}")
    public void valorTotal(String total) throws Exception {
        if (!total.equals("-")) {
            Number obtido = ler(resposta, "$.valor_total");
            assertThat(new BigDecimal(obtido.toString())).isEqualByComparingTo(total);
        }
    }

    private long caixaDoGerente() throws Exception {
        if (caixaGerente == null) {
            caixaGerente = abrirCaixa(tokenGerente, "0.00");
        }
        return caixaGerente;
    }

    private static String motivoJson(String motivo) {
        return "{\"motivo_cancelamento\": \"" + motivo + "\"}";
    }
}
