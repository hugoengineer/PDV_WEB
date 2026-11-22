# language: pt
@UC06 @UC07 @VAL-02
Funcionalidade: Cupom de desconto e desconto direto do gerente
  Como dono da loja
  Quero que descontos só sejam concedidos por cupom aprovado ou com autorização do gerente
  Para evitar descontos indiscriminados (risco de inoperabilidade financeira)

  Contexto:
    Dado que o vendedor "Vendedor 1" está autenticado com o caixa aberto
    E que o produto "PROD-ELE-001" "Teclado Mecânico RGB" tem 15 unidades em estoque e mínimo de 3
    E que existe o cupom vigente "DESCONTO10" de 10 por cento

  # Tabela de decisão: perfil x cupom x desconto direto x autorização
  Esquema do Cenário: BDD-DES <regra>
    Quando o <operador> registra a venda de 1 unidade do produto "PROD-ELE-001" com cupom "<cupom>", desconto direto de <desconto> e autorização "<autorizacao>"
    Então a API deve responder com o status <status>
    E o valor total da venda deve ser "<total>"

    Exemplos:
      | regra                                           | operador | cupom      | desconto | autorizacao       | status | total  |
      | R1 sem descontos                                | vendedor |            | 0.00     | nenhuma           | 201    | 250.00 |
      | R2 somente cupom                                | vendedor | DESCONTO10 | 0.00     | nenhuma           | 201    | 225.00 |
      | R3 desconto direto sem senha do gerente         | vendedor |            | 10.00    | nenhuma           | 403    | -      |
      | R4 desconto direto com senha incorreta          | vendedor |            | 10.00    | senha incorreta   | 403    | -      |
      | R5 desconto direto com senha do gerente         | vendedor |            | 10.00    | senha do gerente  | 201    | 240.00 |
      | R6 cupom + desconto direto (exemplo do contrato)| vendedor | DESCONTO10 | 10.00    | senha do gerente  | 201    | 215.00 |
      | R7 gerente aplica cupom + desconto direto       | gerente  | DESCONTO10 | 10.00    | nenhuma           | 201    | 215.00 |
      | R8 desconto maior que o valor restante          | gerente  | DESCONTO10 | 225.01   | nenhuma           | 422    | -      |
