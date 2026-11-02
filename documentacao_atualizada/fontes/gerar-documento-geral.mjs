// Gera o Documento Geral do PDV Web (../PDV-Web-Documentacao-Geral.pdf) a partir do modelo documento-geral.html,
// incluindo as seções dos documentos 00 a 07 e do ADR-001. A ordem segue o Exemplo.pdf; o sumário recebe os números
// de página numa segunda passagem, lendo o PDF da primeira.
// Uso: cd documentacao_atualizada/fontes && npm install && node gerar-documento-geral.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import puppeteer from "puppeteer-core";
import { marked } from "marked";
import { getDocument } from "pdfjs-dist/legacy/build/pdf.mjs";

const FONTES = path.dirname(fileURLToPath(import.meta.url));
const SAIDA = path.resolve(FONTES, "../PDV-Web-Documentacao-Geral.pdf");
const CHROME = process.env.CHROME_PATH
  || ["C:/Program Files/Google/Chrome/Application/chrome.exe",
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
      "/usr/bin/google-chrome", "/usr/bin/chromium"].find(c => fs.existsSync(c));

const ler = arquivo => fs.readFileSync(path.resolve(FONTES, arquivo), "utf8");
const semNumero = texto => texto.replace(/^\s*\d+\.\s*/, "");

/** Nível do título que recebe o número "prefixo.k": 1.1 → h3, 1.4.2 → h4, D → h3. */
const nivelPara = prefixo => Math.min(6, Math.max(3, prefixo.split(".").length + 1));

/** Divide um corpo HTML em [preâmbulo, ...seções], cada seção começando no seu <h1>. */
function dividirEmSecoes(corpo) {
  const partes = corpo.split(/(?=<h1[\s>])/);
  return { preambulo: partes[0], secoes: partes.slice(1) };
}

/** Insere seções de um documento HTML do projeto, renumerando os títulos dentro de "prefixo". */
function incluirDocumento(especificacao, argumento) {
  const [nome, filtroBruto] = especificacao.split("#");
  const comResumo = !filtroBruto || filtroBruto.endsWith("+");
  const filtro = filtroBruto?.replace(/\+$/, "");
  const html = ler(`${nome}.html`);
  const estilos = [...html.matchAll(/<style>([\s\S]*?)<\/style>/g)].map(m => m[1]);
  let corpo = html.slice(html.indexOf("<body>") + 6, html.lastIndexOf("</body>"));
  const resumo = corpo.match(/<div class="resumo">([\s\S]*?)<\/div>/)?.[1];
  corpo = corpo.replace(/<div class="doc-cabecalho">[\s\S]*?\n<\/div>/, "")
               .replace(/<table class="meta">[\s\S]*?<\/table>/, "");

  const { secoes } = dividirEmSecoes(corpo);
  const escolhidas = filtro
    ? filtro.split(",").map(n => secoes.find(s => s.match(/^<h1[^>]*>\s*(\d+)\./)?.[1] === n))
    : secoes;
  if (escolhidas.some(s => !s)) throw new Error(`Seção inexistente em ${especificacao}`);

  const semTitulo = argumento === "sem-titulo";
  const [prefixo, inicio] = semTitulo ? ["", "1"] : argumento.split("@");
  const nivel = nivelPara(prefixo || "x");
  let saida = comResumo && resumo ? `<p class="lead">${resumo}</p>\n` : "";
  escolhidas.forEach((secao, i) => {
    const numero = `${prefixo}.${Number(inicio || 1) + i}`;
    // sem as quebras de página forçadas dos documentos avulsos: cada seção do documento geral já começa numa página nova
    secao = secao.replace(/^<h1([^>]*)>([\s\S]*?)<\/h1>/, (_, atributos, texto) =>
      semTitulo ? "" : `<h${nivel}${atributos.replace(/\s*class="quebra"/, "")}>${numero} ${semNumero(texto)}</h${nivel}>`);
    // <h2>2.1 …</h2> → <h(n+1)>prefixo.k.1 …</h(n+1)>; h3/h4 apenas descem de nível
    secao = secao.replace(/<h([2-4])([^>]*)>([\s\S]*?)<\/h\1>/g, (_, n, atributos, texto) => {
      const novo = Math.min(6, nivel + Number(n) - 1);
      if (n === "2" && !semTitulo) texto = texto.replace(/^\s*\d+\.(\d+)\s+/, `${numero}.$1 `);
      return `<h${novo}${atributos}>${texto}</h${novo}>`;
    });
    saida += secao;
  });
  return { html: saida, estilos };
}

