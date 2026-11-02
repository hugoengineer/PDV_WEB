-- =====================================================================
-- V4 - Banco como última barreira (integridade e imutabilidade)
--   * Regras de consistência que antes só a aplicação garantia.
--   * Registros financeiros e de auditoria não podem ser alterados nem apagados,
--     nem por SQL direto (a aplicação só cancela vendas, nunca as edita).
--   * Índices nas chaves estrangeiras usadas em filtros e junções.
-- =====================================================================

-- ---------------------------------------------------------------- consistência

-- Venda cancelada tem motivo, data e gerente; venda concluída não tem nenhum dos três
ALTER TABLE vendas ADD CONSTRAINT ck_vendas_cancelamento CHECK (
    (status = 'CONCLUIDA' AND motivo_cancelamento IS NULL AND data_cancelamento IS NULL AND usuario_cancelamento_id IS NULL)
    OR
    (status = 'CANCELADA' AND motivo_cancelamento IS NOT NULL AND data_cancelamento IS NOT NULL AND usuario_cancelamento_id IS NOT NULL)
);
ALTER TABLE vendas ADD CONSTRAINT ck_vendas_comissao CHECK (
    percentual_comissao BETWEEN 0 AND 100 AND valor_comissao >= 0
);

-- Subtotal do item é exatamente quantidade x preço praticado
ALTER TABLE itens_venda ADD CONSTRAINT ck_itens_venda_subtotal CHECK (subtotal = quantidade * preco_unitario);

-- Caixa fechado tem data e valores de conferência; caixa aberto não tem
ALTER TABLE caixas ADD CONSTRAINT ck_caixas_fechamento CHECK (
    (status = 'ABERTO' AND data_fechamento IS NULL)
    OR
    (status = 'FECHADO' AND data_fechamento IS NOT NULL AND data_fechamento >= data_abertura)
);

ALTER TABLE clientes ADD CONSTRAINT ck_clientes_uf CHECK (uf IS NULL OR uf ~ '^[A-Z]{2}$');
ALTER TABLE clientes ADD CONSTRAINT ck_clientes_documento_digitos CHECK (cpf_cnpj ~ '^[0-9]+$');
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_cpf_digitos CHECK (cpf ~ '^[0-9]{11}$');

-- ---------------------------------------------------------------- imutabilidade

CREATE FUNCTION fn_bloquear_alteracao() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'A tabela % não permite %: registro imutável.', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

-- Auditoria (Regra 1.2): só inclusão
CREATE TRIGGER tg_logs_auditoria_imutavel
    BEFORE UPDATE OR DELETE ON logs_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

-- Itens e movimentações de caixa: só inclusão
CREATE TRIGGER tg_itens_venda_imutavel
    BEFORE UPDATE OR DELETE ON itens_venda
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();
CREATE TRIGGER tg_movimentacoes_caixa_imutavel
    BEFORE UPDATE OR DELETE ON movimentacoes_caixa
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

-- Venda: nunca é apagada; a única alteração permitida é CONCLUIDA -> CANCELADA (UC11)
CREATE FUNCTION fn_proteger_venda() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Vendas não podem ser apagadas (use o cancelamento).'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF (NEW.usuario_id, NEW.cliente_id, NEW.caixa_id, NEW.cupom_id, NEW.gerente_autorizador_id,
        NEW.valor_subtotal, NEW.desconto_cupom, NEW.desconto_gerente, NEW.valor_total,
        NEW.modalidade_pagamento, NEW.percentual_comissao, NEW.valor_comissao, NEW.data_venda)
       IS DISTINCT FROM
       (OLD.usuario_id, OLD.cliente_id, OLD.caixa_id, OLD.cupom_id, OLD.gerente_autorizador_id,
        OLD.valor_subtotal, OLD.desconto_cupom, OLD.desconto_gerente, OLD.valor_total,
        OLD.modalidade_pagamento, OLD.percentual_comissao, OLD.valor_comissao, OLD.data_venda) THEN
        RAISE EXCEPTION 'Os valores da venda % não podem ser alterados.', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.status = 'CANCELADA' AND (NEW.status, NEW.motivo_cancelamento, NEW.data_cancelamento, NEW.usuario_cancelamento_id)
       IS DISTINCT FROM (OLD.status, OLD.motivo_cancelamento, OLD.data_cancelamento, OLD.usuario_cancelamento_id) THEN
        RAISE EXCEPTION 'A venda % já foi cancelada e não pode mudar.', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_vendas_protegida
    BEFORE UPDATE OR DELETE ON vendas
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_venda();

-- Caixa fechado não reabre nem tem a conferência alterada
CREATE FUNCTION fn_proteger_caixa() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Caixas não podem ser apagados.' USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.status = 'FECHADO' AND ROW(NEW.*) IS DISTINCT FROM ROW(OLD.*) THEN
        RAISE EXCEPTION 'O caixa % já foi fechado e não pode mudar.', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_caixas_protegido
    BEFORE UPDATE OR DELETE ON caixas
    FOR EACH ROW EXECUTE FUNCTION fn_proteger_caixa();

-- ---------------------------------------------------------------- índices de chaves estrangeiras

CREATE INDEX ix_caixas_usuario ON caixas (usuario_id);
CREATE INDEX ix_movimentacoes_gerente ON movimentacoes_caixa (usuario_gerente_id);
CREATE INDEX ix_vendas_cliente ON vendas (cliente_id);
CREATE INDEX ix_vendas_cupom ON vendas (cupom_id) WHERE cupom_id IS NOT NULL;
CREATE INDEX ix_vendas_status_data ON vendas (status, data_venda);
CREATE INDEX ix_itens_venda_produto ON itens_venda (produto_id);
CREATE INDEX ix_logs_auditoria_usuario ON logs_auditoria (usuario_id);
CREATE INDEX ix_tentativas_janela ON tentativas_acesso (janela_inicio);
