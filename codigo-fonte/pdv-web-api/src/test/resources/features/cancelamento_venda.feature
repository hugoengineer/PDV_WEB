# language: pt
@VAL-02 @VAL-03 @UC11
Funcionalidade: Cancelamento de venda com estorno de estoque
  Como gerente da loja
  Quero cancelar vendas concluídas devolvendo os itens ao estoque
  Para corrigir operações sem perder a integridade do estoque e do caixa

  Contexto:
    Dado que o vendedor "Vendedor 1" está autenticado com o caixa aberto
    E que o produto "PROD-01" "Camiseta P" tem 10 unidades em estoque e mínimo de 2
    E o vendedor registrou a venda de 2 unidades do produto "PROD-01"

  Cenário: BDD-CAN-01 Vendedor tenta cancelar a venda
    Quando o vendedor tenta cancelar a venda com o motivo "Cliente desistiu"
    Então a API deve responder com o status 403
    E o título do erro deve ser "Acesso Negado"
    E o estoque do produto "PROD-01" deve ser de 8 unidades

  Cenário: BDD-CAN-02 Gerente cancela a venda e o estoque é estornado
    Quando o gerente cancela a venda com o motivo "Cliente desistiu"
    Então a API deve responder com o status 200
    E a venda deve estar com o status "CANCELADA"
    E o estoque do produto "PROD-01" deve ser de 10 unidades
    E deve existir 1 registro de auditoria do tipo "CANCELAMENTO_VENDA"

  Cenário: BDD-CAN-03 Venda já cancelada não pode ser cancelada novamente
    Dado que o gerente cancelou a venda com o motivo "Primeiro cancelamento"
    Quando o gerente cancela a venda com o motivo "Segundo cancelamento"
    Então a API deve responder com o status 422
    E o estoque do produto "PROD-01" deve ser de 10 unidades
