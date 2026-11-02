-- =====================================================================
-- PDV Web - esquema inicial (PostgreSQL)
-- Decisões de modelagem: docs/arquitetura/ADR-001-decisoes-pdv-web.md
-- =====================================================================

CREATE TABLE usuarios (
    id                  BIGSERIAL PRIMARY KEY,
    cpf                 VARCHAR(11)  NOT NULL UNIQUE,
    nome                VARCHAR(100) NOT NULL,
    senha_hash          VARCHAR(100) NOT NULL,
    perfil              VARCHAR(10)  NOT NULL DEFAULT 'VENDEDOR'
                        CHECK (perfil IN ('VENDEDOR', 'GERENTE')),
    percentual_comissao NUMERIC(5,2) NOT NULL DEFAULT 0
                        CHECK (percentual_comissao BETWEEN 0 AND 100),
    ativo               BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ
);

CREATE TABLE clientes (
    id                BIGSERIAL PRIMARY KEY,
    tipo              VARCHAR(2)   NOT NULL CHECK (tipo IN ('PF', 'PJ')),
    cpf_cnpj          VARCHAR(14)  NOT NULL UNIQUE,
    nome_razao_social VARCHAR(150) NOT NULL,
    email             VARCHAR(150),
    telefone          VARCHAR(20),
    cep               VARCHAR(8),
    endereco          VARCHAR(200),
    bairro            VARCHAR(100),
    cidade            VARCHAR(100),
    uf                VARCHAR(2),
    ativo             BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ,
    CONSTRAINT ck_clientes_documento_tipo CHECK (
        (tipo = 'PF' AND length(cpf_cnpj) = 11) OR (tipo = 'PJ' AND length(cpf_cnpj) = 14)
    )
);

CREATE TABLE produtos (
    id             BIGSERIAL PRIMARY KEY,
    sku            VARCHAR(40)   NOT NULL UNIQUE,
    nome           VARCHAR(120)  NOT NULL,
    fornecedor     VARCHAR(120),
    unidade_medida VARCHAR(10)   NOT NULL DEFAULT 'UN',
    codigo_barras  VARCHAR(14)   UNIQUE,
    ncm            VARCHAR(8),
    cest           VARCHAR(7),
    preco_venda    NUMERIC(10,2) NOT NULL CHECK (preco_venda > 0),
    -- Última barreira contra estoque negativo (VAL-01); a aplicação valida antes
    estoque_atual  INTEGER       NOT NULL DEFAULT 0 CHECK (estoque_atual >= 0),
    estoque_minimo INTEGER       NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0),
    ativo          BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ
);
CREATE INDEX ix_produtos_nome ON produtos (lower(nome));

CREATE TABLE cupons_desconto (
    id                    BIGSERIAL PRIMARY KEY,
    codigo                VARCHAR(30)   NOT NULL UNIQUE,
    tipo_desconto         VARCHAR(12)   NOT NULL CHECK (tipo_desconto IN ('PERCENTUAL', 'VALOR_FIXO')),
    valor_desconto        NUMERIC(10,2) NOT NULL CHECK (valor_desconto > 0),
    data_inicio_validade  TIMESTAMPTZ   NOT NULL,
    data_termino_validade TIMESTAMPTZ   NOT NULL,
    ativo                 BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em         TIMESTAMPTZ,
    CONSTRAINT ck_cupons_periodo CHECK (data_termino_validade > data_inicio_validade),
    CONSTRAINT ck_cupons_percentual CHECK (tipo_desconto <> 'PERCENTUAL' OR valor_desconto <= 100)
);

CREATE TABLE caixas (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT        NOT NULL REFERENCES usuarios (id),
    status                VARCHAR(7)    NOT NULL DEFAULT 'ABERTO' CHECK (status IN ('ABERTO', 'FECHADO')),
    suprimento_inicial    NUMERIC(10,2) NOT NULL CHECK (suprimento_inicial >= 0),
    data_abertura         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    data_fechamento       TIMESTAMPTZ,
    valor_final_informado NUMERIC(10,2),
    saldo_esperado        NUMERIC(10,2),
    diferenca             NUMERIC(10,2)
);
-- Um operador só pode ter um caixa aberto por vez
CREATE UNIQUE INDEX ux_caixas_um_aberto_por_usuario ON caixas (usuario_id) WHERE status = 'ABERTO';

