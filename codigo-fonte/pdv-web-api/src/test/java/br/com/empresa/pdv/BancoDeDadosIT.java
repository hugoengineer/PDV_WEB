package br.com.empresa.pdv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import br.com.empresa.pdv.suporte.TesteIntegracao;

/**
 * Restrições e gatilhos do esquema (Flyway V1 e V4) como última barreira, mesmo que a aplicação falhe:
 * o banco precisa rejeitar dados inconsistentes gravados por qualquer caminho.
 */
@DisplayName("Integridade do banco de dados (integração)")
class BancoDeDadosIT extends TesteIntegracao {

    @Test
    @DisplayName("CT-DB-002 CHECK impede estoque negativo direto no banco")
    void estoqueNegativo() {
        jdbc.update("INSERT INTO produtos (sku, nome, preco_venda, estoque_atual) VALUES ('DB-1', 'X', 1.00, 1)");
        assertThatThrownBy(() -> jdbc.update("UPDATE produtos SET estoque_atual = estoque_atual - 2 WHERE sku = 'DB-1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CT-DB-003 Índice único parcial impede dois caixas ABERTOS para o mesmo operador")
    void doisCaixasAbertos() {
        jdbc.update("INSERT INTO caixas (usuario_id, suprimento_inicial) VALUES (?, 0)", idGerente);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO caixas (usuario_id, suprimento_inicial) VALUES (?, 0)", idGerente))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE caixas SET status = 'FECHADO', data_fechamento = now()");
        jdbc.update("INSERT INTO caixas (usuario_id, suprimento_inicial) VALUES (?, 0)", idGerente);
        assertThat(contar("caixas")).isEqualTo(2);
    }

    @Test
    @DisplayName("CT-DB-004 CHECK garante valor_total = subtotal - descontos na tabela de vendas")
    void consistenciaTotal() {
        jdbc.update("INSERT INTO caixas (usuario_id, suprimento_inicial) VALUES (?, 0)", idGerente);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO vendas (usuario_id, cliente_id, caixa_id, valor_subtotal, desconto_cupom, desconto_gerente,
                                    valor_total, modalidade_pagamento, percentual_comissao, valor_comissao)
                VALUES (?, 1, 1, 100.00, 10.00, 0, 95.00, 'PIX', 0, 0)""", idGerente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CT-DB-005 Cliente PF exige 11 dígitos e PJ 14 dígitos; documento é único")
    void documentoCliente() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO clientes (tipo, cpf_cnpj, nome_razao_social) VALUES ('PJ', '12345678901', 'X')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO clientes (tipo, cpf_cnpj, nome_razao_social) VALUES ('PF', '00000000000', 'Duplicado')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CT-DB-006 Migrações Flyway aplicadas na ordem e sem falhas (V1 a V4)")
    void migracoes() {
        var versoes = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versoes).containsExactly("1", "2", "3", "4");
    }

    @Test
    @DisplayName("CT-DB-007 Log de auditoria é imutável: UPDATE e DELETE direto no banco são rejeitados")
    void auditoriaImutavel() {
        jdbc.update("INSERT INTO logs_auditoria (tipo_acao, detalhes) VALUES ('LOGIN_SUCESSO', 'teste')");
        assertThatThrownBy(() -> jdbc.update("UPDATE logs_auditoria SET detalhes = 'adulterado' WHERE detalhes = 'teste'"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("imutável");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM logs_auditoria WHERE detalhes = 'teste'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM logs_auditoria WHERE detalhes = 'teste'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("CT-DB-008 Venda: valores não mudam, não é apagada; só CONCLUIDA → CANCELADA, e cancelada não volta")
    void vendaProtegida() {
        long venda = vendaGravada();
        assertThatThrownBy(() -> jdbc.update("UPDATE vendas SET valor_total = 1.00, valor_subtotal = 1.00 WHERE id = ?", venda))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM vendas WHERE id = ?", venda))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE itens_venda SET quantidade = 9, subtotal = 90.00"))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("""
                UPDATE vendas SET status = 'CANCELADA', motivo_cancelamento = 'Teste', data_cancelamento = now(),
                                  usuario_cancelamento_id = ? WHERE id = ?""", idGerente, venda);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE vendas SET status = 'CONCLUIDA', motivo_cancelamento = NULL, data_cancelamento = NULL,
                                  usuario_cancelamento_id = NULL WHERE id = ?""", venda))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CT-DB-009 Cancelamento sem motivo/gerente/data e item com subtotal errado são rejeitados (CHECK)")
    void consistenciaCancelamentoEItem() {
        long venda = vendaGravada();
        assertThatThrownBy(() -> jdbc.update("UPDATE vendas SET status = 'CANCELADA' WHERE id = ?", venda))
                .isInstanceOf(DataIntegrityViolationException.class);
        long produto = jdbc.queryForObject("SELECT produto_id FROM itens_venda LIMIT 1", Long.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario, subtotal)
                VALUES (?, ?, 2, 10.00, 15.00)""", venda, produto))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CT-DB-010 Caixa: fechar sem data é rejeitado; caixa fechado não reabre nem é apagado")
    void caixaProtegido() {
        jdbc.update("INSERT INTO caixas (usuario_id, suprimento_inicial) VALUES (?, 0)", idGerente);
        assertThatThrownBy(() -> jdbc.update("UPDATE caixas SET status = 'FECHADO'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE caixas SET status = 'FECHADO', data_fechamento = now(), valor_final_informado = 0");
        assertThatThrownBy(() -> jdbc.update("UPDATE caixas SET status = 'ABERTO', data_fechamento = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE caixas SET valor_final_informado = 999"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM caixas"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Venda de R$ 20,00 (2 × R$ 10,00) gravada direto no banco, com caixa aberto do gerente. */
    private long vendaGravada() {
        jdbc.update("INSERT INTO caixas (usuario_id, suprimento_inicial) VALUES (?, 0)", idGerente);
        long caixa = jdbc.queryForObject("SELECT max(id) FROM caixas", Long.class);
        jdbc.update("INSERT INTO produtos (sku, nome, preco_venda, estoque_atual) VALUES ('DB-V', 'Item', 10.00, 5)");
        long produto = jdbc.queryForObject("SELECT id FROM produtos WHERE sku = 'DB-V'", Long.class);
        long venda = jdbc.queryForObject("""
                INSERT INTO vendas (usuario_id, cliente_id, caixa_id, valor_subtotal, valor_total, modalidade_pagamento,
                                    percentual_comissao, valor_comissao)
                VALUES (?, 1, ?, 20.00, 20.00, 'PIX', 0, 0) RETURNING id""", Long.class, idGerente, caixa);
        jdbc.update("""
                INSERT INTO itens_venda (venda_id, produto_id, quantidade, preco_unitario, subtotal)
                VALUES (?, ?, 2, 10.00, 20.00)""", venda, produto);
        return venda;
    }
}
