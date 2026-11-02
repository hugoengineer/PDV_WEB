# PDV Web API

Backend RESTful do **Sistema de Ponto de Venda (PDV) Web**, implementando o contrato da
Documentação da API v2.3 e os casos de uso UC01–UC16.

| Item | Valor |
|---|---|
| Linguagem / framework | Java 25 · Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation) |
| Banco | PostgreSQL 17 · migrações Flyway (`src/main/resources/db/migration`) |
| Autenticação | JWT HS256 (24 h) · senhas BCrypt · perfis `VENDEDOR` e `GERENTE` |
| Erros | RFC 7807 (`application/problem+json`) com `invalid_params` |
| JSON | `snake_case`, datas no fuso `-03:00` |
| Documentação do projeto | [documentacao_atualizada/](../documentacao_atualizada/README.md) |
| Documentação de testes | [documentacao_atualizada/testes/](../documentacao_atualizada/testes/) |

## Executar localmente

Pré-requisitos: JDK 25 e um PostgreSQL acessível. O Maven não precisa estar instalado: o projeto usa o Maven Wrapper.

```bash
createdb pdv_web                       # banco vazio; o Flyway cria as tabelas

export DB_URL=jdbc:postgresql://localhost:5432/pdv_web
export DB_USER=postgres
export DB_PASSWORD=...
export PDV_JWT_SEGREDO="$(openssl rand -base64 48)"   # mínimo de 32 caracteres
export PDV_ADMIN_CPF=...               # CPF do gerente inicial
export PDV_ADMIN_SENHA=...             # mínimo de 8 caracteres

./mvnw spring-boot:run                 # Windows: mvnw.cmd spring-boot:run
```

A API sobe em `http://localhost:8080`. Saúde: `GET /actuator/health`.
Sem `PDV_JWT_SEGREDO` válido a aplicação **não inicia** (falha proposital, para não rodar com chave fraca).

Variáveis opcionais:

| Variável | Padrão | Efeito |
|---|---|---|
| `PDV_COMISSAO_BASE` | `LIQUIDO` | Base da comissão: `LIQUIDO` (total após descontos, como no exemplo da API v2.3) ou `BRUTO` (subtotal) — ADR D-04 |
| `PDV_EXIGIR_HTTPS` | `false` | `true` redireciona HTTP para HTTPS e envia HSTS. Atrás de proxy/balanceador, ele deve repassar `X-Forwarded-Proto` |
| `PDV_DOCUMENTACAO_API` | `false` | `true` publica o Swagger UI (`/swagger-ui.html`) e o OpenAPI (`/v3/api-docs`) |

### Modo demonstração (sem instalar banco)

```bash
bash demo.sh          # Windows: demo.cmd
```

Sobe a API com PostgreSQL embarcado e dados de exemplo em `http://localhost:8080/swagger-ui.html`.
Gerente `111.444.777-35` / `Gerente@Demo1`; vendedor `529.982.247-25` / `Vendedor@123`; cupom `DESCONTO10`.
Os dados somem ao encerrar. Usa só configuração de teste (`src/test`), nunca entra no pacote de produção.

### Proteções de robustez

- **Limite de tentativas:** 5 senhas erradas por CPF ou 20 por IP em 15 min bloqueiam o login por 15 min (429 com `Retry-After`).
  A senha do gerente no desconto direto tem o mesmo limite, por vendedor. Bloqueios são auditados. Ajustável em `pdv.seguranca.tentativas`.
- **Logout e renovação persistentes:** tokens revogados ficam no banco; valem após reinício e entre várias instâncias.
- **Aquecimento:** a prontidão (`/actuator/health/readiness`) só fica pronta depois de abrir o pool de conexões e executar as consultas principais.
- **Desligamento gracioso:** requisições em andamento terminam (até 20 s) antes de a aplicação parar.

### Docker (alvo AWS EC2)

```bash
bash scripts/criar-env.sh 11144477735   # gera o .env com senhas fortes (CPF do gerente inicial)
docker compose up -d --build            # PostgreSQL 17 + API + backup automático
```

O banco sobe com papéis separados (a API usa `pdv_app`, sem permissão de alterar a estrutura; as migrações usam
`pdv_migracao`), checksums de dados, backup diário verificado e porta 5432 só em `127.0.0.1`.
Operação, backup e restauração: [docker/README.md](docker/README.md).

> O ambiente Docker foi executado e validado localmente pelo roteiro RT-DEP (banco, API, backup, restauração e
> proxy HTTPS). Falta a implantação na AWS.

## Endpoints

