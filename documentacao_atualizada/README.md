# Documentação Atualizada — PDV Web (versão 2.0)

Comece por **`00-indice.pdf`**: ele lista cada documento, o que substitui e as incompatibilidades resolvidas.

Para uma visão completa num só arquivo, use **`PDV-Web-Documentacao-Geral.pdf`**: reúne a apresentação do projeto e os
documentos 00 a 07 e o resumo dos testes, na ordem do `Exemplo.pdf` (requisitos → projeto → qualidade e testes).

| Arquivo | Conteúdo |
|---|---|
| `PDV-Web-Documentacao-Geral.pdf` | Documento geral de apresentação do projeto (todas as informações acima num só PDF, com sumário clicável) |
| `00-indice.pdf` | Índice, situação dos documentos originais e incompatibilidades resolvidas |
| `01-requisitos-funcionais.pdf` | RF-01 a RF-16 e a numeração unificada das validações VAL-01 a VAL-08 |
| `02-requisitos-nao-funcionais.pdf` | RNF-01 a RNF-12 com metas, verificação e situação |
| `03-diagrama-casos-de-uso.pdf` | Diagrama UML com UC01–UC16 e correspondência com o diagrama anterior |
| `04-detalhamento-casos-de-uso.pdf` | Fluxos, exceções e pós-condições dos 16 casos de uso |
| `05-diagrama-entidade-relacionamento.pdf` | Modelo do banco (9 tabelas, 95 colunas, 12 chaves estrangeiras) |
| `06-dicionario-de-dados.pdf` | Todas as colunas, tipos, regras, domínios e índices |
| `07-tecnologias.pdf` | Tecnologias e versões do backend, testes, infraestrutura e do desktop legado |
| `testes/` | Plano, especificação dos casos, cenários BDD, matriz de rastreabilidade, relatórios de execução e de defeitos, roteiro manual |

## Como regenerar os PDFs

As fontes ficam em `fontes/`: os HTML dos documentos do projeto e o estilo comum. Os PDFs de `testes/` são a versão
final (os Markdown de origem não fazem parte do repositório). Requer Node.js e Google Chrome (ou Microsoft Edge) instalados.

```bash
cd documentacao_atualizada/fontes
npm install
node gerar-der.mjs      # só se o esquema do banco mudar (redesenha o diagrama ER)
node gerar-pdfs.mjs     # gera todos os PDFs
node gerar-pdfs.mjs 06-dicionario-de-dados   # ou apenas um documento
node gerar-documento-geral.mjs   # documento geral (modelo em documento-geral.html)
```
