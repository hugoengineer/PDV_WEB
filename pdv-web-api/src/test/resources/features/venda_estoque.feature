# language: pt
@VAL-01 @VAL-04 @UC04 @UC05
Funcionalidade: Validação de estoque na frente de caixa
  Como vendedor na frente de caixa
  Quero que o sistema confira o estoque antes de concluir a venda
  Para não vender produtos que não existem fisicamente na loja

  Contexto:
    Dado que o vendedor "Vendedor 1" está autenticado com o caixa aberto

  # Tabela "Exemplos de Validação (Casos Reais do PDV)" - Regras de Sistema, seção 3
  Esquema do Cenário: BDD-EST-01 Venda conforme o saldo em estoque - <produto>
    Dado que o produto "<codigo>" "<produto>" tem <estoque> unidades em estoque e mínimo de <minimo>
    Quando o vendedor registra a venda de <quantidade> unidades do produto "<codigo>"
    Então a API deve responder com o status <status>
    E o estoque do produto "<codigo>" deve ser de <estoque_final> unidades
    E o alerta de estoque crítico do produto "<codigo>" deve estar "<alerta>"

    Exemplos:
      | codigo  | produto    | estoque | minimo | quantidade | status | estoque_final | alerta    |
      | PROD-01 | Camiseta P | 10      | 2      | 2          | 201    | 8             | desligado |
      | PROD-02 | Tênis 41   | 1       | 0      | 3          | 422    | 1             | desligado |
      | PROD-03 | Meia       | 3       | 2      | 1          | 201    | 2             | ligado    |

  Cenário: BDD-EST-02 Mensagem de saldo insuficiente informa o estoque atual
    Dado que o produto "PROD-02" "Tênis 41" tem 1 unidades em estoque e mínimo de 0
    Quando o vendedor registra a venda de 3 unidades do produto "PROD-02"
    Então a API deve responder com o status 422
    E o motivo do erro deve ser "Estoque atual é 1, mas foi solicitado 3."
    E nenhuma venda deve ter sido gravada