/** Insere só as fichas escolhidas (ex.: UC01,UC04) de um documento com fichas de caso de uso. */
function incluirFichas(especificacao) {
  const [nome, ids] = especificacao.split("#");
  const fichas = [...ler(`${nome}.html`).matchAll(/<div class="ficha">[\s\S]*?<\/table><\/div><\/div>/g)].map(m => m[0]);
  return ids.split(",").map(id => {
    const ficha = fichas.find(f => f.includes(`<b>${id} —`));
    if (!ficha) throw new Error(`Ficha ${id} inexistente em ${nome}`);
    return ficha;
  }).join("\n");
}

/** Insere a primeira figura (diagrama) de um documento do projeto. */
function incluirFigura(nome) {
  const figura = ler(`${nome}.html`).match(/<figure>[\s\S]*?<\/figure>/);
  if (!figura) throw new Error(`Figura inexistente em ${nome}`);
  return figura[0];
}

/** Monta o dicionário de dados no formato do Exemplo (Campo · Tipo · Chave · Obrig. · Descrição) a partir do documento 06. */
function incluirDicionario(nome) {
  const html = ler(`${nome}.html`);
  const texto = trecho => trecho.replace(/<span class="ex">[\s\S]*?<\/span>/g, "").replace(/<[^>]+>/g, "").trim();
  const tabelas = [...html.matchAll(/<h2>[\d.]+\s*<code>([^<]+)<\/code>\s*—\s*([\s\S]*?)<\/h2>\s*<table class="dic">[\s\S]*?<tbody>([\s\S]*?)<\/tbody><\/table>/g)];
  return tabelas.map(([, tabela, descricao, corpo]) => {
    const linhas = [...corpo.matchAll(/<tr>([\s\S]*?)<\/tr>/g)].map(([, tr]) => {
      const [coluna, tipo, obrigatorio, chave, regras, desc] = [...tr.matchAll(/<td[^>]*>([\s\S]*?)<\/td>/g)].map(m => m[1]);
      const regra = texto(regras);
      return `<tr><td><b>${coluna}</b></td><td>${tipo}</td><td>${texto(chave).replace(/\s+/g, ", ")}</td>`
        + `<td>${obrigatorio.includes("✔") ? "Sim" : "Não"}</td><td>${texto(desc)}${regra ? ` <span class="muted">(${regra})</span>` : ""}</td></tr>`;
    }).join("\n");
    return `<div class="tabela-dic"><h4>Tabela: ${tabela}</h4><div class="desc-tabela">${descricao.trim()}</div>
<table><thead><tr><th style="width:21%">Campo</th><th style="width:15%">Tipo</th><th style="width:8%">Chave</th><th style="width:7%">Obrig.</th><th>Descrição</th></tr></thead>
<tbody>${linhas}</tbody></table></div>`;
  }).join("\n");
}