| Recurso | Rotas | Perfis |
|---|---|---|
| Autenticação | `POST /auth/login` · `POST /auth/refresh-token` · `POST /auth/logout` · `PATCH /auth/alterar-senha` | público / autenticado |
| Produtos | `GET /produtos[/{id}]` | Vendedor, Gerente |
| | `POST` · `PUT` · `PATCH` · `DELETE /produtos[/{id}]` | Gerente |
| Cupons | `GET /cupons[/{id}]` | Vendedor, Gerente |
| | `POST` · `PUT` · `PATCH` · `DELETE /cupons[/{id}]` | Gerente |
| Vendas | `POST /vendas` · `GET /vendas[/{id}]` · `GET /vendas/comissoes` | Vendedor (próprias), Gerente |
| | `POST /vendas/{id}/cancelar` | Gerente |
| Caixa | `GET /caixas/atual` · `GET /caixas/{id}` · `POST /caixas/abertura` · `POST /caixas/{id}/fechamento` | Vendedor, Gerente |
| | `POST /caixas/{id}/sangria` · `POST /caixas/{id}/suprimento` | Gerente |
| Clientes | `POST` · `GET` · `PUT` · `PATCH /clientes[/{id}]` | Vendedor, Gerente |
| | `DELETE /clientes/{id}` | Gerente |
| Usuários | `/usuarios/**` | Gerente |
| Auditoria | `GET /auditoria` | Gerente |

Exemplo do contrato (seção 3.4), coberto pelo teste `CT-VEN-004`:

```http
POST /vendas
Authorization: Bearer <token do vendedor>

{ "caixa_id": 12, "cliente_id": 3, "cupom_codigo": "DESCONTO10",
  "desconto_direto_gerente": 10.00, "senha_autorizacao_gerente": "...",
  "modalidade_pagamento": "PIX",
  "itens": [{ "produto_id": 104, "quantidade": 1 }] }

201 Created
{ "venda_id": 89, "valor_subtotal": 250.00, "desconto_cupom": 25.00, "desconto_gerente": 10.00,
  "valor_total": 215.00, "modalidade_pagamento": "PIX", "status": "CONCLUIDA",
  "comissao_vendedor": 10.75, "alertas_estoque_critico": [] }
```

## Testes

Os testes de integração usam um **PostgreSQL real embarcado** (binários oficiais via Zonky): não exigem Docker
nem banco instalado, e nunca tocam o banco de desenvolvimento.

| Comando | O que executa |
|---|---|
| `./mvnw test` | Toda a bateria: unitários, WebMvc, integração, concorrência, desempenho e BDD |
| `./mvnw verify` | Tudo acima + verificação de cobertura (mín. 80% linhas / 70% ramos) |
| `./mvnw test -Dtest='*Test'` | Somente unitários e de contrato HTTP (rápidos) |
| `./mvnw test -Dtest='*IT'` | Somente integração |
| `./mvnw test -Dtest=CucumberSuite` | Somente cenários BDD (Gherkin) |
| `./mvnw -P mutacao test-compile org.pitest:pitest-maven:mutationCoverage` | Teste de mutação (PIT) |
| `bash demo.sh` (Windows: `demo.cmd`) | Sobe a API em modo demonstração (porta 8080) |
| `./mvnw surefire-report:report-only` | Relatório HTML dos testes |

Relatórios gerados em `target/`:

| Relatório | Caminho |
|---|---|
| Resultados (HTML) | `target/reports/surefire.html` |
| Cobertura (JaCoCo) | `target/site/jacoco/index.html` |
| Cenários BDD | `target/cucumber/relatorio.html` |
| Mutação (PIT) | `target/pit-reports/index.html` |
| Tempos de resposta | `target/desempenho.csv` |

## Estrutura

```
src/main/java/br/com/empresa/pdv/
├── auth/        UC01 login, refresh, logout, troca de senha
├── usuario/     UC13 funcionários e comissões; gerente inicial
├── produto/     UC09/UC12 catálogo e estoque
├── cliente/     UC08 clientes PF/PJ
├── cupom/       UC14 cupons
├── caixa/       UC02/UC03/UC15 caixa, sangria e suprimento
├── venda/       UC04–UC07, UC10, UC11 vendas, descontos, comissões, cancelamento
├── auditoria/   UC16 e Regra 1.2 (LOG)
├── seguranca/   JWT, RBAC, revogação de tokens, limite de tentativas
└── comum/       erros RFC 7807, paginação, dinheiro, CPF/CNPJ, relógio, OpenAPI, aquecimento
```

## Limitações conhecidas

- **Alerta de estoque crítico:** é exposto como dado (`alerta_estoque_critico`, `alertas_estoque_critico`). O envio
  em tempo real (SSE/WebSocket) depende do front-end.
- **Reserva de estoque no carrinho:** não implementada (estoque tripartido, ADR-001 D-02).
- **HTTPS:** a API força HTTPS com `PDV_EXIGIR_HTTPS=true`, mas o certificado TLS fica no proxy/balanceador do ambiente (PEND-07).
- **AWS:** implantação e teste de carga no ambiente alvo ainda não executados (PEND-04).
