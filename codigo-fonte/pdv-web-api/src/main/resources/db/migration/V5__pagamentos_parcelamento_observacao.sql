-- =====================================================================
-- V5 — Ciclo 3: várias formas de pagamento por venda, parcelamento sem juros
-- configurado pelo gerente, observação na venda e encerramento de sessões
-- quando o gerente troca a senha ou o CPF (login) de um funcionário.
-- =====================================================================

-- 1) Observação livre do pedido (opcional). Imutável como o resto da venda.
ALTER TABLE vendas ADD COLUMN observacao VARCHAR(500);

-- 2) A venda passa a aceitar "MULTIPLA" quando o pagamento foi dividido em mais de uma forma.
ALTER TABLE vendas DROP CONSTRAINT vendas_modalidade_pagamento_check;
ALTER TABLE vendas ADD CONSTRAINT ck_vendas_modalidade_pagamento
    CHECK (modalidade_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'CREDIARIO', 'MULTIPLA'));

-- 3) Pagamentos da venda: uma linha por forma de pagamento usada.
--    A soma dos valores é sempre igual ao valor_total da venda (conferido na API, dentro da mesma transação).
CREATE TABLE venda_pagamentos (
    id                   BIGSERIAL PRIMARY KEY,
    venda_id             BIGINT        NOT NULL REFERENCES vendas (id),
    modalidade_pagamento VARCHAR(15)   NOT NULL
                         CHECK (modalidade_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'CREDIARIO')),
    valor                NUMERIC(10,2) NOT NULL CHECK (valor >= 0),
    parcelas             SMALLINT      NOT NULL DEFAULT 1 CHECK (parcelas BETWEEN 1 AND 12),
    -- Só crédito e crediário parcelam; as demais formas são sempre à vista
    CONSTRAINT ck_venda_pagamentos_parcelas CHECK (parcelas = 1 OR modalidade_pagamento IN ('CARTAO_CREDITO', 'CREDIARIO'))
);
CREATE INDEX ix_venda_pagamentos_venda ON venda_pagamentos (venda_id);

-- Vendas anteriores: um pagamento à vista com o valor total, na forma registrada na venda
INSERT INTO venda_pagamentos (venda_id, modalidade_pagamento, valor, parcelas)
SELECT id, modalidade_pagamento, valor_total, 1 FROM vendas;

-- Pagamentos são só inclusão, como os itens da venda
CREATE TRIGGER tg_venda_pagamentos_imutavel
    BEFORE UPDATE OR DELETE ON venda_pagamentos
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_alteracao();

-- 4) Limite de parcelas por forma de pagamento, definido pelo gerente (1 = sem parcelamento).
CREATE TABLE configuracao_parcelamento (
    modalidade_pagamento VARCHAR(15) PRIMARY KEY CHECK (modalidade_pagamento IN ('CARTAO_CREDITO', 'CREDIARIO')),
    max_parcelas         SMALLINT    NOT NULL CHECK (max_parcelas BETWEEN 1 AND 12),
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_por       BIGINT      REFERENCES usuarios (id)
);
INSERT INTO configuracao_parcelamento (modalidade_pagamento, max_parcelas) VALUES
    ('CARTAO_CREDITO', 12),
    ('CREDIARIO', 12);

-- 5) A observação também fica protegida contra alteração depois do registro
CREATE OR REPLACE FUNCTION fn_proteger_venda() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Vendas não podem ser apagadas (use o cancelamento).'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF (NEW.usuario_id, NEW.cliente_id, NEW.caixa_id, NEW.cupom_id, NEW.gerente_autorizador_id,
        NEW.valor_subtotal, NEW.desconto_cupom, NEW.desconto_gerente, NEW.valor_total,
        NEW.modalidade_pagamento, NEW.percentual_comissao, NEW.valor_comissao, NEW.data_venda, NEW.observacao)
       IS DISTINCT FROM
       (OLD.usuario_id, OLD.cliente_id, OLD.caixa_id, OLD.cupom_id, OLD.gerente_autorizador_id,
        OLD.valor_subtotal, OLD.desconto_cupom, OLD.desconto_gerente, OLD.valor_total,
        OLD.modalidade_pagamento, OLD.percentual_comissao, OLD.valor_comissao, OLD.data_venda, OLD.observacao) THEN
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

-- 6) Versão das sessões: o token guarda a versão da emissão. Quando o gerente troca a senha ou o CPF
--    do funcionário, a versão sobe e todos os tokens anteriores dele deixam de valer.
ALTER TABLE usuarios ADD COLUMN versao_sessao INTEGER NOT NULL DEFAULT 0 CHECK (versao_sessao >= 0);
