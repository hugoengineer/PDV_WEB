# language: pt
@VAL-05 @UC02 @UC03 @UC15
Funcionalidade: Controle rigoroso de caixa
  Como gerente da loja
  Quero que vendas só aconteçam com o caixa do turno aberto e que sangrias respeitem o saldo
  Para manter o fechamento de caixa confiável

  Contexto:
    Dado que o vendedor "Vendedor 1" está autenticado sem caixa aberto
    E que o produto "PROD-01" "Camiseta P" tem 10 unidades em estoque e mínimo de 2

  Cenário: BDD-CX-01 Venda é permitida somente após a abertura do caixa
    Quando o vendedor abre o caixa com suprimento inicial de "100.00"
    E o vendedor registra a venda de 1 unidades do produto "PROD-01"
    Então a API deve responder com o status 201

  Cenário: BDD-CX-02 Caixa fechado bloqueia novas vendas
    Dado que o vendedor abriu o caixa com suprimento inicial de "100.00"
    E o vendedor fechou o caixa informando "100.00" em dinheiro
    Quando o vendedor registra a venda de 1 unidades do produto "PROD-01"
    Então a API deve responder com o status 422
    E o tipo do erro deve ser "caixa-fechado"

  Cenário: BDD-CX-03 Sangria maior que o saldo em dinheiro é rejeitada
    Dado que o vendedor abriu o caixa com suprimento inicial de "100.00"
    Quando o gerente realiza uma sangria de "100.01" no caixa do vendedor
    Então a API deve responder com o status 422
    E o tipo do erro deve ser "saldo-caixa-insuficiente"