/** Lista dos requisitos funcionais com a descrição de cada um (formato do Exemplo), a partir da lista e das fichas do documento 01. */
function incluirRequisitos(nome) {
  const html = ler(`${nome}.html`);
  const lista = html.slice(html.indexOf("<h1>2. Lista de requisitos</h1>"));
  const linhas = [...lista.slice(0, lista.indexOf("</table>")).matchAll(/<tr><td>(RF-\d+)<\/td><td>([^<]+)<\/td><td>(UC\d+)<\/td><td>([^<]+)<\/td><td>(.*?)<\/td><\/tr>/g)];
  if (linhas.length !== 16) throw new Error(`Esperava 16 requisitos em ${nome}, achei ${linhas.length}`);
  return `<table class="lista-rf"><thead><tr><th style="width:8%">ID</th><th style="width:22%">Requisito</th><th>Descrição</th><th style="width:16%">Ator</th><th style="width:11%">Prioridade</th></tr></thead><tbody>
${linhas.map(([, id, requisito, uc, ator, prioridade]) => {
    const descricao = html.match(new RegExp(`<b>${id} — [\\s\\S]*?<tr><td>Descrição</td><td>([\\s\\S]*?)</td></tr>`))?.[1];
    if (!descricao) throw new Error(`Descrição de ${id} não encontrada`);
    return `<tr><td><b>${id}</b><br><span class="muted">${uc}</span></td><td><b>${requisito}</b></td><td>${descricao}</td><td>${ator}</td><td>${prioridade}</td></tr>`;
  }).join("\n")}</tbody></table>`;
}

// Trechos dos documentos 01 a 07 escritos antes do Ciclo 1.2 (Docker executado e validado em 28/10/2026)
const ATUALIZACOES = [
  ["(Ciclo 1, 265/265 testes)", "(Ciclo 1.2, 320/320 testes)"],
  [`<td class="warn">Pronto, não executado</td>`, `<td class="warn">Validado no Docker; AWS pendente</td>`],
];

