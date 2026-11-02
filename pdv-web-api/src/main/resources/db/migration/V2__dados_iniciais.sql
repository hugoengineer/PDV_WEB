-- Cliente genérico usado quando a venda não identifica o comprador (Regra 1.1.1.9).
-- O documento 00000000000 só é criado por esta migração: a API rejeita CPFs com dígitos repetidos.
INSERT INTO clientes (tipo, cpf_cnpj, nome_razao_social)
VALUES ('PF', '00000000000', 'CONSUMIDOR PADRÃO');
