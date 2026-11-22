-- =====================================================================
-- V3 - Robustez de segurança
--   * tokens_revogados: logout/renovação passam a valer mesmo após reinício da API
--     e com várias instâncias (antes ficavam só em memória).
--   * tentativas_acesso: limite de tentativas de login (por CPF e por IP) e de
--     senha de gerente na autorização de desconto, contra força bruta.
-- =====================================================================

CREATE TABLE tokens_revogados (
    jti         VARCHAR(64) PRIMARY KEY,
    expira_em   TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- A limpeza periódica apaga os registros cujo token já expirou
CREATE INDEX ix_tokens_revogados_expira ON tokens_revogados (expira_em);

CREATE TABLE tentativas_acesso (
    -- Ex.: "cpf:52998224725", "ip:10.0.0.7", "pin:15"
    chave         VARCHAR(80) PRIMARY KEY,
    falhas        INTEGER     NOT NULL DEFAULT 0 CHECK (falhas >= 0),
    janela_inicio TIMESTAMPTZ NOT NULL,
    bloqueado_ate TIMESTAMPTZ
);
