# PDV Web — Sistema de Ponto de Venda

Sistema de ponto de venda (PDV) web para o varejo: vendas com controle de estoque em tempo real, caixa com abertura,
sangria, suprimento e fechamento, cupons e descontos com autorização do gerente, comissões por vendedor e auditoria de
todas as ações sensíveis.

| Pasta | Conteúdo |
|---|---|
| [`pdv-web-api/`](pdv-web-api/README.md) | **Backend** — API REST (Java 25, Spring Boot 4.1, PostgreSQL 17), casos de uso UC01–UC16, testes automatizados e ambiente Docker |
| [`documentacao_atualizada/`](documentacao_atualizada/README.md) | **Documentação** — documento geral do projeto, requisitos, casos de uso, DER, dicionário de dados, tecnologias e documentação de testes (PDF) |

Comece pelo [documento geral do projeto](documentacao_atualizada/PDV-Web-Documentacao-Geral.pdf).

## Execução rápida

```bash
cd pdv-web-api
bash demo.sh                 # Windows: demo.cmd — API com dados de exemplo em http://localhost:8080/swagger-ui.html
./mvnw clean verify          # toda a bateria de testes + cobertura
```

Produção com Docker: veja [pdv-web-api/docker/README.md](pdv-web-api/docker/README.md).

## Segurança

Nenhuma senha ou chave fica no código: a configuração de produção vem de variáveis de ambiente (modelo em
[`pdv-web-api/.env.exemplo`](pdv-web-api/.env.exemplo); o `.env` real não é versionado). As credenciais que aparecem em
`src/test` e no modo demonstração são fictícias e valem só para testes locais. Os CPFs usados são sintéticos.

## Autor

**hugoengineer** — Hugo de Oliveira Guimarães