CREATE TABLE movimentacoes_caixa (
    id                 BIGSERIAL PRIMARY KEY,
    caixa_id           BIGINT        NOT NULL REFERENCES caixas (id),
    usuario_gerente_id BIGINT        NOT NULL REFERENCES usuarios (id),
    tipo               VARCHAR(10)   NOT NULL CHECK (tipo IN ('SANGRIA', 'SUPRIMENTO')),
    valor              NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    observacao         VARCHAR(255)  NOT NULL,
    data_hora          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_movimentacoes_caixa ON movimentacoes_caixa (caixa_id);

CREATE TABLE vendas (
    id                      BIGSERIAL PRIMARY KEY,
    usuario_id              BIGINT        NOT NULL REFERENCES usuarios (id),
    cliente_id              BIGINT        NOT NULL REFERENCES clientes (id),
    caixa_id                BIGINT        NOT NULL REFERENCES caixas (id),
    cupom_id                BIGINT        REFERENCES cupons_desconto (id),
    gerente_autorizador_id  BIGINT        REFERENCES usuarios (id),
    valor_subtotal          NUMERIC(10,2) NOT NULL CHECK (valor_subtotal > 0),
    desconto_cupom          NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (desconto_cupom >= 0),
    desconto_gerente        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (desconto_gerente >= 0),
    valor_total             NUMERIC(10,2) NOT NULL CHECK (valor_total >= 0),
    modalidade_pagamento    VARCHAR(15)   NOT NULL
                            CHECK (modalidade_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'CREDIARIO')),
    status                  VARCHAR(10)   NOT NULL DEFAULT 'CONCLUIDA' CHECK (status IN ('CONCLUIDA', 'CANCELADA')),
    percentual_comissao     NUMERIC(5,2)  NOT NULL,
    valor_comissao          NUMERIC(10,2) NOT NULL,
    data_venda              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    motivo_cancelamento     VARCHAR(255),
    data_cancelamento       TIMESTAMPTZ,
    usuario_cancelamento_id BIGINT        REFERENCES usuarios (id),
    CONSTRAINT ck_vendas_total CHECK (valor_total = valor_subtotal - desconto_cupom - desconto_gerente)
);
CREATE INDEX ix_vendas_data ON vendas (data_venda);
CREATE INDEX ix_vendas_usuario ON vendas (usuario_id);
CREATE INDEX ix_vendas_caixa ON vendas (caixa_id);

CREATE TABLE itens_venda (
    id             BIGSERIAL PRIMARY KEY,
    venda_id       BIGINT        NOT NULL REFERENCES vendas (id) ON DELETE CASCADE,
    produto_id     BIGINT        NOT NULL REFERENCES produtos (id),
    quantidade     INTEGER       NOT NULL CHECK (quantidade > 0),
    preco_unitario NUMERIC(10,2) NOT NULL CHECK (preco_unitario > 0),
    subtotal       NUMERIC(10,2) NOT NULL
);
CREATE INDEX ix_itens_venda_venda ON itens_venda (venda_id);

CREATE TABLE logs_auditoria (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT        REFERENCES usuarios (id),
    data_hora      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    tipo_acao      VARCHAR(40)   NOT NULL,
    entidade       VARCHAR(40),
    entidade_id    BIGINT,
    detalhes       VARCHAR(1000) NOT NULL,
    valor_anterior TEXT,
    valor_novo     TEXT
);
CREATE INDEX ix_logs_auditoria_data ON logs_auditoria (data_hora);
CREATE INDEX ix_logs_auditoria_tipo ON logs_auditoria (tipo_acao);
