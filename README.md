# PDV Web — Sistema de Ponto de Venda

Sistema de ponto de venda (PDV) web para o varejo: vendas com controle de estoque em tempo real, caixa com abertura,
sangria, suprimento e fechamento, cupons e descontos com autorização do gerente, comissões por vendedor e auditoria de
todas as ações sensíveis.

| Pasta | Conteúdo |
|---|---|
| [`documentacao/`](documentacao/PDV-Web-Documentacao.pdf) | **Documentação** — um único PDF: motivação, requisitos funcionais e não funcionais, diagrama e especificação dos casos de uso, DER, tecnologias, endpoints (Swagger), desenvolvimento com prints da aplicação e casos de teste |
| [`codigo-fonte/pdv-web-api/`](codigo-fonte/pdv-web-api/README.md) | **Aplicação** — backend REST (Java 25, Spring Boot 4.1, PostgreSQL 17) e front-end web (HTML, CSS e JavaScript), testes automatizados e ambiente Docker |
| [`codigo-fonte/documentacao-fontes/`](codigo-fonte/documentacao-fontes/) | Fontes que geram o PDF da documentação e os prints do roteiro de uso |

Comece pelo [documento do projeto](documentacao/PDV-Web-Documentacao.pdf).

## Execução rápida

**Windows:** dê dois cliques em `codigo-fonte/pdv-web-api/demo.cmd`. O script acha o Java 25 sozinho (JAVA_HOME,
JDK do IntelliJ ou JDKs instalados), sobe a aplicação com banco embarcado e dados de exemplo e abre o navegador
na tela de login (http://localhost:8080). Para encerrar, feche a janela.

**IntelliJ:** abra a pasta `codigo-fonte/pdv-web-api`, escolha a configuração **PDV Web (demo)** e clique em ▶
(ou rode o `main` de `src/test/java/br/com/empresa/pdv/DemonstracaoPdvWebApi.java`).

**Linux/macOS/Git Bash:** `bash codigo-fonte/pdv-web-api/demo.sh`.

Testes: `./mvnw clean verify` em `codigo-fonte/pdv-web-api`.

No modo demonstração: gerente `111.444.777-35` / `Gerente@Demo1`; vendedor `529.982.247-25` / `Vendedor@123`;
Swagger em `http://localhost:8080/swagger-ui.html`.

Produção com Docker: veja [codigo-fonte/pdv-web-api/docker/README.md](codigo-fonte/pdv-web-api/docker/README.md).

## Regerar a documentação

Com o modo demonstração no ar e dados recém-criados (use `PDV_DEMO_ABRIR_NAVEGADOR=false` para não abrir o navegador):

```bash
cd codigo-fonte/documentacao-fontes
npm install
node capturar-telas.mjs        # executa o roteiro de uso no front-end e tira os prints (telas/)
node gerar-documento-geral.mjs # gera documentacao/PDV-Web-Documentacao.pdf
```

Requer Node.js e Google Chrome (ou Microsoft Edge).

## Segurança

Nenhuma senha ou chave fica no código: a configuração de produção vem de variáveis de ambiente (modelo em
[`codigo-fonte/pdv-web-api/.env.exemplo`](codigo-fonte/pdv-web-api/.env.exemplo); o `.env` real não é versionado).
As credenciais que aparecem em `src/test` e no modo demonstração são fictícias e valem só para testes locais.
Os CPFs usados são sintéticos.

## Autor

**hugoengineer** — Hugo de Oliveira Guimarães
