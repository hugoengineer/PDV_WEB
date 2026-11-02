// Gera os PDFs da pasta documentacao_atualizada a partir das fontes HTML (documentos do projeto)
// e dos Markdown de docs/testes (documentação dos testes).
// Uso: cd documentacao_atualizada/fontes && npm install && node gerar-pdfs.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import puppeteer from "puppeteer-core";
import { marked } from "marked";

const FONTES = path.dirname(fileURLToPath(import.meta.url));
const SAIDA = path.resolve(FONTES, "..");
const TESTES_MD = path.resolve(FONTES, "../../docs/testes");
const CHROME = process.env.CHROME_PATH
  || ["C:/Program Files/Google/Chrome/Application/chrome.exe",
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
      "/usr/bin/google-chrome", "/usr/bin/chromium"].find(c => fs.existsSync(c));

const MERMAID = '<script src="https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"></script>'
  + '<script>mermaid.initialize({ startOnLoad: true, theme: "neutral" });</script>';

/** Converte um Markdown de docs/testes em HTML com o mesmo visual dos demais documentos. */
function markdownParaHtml(arquivoMd, titulo) {
  let md = fs.readFileSync(arquivoMd, "utf8");
  md = md.replace(/^# .*\n/, "");                                  // o título vai para o cabeçalho
  md = md.replace(/\]\(([^)]+?)\.md(#[^)]*)?\)/g, "]($1.pdf)");      // links entre documentos
  let corpo = marked.parse(md, { gfm: true });
  corpo = corpo.replace(/<pre><code class="language-mermaid">([\s\S]*?)<\/code><\/pre>/g,
    (_, codigo) => `<figure><pre class="mermaid">${codigo}</pre></figure>`);
  return `<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><title>${titulo}</title>
<link rel="stylesheet" href="../estilo.css"></head><body>
<div class="doc-cabecalho"><div class="projeto">PDV Web · Documentação de Testes</div><div class="titulo">${titulo}</div></div>
${corpo}${corpo.includes('class="mermaid"') ? MERMAID : ""}</body></html>`;
}

async function gerar(navegador, htmlPath, pdfPath, titulo) {
  const pagina = await navegador.newPage();
  await pagina.goto(pathToFileURL(htmlPath).href, { waitUntil: "networkidle0", timeout: 60000 });
  if (await pagina.$(".mermaid")) {
    await pagina.waitForFunction(() => [...document.querySelectorAll(".mermaid")].every(e => e.querySelector("svg")),
      { timeout: 30000 });
  }
  await pagina.pdf({
    path: pdfPath, format: "A4", printBackground: true, displayHeaderFooter: true,
    headerTemplate: "<span></span>",
    footerTemplate: `<div style="font-family:Segoe UI,Arial;font-size:7px;color:#5b6573;width:100%;padding:0 15mm;display:flex;justify-content:space-between">
      <span>PDV Web · ${titulo}</span><span>Página <span class="pageNumber"></span> de <span class="totalPages"></span></span></div>`,
    margin: { top: "14mm", bottom: "16mm", left: "15mm", right: "15mm" },
  });
  await pagina.close();
  console.log("gerado:", path.relative(SAIDA, pdfPath));
}

const DOCUMENTOS = [
  ["01-requisitos-funcionais", "Requisitos Funcionais"],
  ["02-requisitos-nao-funcionais", "Requisitos Não Funcionais"],
  ["03-diagrama-casos-de-uso", "Diagrama de Casos de Uso"],
  ["04-detalhamento-casos-de-uso", "Detalhamento dos Casos de Uso"],
  ["05-diagrama-entidade-relacionamento", "Diagrama Entidade-Relacionamento"],
  ["06-dicionario-de-dados", "Dicionário de Dados"],
  ["07-tecnologias", "Tecnologias"],
  ["00-indice", "Índice da Documentação Atualizada"],
];
const TESTES = [
  ["01-plano-de-testes", "Plano de Testes"],
  ["02-especificacao-de-casos-de-teste", "Especificação de Casos de Teste"],
  ["03-cenarios-bdd", "Cenários BDD (Gherkin)"],
  ["04-matriz-de-rastreabilidade", "Matriz de Rastreabilidade"],
  ["05-relatorio-de-execucao", "Relatório de Execução de Testes"],
  ["06-relatorio-de-defeitos", "Relatório de Defeitos"],
  ["07-roteiro-de-testes-manuais", "Roteiro de Testes Manuais e Exploratórios"],
];

const somente = process.argv.slice(2);
const navegador = await puppeteer.launch({ executablePath: CHROME, headless: true });
try {
  for (const [nome, titulo] of DOCUMENTOS) {
    const html = path.join(FONTES, `${nome}.html`);
    if ((somente.length && !somente.includes(nome)) || !fs.existsSync(html)) continue;
    await gerar(navegador, html, path.join(SAIDA, `${nome}.pdf`), titulo);
  }
  if (!fs.existsSync(TESTES_MD)) {
    console.log("docs/testes não encontrado: os PDFs de testes/ ficam como estão.");
    TESTES.length = 0;
  }
  fs.mkdirSync(path.join(FONTES, "testes"), { recursive: true });
  fs.mkdirSync(path.join(SAIDA, "testes"), { recursive: true });
  for (const [nome, titulo] of TESTES) {
    if (somente.length && !somente.includes("testes") && !somente.includes(nome)) continue;
    const html = path.join(FONTES, "testes", `${nome}.html`);
    fs.writeFileSync(html, markdownParaHtml(path.join(TESTES_MD, `${nome}.md`), titulo));
    await gerar(navegador, html, path.join(SAIDA, "testes", `${nome}.pdf`), titulo);
  }
} finally {
  await navegador.close();
}