/** Insere seções "## Título" de um Markdown, numeradas como prefixo.1, prefixo.2… */
function incluirMarkdown(especificacao, prefixo) {
  const [arquivo, titulos] = especificacao.split("#");
  const md = ler(arquivo);
  const secoes = md.split(/^(?=## )/m).slice(1);
  const nivel = nivelPara(prefixo);
  return titulos.split("|").map((titulo, i) => {
    const secao = secoes.find(s => s.startsWith(`## ${titulo}\n`));
    if (!secao) throw new Error(`Seção "${titulo}" inexistente em ${arquivo}`);
    const corpo = marked.parse(secao.replace(/^## .*\n/, ""), { gfm: true });
    return `<h${nivel}>${prefixo}.${i + 1} ${titulo}</h${nivel}>\n${corpo}`;
  }).join("\n");
}

/** Lista as entradas do sumário (títulos com data-toc) e acrescenta a cada uma uma marca invisível para achar a página. */
function marcarSumario(html) {
  const entradas = [];
  html = html.replace(/<(h[1-3])([^>]*\bdata-toc="(\d)"[^>]*)>([\s\S]*?)<\/\1>/g, (_, tag, atributos, nivel, texto) => {
    const id = atributos.match(/\bid="([^"]+)"/)[1];
    const numero = atributos.match(/\bdata-num="([^"]+)"/)?.[1] || "";
    entradas.push({ id, nivel: Number(nivel), numero, texto: texto.trim() });
    const marca = `zzPG${String(entradas.length).padStart(3, "0")}zz`;
    const prefixo = numero && tag !== "h1" ? `${numero} ` : "";
    return `<${tag}${atributos}><span class="marca">${marca}</span>${prefixo}${texto}</${tag}>`;
  });
  return { html, entradas };
}

function montarSumario(html, entradas, paginas = {}) {
  const linha = e => `<a class="n${e.nivel}" href="#${e.id}"><span class="txt"><span class="num">${e.numero}</span>${e.texto}</span>`
    + `<span class="pontos"></span><span class="pg">${paginas[e.id] ?? ""}</span></a>`;
  html = html.replace("<!--sumario-->", entradas.map(linha).join("\n"));
  // lista de seções de cada página divisória
  entradas.forEach((e, i) => {
    if (e.nivel !== 0) return;
    const filhos = [];
    for (let j = i + 1; j < entradas.length && entradas[j].nivel !== 0; j++) {
      if (entradas[j].nivel === 1) filhos.push(entradas[j]);
    }
    html = html.replace(`<!--itens:${e.id}-->`,
      filhos.map(f => `<a href="#${f.id}"><span>${f.numero}</span>${f.texto}</a>`).join("\n"));
  });
  return html;
}

async function gerarPdf(navegador, html) {
  const pagina = await navegador.newPage();
  await pagina.setContent(html, { waitUntil: "networkidle0", timeout: 60000 });
  const pdf = await pagina.pdf({
    format: "A4", printBackground: true, displayHeaderFooter: true, outline: true, tagged: true,
    headerTemplate: "<span></span>",
    footerTemplate: `<div style="font-family:Segoe UI,Arial;font-size:7px;color:#5b6573;width:100%;padding:0 15mm;display:flex;justify-content:space-between">
      <span>PDV Web · Documentação do Projeto de Software</span><span>Página <span class="pageNumber"></span> de <span class="totalPages"></span></span></div>`,
    margin: { top: "14mm", bottom: "16mm", left: "15mm", right: "15mm" },
  });
  await pagina.close();
  return pdf;
}

/** Procura, página a página, as marcas zzPGnnnzz inseridas nos títulos do sumário. */
async function localizarMarcas(pdf, entradas) {
  const documento = await getDocument({ data: new Uint8Array(pdf), verbosity: 0 }).promise;
  const paginas = {};
  for (let p = 1; p <= documento.numPages; p++) {
    const conteudo = await (await documento.getPage(p)).getTextContent();
    const texto = conteudo.items.map(item => item.str).join("");
    for (const m of texto.matchAll(/zzPG(\d{3})zz/g)) {
      const entrada = entradas[Number(m[1]) - 1];
      paginas[entrada.id] ??= p;
    }
  }
  return { paginas, total: documento.numPages };
}

// ---------------------------------------------------------------- montagem
let html = ler("documento-geral.html");
const estilosIncluidos = new Set();
html = html.replace(/<!--incluir:(\S+) (\S+)-->/g, (_, especificacao, argumento) => {
  const { html: trecho, estilos } = incluirDocumento(especificacao, argumento);
  estilos.forEach(e => estilosIncluidos.add(e));
  return trecho;
});
html = html.replace(/<!--md:(.+?) (\S+)-->/g, (_, especificacao, prefixo) => incluirMarkdown(especificacao, prefixo));
html = html.replace(/<!--fichas:(\S+)-->/g, (_, especificacao) => incluirFichas(especificacao));
html = html.replace(/<!--figura:(\S+)-->/g, (_, nome) => incluirFigura(nome));
html = html.replace(/<!--dicionario:(\S+)-->/g, (_, nome) => incluirDicionario(nome));
html = html.replace(/<!--requisitos:(\S+)-->/g, (_, nome) => incluirRequisitos(nome));
for (const [antigo, novo] of ATUALIZACOES) {
  if (!html.includes(antigo)) throw new Error(`Atualização sem efeito (o documento de origem mudou?): ${antigo}`);
  html = html.replaceAll(antigo, novo);
}
html = html.replace("<!--estilos-->", `<style>${ler("estilo.css")}\n${[...estilosIncluidos].join("\n")}</style>`);
html = html.replace("<!--data-->", new Date().toLocaleDateString("pt-BR"));
// cada documento numerava as próprias figuras a partir de 1
let figura = 0;
html = html.replace(/Figura \d+ —/g, () => `Figura ${++figura} —`);

const marcado = marcarSumario(html);
const navegador = await puppeteer.launch({ executablePath: CHROME, headless: true });
try {
  const rascunho = await gerarPdf(navegador, montarSumario(marcado.html, marcado.entradas));
  const { paginas, total } = await localizarMarcas(rascunho, marcado.entradas);
  const faltando = marcado.entradas.filter(e => !paginas[e.id]).map(e => e.id);
  if (faltando.length) throw new Error(`Página não encontrada para: ${faltando.join(", ")}`);

  const final = montarSumario(marcado.html, marcado.entradas, paginas).replace(/<span class="marca">zzPG\d{3}zz<\/span>/g, "");
  const pdf = await gerarPdf(navegador, final);
  fs.writeFileSync(SAIDA, pdf);
  console.log(`gerado: ${path.relative(path.resolve(FONTES, ".."), SAIDA)} (${total} páginas, ${marcado.entradas.length} itens no sumário)`);
} finally {
  await navegador.close();
}
