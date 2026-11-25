// PDV Web — front-end básico (HTML + JavaScript, sem framework) servido pela própria API em /app/.
// Cada operação mostra uma mensagem de retorno: verde quando a API aceita, vermelha com o motivo quando recusa.
"use strict";

const $ = seletor => document.querySelector(seletor);
const sessao = {
  get token() { return sessionStorage.getItem("pdv.token"); },
  get usuario() { return JSON.parse(sessionStorage.getItem("pdv.usuario") || "null"); },
  salvar(token, usuario) { sessionStorage.setItem("pdv.token", token); sessionStorage.setItem("pdv.usuario", JSON.stringify(usuario)); },
  limpar() { sessionStorage.removeItem("pdv.token"); sessionStorage.removeItem("pdv.usuario"); },
};
const ehGerente = () => sessao.usuario?.perfil === "GERENTE";

// ------------------------------------------------------------------ utilidades
const esc = valor => String(valor ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const moeda = valor => valor == null ? "—" : Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const dataHora = valor => valor ? new Date(valor).toLocaleString("pt-BR") : "—";
const soDigitos = valor => String(valor || "").replace(/\D/g, "");
const cpfFormatado = cpf => soDigitos(cpf).replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
const numero = valor => valor === "" || valor == null ? null : Number(String(valor).replace(",", "."));

/** Lê os campos de um formulário como objeto; campos vazios são omitidos. */
function dadosDoFormulario(form) {
  const dados = {};
  for (const [nome, valor] of new FormData(form)) if (String(valor).trim() !== "") dados[nome] = String(valor).trim();
  return dados;
}

class ErroApi extends Error {
  constructor(status, corpo) {
    super(corpo?.detail || `HTTP ${status}`);
    this.status = status;
    this.corpo = corpo || {};
  }
}

/** Chama a API com o token da sessão e devolve o JSON; respostas 4xx/5xx viram ErroApi (RFC 7807). */
async function api(metodo, caminho, corpo) {
  const cabecalhos = { "Accept": "application/json" };
  if (corpo !== undefined) cabecalhos["Content-Type"] = "application/json";
  if (sessao.token) cabecalhos["Authorization"] = `Bearer ${sessao.token}`;
  let resposta;
  try {
    resposta = await fetch(caminho, { method: metodo, headers: cabecalhos, body: corpo === undefined ? undefined : JSON.stringify(corpo) });
  } catch {
    throw new ErroApi(0, { title: "Sem conexão", detail: "Não foi possível falar com o servidor. Verifique se a API está no ar." });
  }
  const texto = await resposta.text();
  const json = texto ? JSON.parse(texto) : null;
  if (!resposta.ok) {
    // token expirado ou revogado: volta para o login
    if (resposta.status === 401 && caminho !== "/auth/login") { sessao.limpar(); mostrarLogin(); }
    throw new ErroApi(resposta.status, json);
  }
  return json;
}

// ------------------------------------------------------------------ mensagens de retorno
function mensagem(alvo, tipo, titulo, detalhe, itens = [], http = "") {
  const lista = itens.length ? `<ul>${itens.map(i => `<li>${esc(i)}</li>`).join("")}</ul>` : "";
  $(alvo).innerHTML = `<div class="mensagem ${tipo}" role="${tipo === "erro" ? "alert" : "status"}">`
    + `<b>${esc(titulo)}</b>${detalhe ? `<span>${esc(detalhe)}</span>` : ""}${lista}`
    + `${http ? `<div class="http">${esc(http)}</div>` : ""}</div>`;
}
const sucesso = (titulo, detalhe, itens, http) => mensagem("#aviso", "sucesso", titulo, detalhe, itens, http);
const limparAviso = () => { $("#aviso").innerHTML = ""; };

/** Mostra o erro devolvido pela API: título, detalhe, campos inválidos e o código HTTP. */
function erro(e, alvo = "#aviso") {
  if (!(e instanceof ErroApi)) { console.error(e); mensagem(alvo, "erro", "Erro inesperado", e.message); return; }
  const campos = (e.corpo.invalid_params || []).map(p => `${p.name}: ${p.reason}`);
  mensagem(alvo, "erro", e.corpo.title || "Operação recusada", e.corpo.detail, campos,
    e.status ? `HTTP ${e.status}${e.corpo.type ? ` · ${e.corpo.type}` : ""}` : "");
}

/** Executa uma ação de formulário mostrando sucesso ou erro. */
async function executar(acao) {
  limparAviso();
  try { await acao(); } catch (e) { erro(e); }
  window.scrollTo({ top: 0 });
}

function tabela(colunas, linhas, vazio = "Nenhum registro encontrado.") {
  if (!linhas.length) return `<div class="tabela"><div class="vazio">${vazio}</div></div>`;
  return `<div class="tabela"><table><thead><tr>${colunas.map(c => `<th class="${c.classe || ""}">${c.titulo}</th>`).join("")}</tr></thead>`
    + `<tbody>${linhas.map(l => `<tr>${colunas.map(c => `<td class="${c.classe || ""}">${c.valor(l)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
}
const tag = (texto, tipo) => `<span class="tag ${tipo}">${esc(texto)}</span>`;
const ativo = ativo => ativo ? tag("Ativo", "ok") : tag("Inativo", "erro");

// ------------------------------------------------------------------ tema claro / escuro
const temaAtual = () => document.documentElement.dataset.tema
  || (window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "escuro" : "claro");
function aplicarTema(tema) {
  document.documentElement.dataset.tema = tema;
  document.querySelectorAll("[data-alternar-tema]").forEach(b => { b.textContent = tema === "escuro" ? "Modo claro" : "Modo escuro"; });
}
document.querySelectorAll("[data-alternar-tema]").forEach(b => b.addEventListener("click", () => {
  const novo = temaAtual() === "escuro" ? "claro" : "escuro";
  try { localStorage.setItem("pdv.tema", novo); } catch { /* armazenamento indisponível: vale só nesta página */ }
  aplicarTema(novo);
}));
aplicarTema(temaAtual());

// ------------------------------------------------------------------ login e navegação
function mostrarLogin() {
  document.body.classList.remove("gerente");
  $("#tela-app").hidden = true;
  $("#tela-login").hidden = false;
  $("#login-cpf").focus();
}

$("#form-login").addEventListener("submit", async evento => {
  evento.preventDefault();
  const dados = dadosDoFormulario(evento.target);
  $("#aviso-login").innerHTML = "";
  try {
    const resposta = await api("POST", "/auth/login", { cpf: soDigitos(dados.cpf), senha: dados.senha || "" });
    sessao.salvar(resposta.token, resposta.usuario);
    evento.target.reset();
    history.replaceState(null, "", "#/inicio"); // sem disparar hashchange, que limparia a mensagem
    iniciarApp();
    sucesso("Login realizado com sucesso", `Bem-vindo(a), ${resposta.usuario.nome}. Perfil: ${resposta.usuario.perfil}.`,
      [`Sessão válida até ${dataHora(resposta.expira_em)}`], "HTTP 200 · POST /auth/login");
  } catch (e) {
    erro(e, "#aviso-login");
  }
});

$("#botao-sair").addEventListener("click", async () => {
  try { await api("POST", "/auth/logout"); } catch { /* sessão já encerrada */ }
  sessao.limpar();
  mostrarLogin();
  mensagem("#aviso-login", "sucesso", "Sessão encerrada", "O token foi revogado no servidor.");
});

// gerente: true → tela exclusiva do gerente (o vendedor é levado ao Início se tentar abrir pelo endereço)
const TELAS = {
  inicio: { titulo: "Início", abrir: telaInicio },
  venda: { titulo: "Frente de caixa", abrir: telaNovaVenda },
  vendas: { titulo: "Vendas", abrir: telaVendas },
  caixa: { titulo: "Caixa", abrir: telaCaixa, gerente: true },
  produtos: { titulo: "Produtos", abrir: telaProdutos },
  clientes: { titulo: "Clientes", abrir: telaClientes },
  cupons: { titulo: "Cupons de desconto", abrir: telaCupons, gerente: true },
  comissoes: { titulo: "Comissões", abrir: telaComissoes },
  usuarios: { titulo: "Funcionários", abrir: telaUsuarios, gerente: true },
  configuracoes: { titulo: "Configurações", abrir: telaConfiguracoes, gerente: true },
  auditoria: { titulo: "Auditoria", abrir: telaAuditoria, gerente: true },
};

function iniciarApp() {
  const usuario = sessao.usuario;
  $("#tela-login").hidden = true;
  $("#tela-app").hidden = false;
  $("#menu-usuario").textContent = usuario.nome;
  $("#perfil-tela").textContent = usuario.perfil;
  document.body.classList.toggle("gerente", ehGerente()); // gerente usa outra cor (roxo) para diferenciar do vendedor
  document.querySelectorAll("[data-gerente]").forEach(a => { a.hidden = !ehGerente(); });
  abrirTela();
}

async function abrirTela() {
  if (!sessao.token) return mostrarLogin();
  let nome = location.hash.replace(/^#\//, "").split("?")[0] || "inicio";
  if (!TELAS[nome] || (TELAS[nome].gerente && !ehGerente())) nome = "inicio";
  const tela = TELAS[nome];
  document.querySelectorAll("#menu-links a").forEach(a => a.classList.toggle("ativo", a.getAttribute("href") === `#/${nome}`));
  $("#titulo-tela").textContent = tela.titulo;
  // cada abertura desenha num container novo: se uma tela anterior terminar de carregar depois, não sobrescreve a atual
  const el = document.createElement("div");
  el.innerHTML = `<div class="vazio">Carregando…</div>`;
  $("#tela").replaceChildren(el);
  try { await tela.abrir(el); } catch (e) { if (el.isConnected) { el.innerHTML = ""; erro(e); } }
}

window.addEventListener("hashchange", () => { limparAviso(); abrirTela(); });

// ------------------------------------------------------------------ telas
async function caixaAtual() {
  try { return await api("GET", "/caixas/atual"); } catch (e) { if (e.status === 404) return null; throw e; }
}

async function telaInicio(el) {
  const [caixa, produtos, criticos] = await Promise.all([
    caixaAtual(), api("GET", "/produtos?limite=1"), api("GET", "/produtos?estoque_critico=true&limite=50")]);
  el.innerHTML = `
    <div class="indicadores">
      <div class="indicador"><small>Caixa</small><b>${caixa ? tag("Aberto #" + caixa.caixa_id, "ok") : tag("Fechado", "alerta")}</b></div>
      <div class="indicador"><small>Vendas no turno</small><b>${caixa ? moeda(caixa.total_vendas) : "—"}</b></div>
      <div class="indicador"><small>Dinheiro em caixa</small><b>${caixa ? moeda(caixa.saldo_em_dinheiro_atual) : "—"}</b></div>
      <div class="indicador"><small>Produtos cadastrados</small><b>${produtos.total_registros}</b></div>
    </div>
    <div class="cartao"><h2>Estoque crítico</h2>
      ${tabela([
        { titulo: "SKU", valor: p => esc(p.sku) }, { titulo: "Produto", valor: p => esc(p.nome) },
        { titulo: "Estoque", classe: "num", valor: p => p.estoque_atual }, { titulo: "Mínimo", classe: "num", valor: p => p.estoque_minimo }],
        criticos.dados, "Nenhum produto com estoque abaixo do mínimo.")}
    </div>`;
}

// ---------- produtos
/** Ordenações da lista de produtos; a padrão é pelo ID. Empates sempre desempatam pelo ID. */
const porId = (a, b) => a.id - b.id;
const ORDENS_PRODUTO = {
  id: { rotulo: "ID (crescente)", comparar: porId },
  "id-desc": { rotulo: "ID (decrescente)", comparar: (a, b) => b.id - a.id },
  "estoque-asc": { rotulo: "Estoque (menor primeiro)", comparar: (a, b) => a.estoque_atual - b.estoque_atual || porId(a, b) },
  "estoque-desc": { rotulo: "Estoque (maior primeiro)", comparar: (a, b) => b.estoque_atual - a.estoque_atual || porId(a, b) },
  nome: { rotulo: "Nome (A–Z)", comparar: (a, b) => String(a.nome).localeCompare(String(b.nome), "pt-BR") || porId(a, b) },
  preco: { rotulo: "Preço (menor primeiro)", comparar: (a, b) => a.preco_venda - b.preco_venda || porId(a, b) },
};

async function telaProdutos(el) {
  const pagina = await api("GET", "/produtos?limite=100");
  el.innerHTML = `
    ${ehGerente() ? `<form id="form-produto" class="cartao" novalidate><h2>Cadastrar produto</h2><div class="grade">
      <label>SKU<input name="sku" placeholder="PROD-10"></label>
      <label>Nome<input name="nome"></label>
      <label>Fornecedor<input name="fornecedor"></label>
      <label>Unidade<input name="unidade_medida" value="UN"></label>
      <label>Código de barras<input name="codigo_barras" placeholder="8 a 14 dígitos"></label>
      <label>Preço de venda (R$)<input name="preco_venda" inputmode="decimal"></label>
      <label>Estoque atual<input name="estoque_atual" inputmode="numeric"></label>
      <label>Estoque mínimo<input name="estoque_minimo" inputmode="numeric"></label>
    </div><div class="acoes"><button class="primario">Cadastrar produto</button></div></form>` : ""}
    <div class="titulo-lista"><h2>Produtos (${pagina.total_registros})</h2>
      <label>Ordenar por<select id="ordem-produtos">
        ${Object.entries(ORDENS_PRODUTO).map(([chave, o]) => `<option value="${chave}">${o.rotulo}</option>`).join("")}
      </select></label></div>
    <div id="lista-produtos"></div>`;
  const desenharLista = () => {
    const ordem = ORDENS_PRODUTO[el.querySelector("#ordem-produtos").value] || ORDENS_PRODUTO.id;
    el.querySelector("#lista-produtos").innerHTML = tabela([
      { titulo: "ID", valor: p => p.id }, { titulo: "SKU", valor: p => esc(p.sku) }, { titulo: "Nome", valor: p => esc(p.nome) },
      { titulo: "Preço", classe: "num", valor: p => moeda(p.preco_venda) },
      { titulo: "Estoque", classe: "num", valor: p => `${p.estoque_atual} ${p.alerta_estoque_critico ? tag("crítico", "alerta") : ""}` },
      { titulo: "Situação", valor: p => ativo(p.ativo) }], [...pagina.dados].sort(ordem.comparar));
  };
  el.querySelector("#ordem-produtos").addEventListener("change", desenharLista);
  desenharLista();
  el.querySelector("#form-produto")?.addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const criado = await api("POST", "/produtos", { ...d, preco_venda: numero(d.preco_venda),
        estoque_atual: numero(d.estoque_atual), estoque_minimo: numero(d.estoque_minimo) });
      await telaProdutos(el);
      sucesso("Produto cadastrado com sucesso", `${criado.nome} (SKU ${criado.sku}) — ID ${criado.id}, preço ${moeda(criado.preco_venda)}, estoque ${criado.estoque_atual}.`, [], "HTTP 201 · POST /produtos");
    });
  });
}

// ---------- clientes
async function telaClientes(el) {
  const pagina = await api("GET", "/clientes?limite=100");
  el.innerHTML = `
    <form id="form-cliente" class="cartao" novalidate><h2>Cadastrar cliente</h2><div class="grade">
      <label>Tipo<select name="tipo"><option value="PF">Pessoa física</option><option value="PJ">Pessoa jurídica</option></select></label>
      <label>CPF/CNPJ<input name="cpf_cnpj"></label>
      <label>Nome / razão social<input name="nome_razao_social"></label>
      <label>E-mail<input name="email" type="email"></label>
      <label>Telefone<input name="telefone"></label>
      <label>CEP<input name="cep"></label>
      <label>Cidade<input name="cidade"></label>
      <label>UF<input name="uf" maxlength="2"></label>
    </div><div class="acoes"><button class="primario">Cadastrar cliente</button></div></form>
    <h2>Clientes (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "ID", valor: c => c.id }, { titulo: "Tipo", valor: c => c.tipo }, { titulo: "CPF/CNPJ", valor: c => esc(c.cpf_cnpj) },
      { titulo: "Nome / razão social", valor: c => esc(c.nome_razao_social) }, { titulo: "Cidade/UF", valor: c => esc([c.cidade, c.uf].filter(Boolean).join("/")) },
      { titulo: "Situação", valor: c => ativo(c.ativo) }], pagina.dados)}`;
  el.querySelector("#form-cliente").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const criado = await api("POST", "/clientes", { ...d, cpf_cnpj: soDigitos(d.cpf_cnpj) || d.cpf_cnpj });
      await telaClientes(el);
      sucesso("Cliente cadastrado com sucesso", `${criado.nome_razao_social} (${criado.tipo} ${criado.cpf_cnpj}) — ID ${criado.id}.`, [], "HTTP 201 · POST /clientes");
    });
  });
}

// ---------- caixa
/** Formulário de abertura de caixa (usado na aba Caixa e na Frente de caixa). */
function formAbertura(aoAbrir) {
  const form = document.createElement("form");
  form.className = "cartao";
  form.noValidate = true;
  form.innerHTML = `<h2>Abrir caixa</h2>
    <p>Nenhum caixa aberto para o seu usuário. Informe o suprimento inicial (troco) para começar o turno.</p>
    <div class="grade"><label>Suprimento inicial (R$)<input name="suprimento_inicial" inputmode="decimal"></label></div>
    <div class="acoes"><button class="primario">Abrir caixa</button></div>`;
  form.addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const r = await api("POST", "/caixas/abertura", { suprimento_inicial: numero(dadosDoFormulario(form).suprimento_inicial) });
      await aoAbrir();
      sucesso("Caixa aberto com sucesso", `Caixa #${r.caixa_id} aberto em ${dataHora(r.data_abertura)} com ${moeda(r.suprimento_inicial)} de troco.`, [], "HTTP 201 · POST /caixas/abertura");
    });
  });
  return form;
}

/** Formulário de fechamento (vendedor e gerente fecham o próprio caixa; RF-03). */
function formFechamento(caixa, aoFechar) {
  const form = document.createElement("form");
  form.className = "cartao";
  form.noValidate = true;
  form.innerHTML = `<h2>Fechar caixa #${caixa.caixa_id}</h2>
    <p>Conte o dinheiro da gaveta e informe o valor. O sistema compara com o esperado (${moeda(caixa.saldo_em_dinheiro_atual)}).</p>
    <div class="grade"><label>Valor contado em dinheiro (R$)<input name="valor_final_dinheiro" inputmode="decimal"></label></div>
    <div class="acoes"><button class="perigo">Fechar caixa</button></div>`;
  form.addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const r = await api("POST", `/caixas/${caixa.caixa_id}/fechamento`, { valor_final_dinheiro: numero(dadosDoFormulario(form).valor_final_dinheiro) });
      await aoFechar();
      const diferenca = Number(r.diferenca);
      sucesso("Caixa fechado com sucesso", `Caixa #${r.caixa_id} fechado em ${dataHora(r.data_fechamento)}.`, [
        `Total de vendas: ${moeda(r.total_vendas)} (dinheiro: ${moeda(r.total_vendas_dinheiro)})`,
        `Saldo esperado em dinheiro: ${moeda(r.saldo_esperado_dinheiro)} · contado: ${moeda(r.valor_final_dinheiro)}`,
        `Diferença: ${moeda(r.diferenca)}${diferenca === 0 ? " (caixa conferido)" : diferenca < 0 ? " (falta)" : " (sobra)"}`],
        `HTTP 200 · POST /caixas/${r.caixa_id}/fechamento`);
    });
  });
  return form;
}

/** Aba Caixa — só o gerente: resumo do turno, sangria, suprimento e fechamento (RF-15). */
async function telaCaixa(el) {
  const caixa = await caixaAtual();
  if (!caixa) {
    el.replaceChildren(formAbertura(() => telaCaixa(el)));
    return;
  }
  const movimento = tipo => `<form class="cartao" data-mov="${tipo}" novalidate><h2>${tipo === "sangria" ? "Sangria (retirada)" : "Suprimento (reforço)"}</h2>
      <div class="grade"><label>Valor (R$)<input name="valor" inputmode="decimal"></label><label>Observação<input name="observacao"></label></div>
      <div class="acoes"><button>${tipo === "sangria" ? "Registrar sangria" : "Registrar suprimento"}</button></div></form>`;
  el.innerHTML = `
    <div class="indicadores">
      <div class="indicador"><small>Caixa</small><b>#${caixa.caixa_id} ${tag(caixa.status, "ok")}</b></div>
      <div class="indicador"><small>Aberto em</small><b class="pequeno">${dataHora(caixa.data_abertura)}</b></div>
      <div class="indicador"><small>Suprimento inicial</small><b>${moeda(caixa.suprimento_inicial)}</b></div>
      <div class="indicador"><small>Total de vendas</small><b>${moeda(caixa.total_vendas)}</b></div>
      <div class="indicador"><small>Vendas em dinheiro</small><b>${moeda(caixa.total_vendas_dinheiro)}</b></div>
      <div class="indicador"><small>Sangrias</small><b>${moeda(caixa.total_sangrias)}</b></div>
      <div class="indicador"><small>Dinheiro em caixa</small><b>${moeda(caixa.saldo_em_dinheiro_atual)}</b></div>
    </div>
    ${movimento("sangria")}${movimento("suprimento")}`;
  el.append(formFechamento(caixa, () => telaCaixa(el)));
  el.querySelectorAll("[data-mov]").forEach(form => form.addEventListener("submit", evento => {
    evento.preventDefault();
    const tipo = form.dataset.mov;
    executar(async () => {
      const d = dadosDoFormulario(form);
      const r = await api("POST", `/caixas/${caixa.caixa_id}/${tipo}`, { valor: numero(d.valor), observacao: d.observacao });
      await telaCaixa(el);
      sucesso(tipo === "sangria" ? "Sangria registrada com sucesso" : "Suprimento registrado com sucesso",
        `${moeda(r.valor)} — saldo em dinheiro agora: ${moeda(r.saldo_em_dinheiro_atual)}.`, [], `HTTP 201 · POST /caixas/${caixa.caixa_id}/${tipo}`);
    });
  }));
}

// ---------- frente de caixa (registro de venda)
const FORMAS = {
  DINHEIRO: "Dinheiro", PIX: "PIX", CARTAO_CREDITO: "Cartão de crédito", CARTAO_DEBITO: "Cartão de débito",
  CREDIARIO: "Crediário", MULTIPLA: "Múltipla",
};
const centavos = valor => Math.round(Number(valor || 0) * 100);
const deCentavos = c => (c / 100).toFixed(2);
const descreverPagamento = p => `${FORMAS[p.modalidade_pagamento] || p.modalidade_pagamento} ${moeda(p.valor)}`
  + (p.parcelas > 1 ? ` em ${p.parcelas}x de ${moeda(p.valor_parcela ?? Number(p.valor) / p.parcelas)}`
    + (p.valor_ultima_parcela != null && Number(p.valor_ultima_parcela) !== Number(p.valor_parcela) ? ` (última ${moeda(p.valor_ultima_parcela)})` : "") : "");

/**
 * Frente de caixa. Vendedor e gerente abrem/fecham o próprio caixa aqui. O pedido (carrinho) fica no navegador
 * até ser finalizado: itens podem ser editados, removidos ou o pedido inteiro descartado. Depois de finalizada,
 * a venda é imutável (RF-04) e só pode ser cancelada com autorização do gerente (RF-11).
 */
async function telaNovaVenda(el) {
  const caixa = await caixaAtual();
  if (!caixa) {
    el.replaceChildren(formAbertura(() => telaNovaVenda(el)));
    return;
  }
  const [produtos, clientes, parcelamento] = await Promise.all([
    api("GET", "/produtos?limite=100"), api("GET", "/clientes?limite=100"), api("GET", "/configuracoes/parcelamento")]);
  const maxParcelas = Object.fromEntries(parcelamento.map(c => [c.modalidade_pagamento, c.max_parcelas]));
  const carrinho = [];
  const pagamentos = [];
  let totais = null;     // prévia calculada pela API (valores exatos, em reais)
  let versaoPrevia = 0;  // descarta respostas atrasadas da prévia
  const ativos = produtos.dados.filter(p => p.ativo);

  el.innerHTML = `
    <div class="faixa-caixa cartao">
      <div><b>Caixa #${caixa.caixa_id} aberto</b> desde ${dataHora(caixa.data_abertura)} · dinheiro em caixa: <b>${moeda(caixa.saldo_em_dinheiro_atual)}</b></div>
      <button type="button" id="alternar-fechamento">Fechar caixa</button>
    </div>
    <div id="area-fechamento"></div>
    <div class="cartao"><h2>Itens do pedido</h2><div class="grade">
      <label>Produto<select id="venda-produto">${ativos.map(p => `<option value="${p.id}">${esc(p.sku)} — ${esc(p.nome)} (${moeda(p.preco_venda)}, estoque ${p.estoque_atual})</option>`).join("")}</select></label>
      <label>Quantidade<input id="venda-quantidade" value="1" inputmode="numeric"></label>
    </div><div class="acoes"><button type="button" id="venda-adicionar">Adicionar item</button>
      <button type="button" class="perigo" id="venda-descartar">Descartar pedido</button></div>
      <div id="venda-carrinho" class="carrinho"></div></div>
    <form id="form-venda" class="cartao" novalidate>
      <h2>Cliente e descontos</h2><div class="grade">
        <label>Cliente<select name="cliente_id"><option value="">Consumidor padrão</option>${clientes.dados.filter(c => c.ativo && c.cpf_cnpj !== "00000000000").map(c => `<option value="${c.id}">${esc(c.nome_razao_social)}</option>`).join("")}</select></label>
        <label>Cupom<input name="cupom_codigo" placeholder="opcional"></label>
        <label>Desconto do gerente (R$)<input name="desconto_direto_gerente" inputmode="decimal" placeholder="opcional"></label>
        <label>Senha do gerente<input name="senha_autorizacao_gerente" type="password" placeholder="se houver desconto" autocomplete="off"></label>
      </div>
      <div id="venda-totais" class="totais"></div>
      <h2>Pagamento</h2>
      <p class="dica">Inclua uma ou mais formas de pagamento até o restante chegar a zero. Ex.: parte em dinheiro e o resto no cartão parcelado.</p>
      <div class="grade">
        <label>Forma<select id="pag-forma">${["DINHEIRO", "PIX", "CARTAO_CREDITO", "CARTAO_DEBITO", "CREDIARIO"].map(f => `<option value="${f}">${FORMAS[f]}</option>`).join("")}</select></label>
        <label>Valor (R$)<input id="pag-valor" inputmode="decimal"></label>
        <label id="rotulo-parcelas">Parcelas (sem juros)<select id="pag-parcelas"></select></label>
      </div>
      <div class="acoes"><button type="button" id="pag-incluir">Incluir pagamento</button></div>
      <div id="pag-lista"></div>
      <label class="observacao">Observação do pedido<textarea name="observacao" maxlength="500" rows="2" placeholder="opcional (até 500 caracteres)"></textarea></label>
      <div class="acoes finalizar"><button class="primario" id="venda-finalizar">Finalizar venda</button></div>
    </form>`;

  const $el = seletor => el.querySelector(seletor);
  const form = $el("#form-venda");

  $el("#alternar-fechamento").addEventListener("click", () => {
    const area = $el("#area-fechamento");
    if (area.firstChild) { area.replaceChildren(); return; }
    area.replaceChildren(formFechamento(caixa, () => telaNovaVenda(el)));
  });

  const totalCentavos = () => totais ? centavos(totais.valor_total) : null;
  const pagoCentavos = () => pagamentos.reduce((s, p) => s + centavos(p.valor), 0);

  const desenharParcelas = () => {
    const forma = $el("#pag-forma").value;
    const maximo = maxParcelas[forma] || 1;
    $el("#rotulo-parcelas").hidden = maximo <= 1;
    const valor = centavos(numero($el("#pag-valor").value));
    $el("#pag-parcelas").innerHTML = Array.from({ length: maximo }, (_, i) => i + 1)
      .map(n => `<option value="${n}">${n}x${valor > 0 ? ` de ${moeda(Math.floor(valor / n) / 100)}` : ""} sem juros</option>`).join("");
  };

  const desenharPagamentos = () => {
    const total = totalCentavos();
    const restante = total == null ? null : total - pagoCentavos();
    $el("#pag-lista").innerHTML = tabela([
      { titulo: "Forma", valor: p => FORMAS[p.modalidade_pagamento] },
      { titulo: "Parcelas", valor: p => p.parcelas > 1 ? `${p.parcelas}x de ${moeda(Math.floor(centavos(p.valor) / p.parcelas) / 100)}` : "à vista" },
      { titulo: "Valor", classe: "num", valor: p => moeda(p.valor) },
      { titulo: "", classe: "num", valor: (p, i) => `<button type="button" class="perigo" data-remover-pag="${pagamentos.indexOf(p)}">Remover</button>` }],
      pagamentos, "Nenhum pagamento incluído.")
      + `<div class="total ${restante === 0 ? "ok-texto" : restante < 0 ? "erro-texto" : ""}">${restante == null ? "Adicione itens para calcular o total."
        : restante > 0 ? `Falta pagar: <b>${moeda(restante / 100)}</b>`
        : restante < 0 ? `Pagamentos passam do total em <b>${moeda(-restante / 100)}</b>: remova ou ajuste um pagamento.`
        : "Pagamento completo: a soma confere exatamente com o total."}</div>`;
    if (restante > 0) $el("#pag-valor").value = deCentavos(restante);
    $el("#venda-finalizar").disabled = !(carrinho.length && restante === 0);
    desenharParcelas();
  };

  const desenharTotais = () => {
    $el("#venda-totais").innerHTML = !carrinho.length ? "" : !totais ? `<div class="total">Calculando…</div>` : `
      <div class="linha-total"><span>Subtotal</span><b>${moeda(totais.valor_subtotal)}</b></div>
      ${Number(totais.desconto_cupom) ? `<div class="linha-total"><span>Desconto do cupom</span><b>− ${moeda(totais.desconto_cupom)}</b></div>` : ""}
      ${Number(totais.desconto_gerente) ? `<div class="linha-total"><span>Desconto do gerente</span><b>− ${moeda(totais.desconto_gerente)}</b></div>` : ""}
      <div class="linha-total destaque"><span>Total a pagar</span><b>${moeda(totais.valor_total)}</b></div>`;
    desenharPagamentos();
  };

  /** Recalcula o total exato pela API (cupom e desconto incluídos) a cada mudança no pedido. */
  const atualizarTotais = async () => {
    const versao = ++versaoPrevia;
    totais = null;
    desenharTotais();
    if (!carrinho.length) return;
    const d = dadosDoFormulario(form);
    try {
      const r = await api("POST", "/vendas/previa", {
        cupom_codigo: d.cupom_codigo, desconto_direto_gerente: numero(d.desconto_direto_gerente),
        itens: carrinho.map(i => ({ produto_id: i.produto.id, quantidade: i.quantidade })) });
      if (versao !== versaoPrevia) return;
      totais = r;
      limparAviso();
    } catch (e) {
      if (versao !== versaoPrevia) return;
      erro(e);
    }
    desenharTotais();
  };

  const desenharCarrinho = () => {
    const subtotal = carrinho.reduce((s, i) => s + centavos(i.produto.preco_venda) * i.quantidade, 0);
    $el("#venda-carrinho").innerHTML = tabela([
      { titulo: "Produto", valor: i => esc(i.produto.nome) },
      { titulo: "Qtd.", classe: "num", valor: i => `<input class="qtd-item" data-item="${i.produto.id}" value="${i.quantidade}" inputmode="numeric" aria-label="Quantidade de ${esc(i.produto.nome)}">` },
      { titulo: "Unitário", classe: "num", valor: i => moeda(i.produto.preco_venda) },
      { titulo: "Subtotal", classe: "num", valor: i => moeda(centavos(i.produto.preco_venda) * i.quantidade / 100) },
      { titulo: "", classe: "num", valor: i => `<button type="button" class="perigo" data-remover="${i.produto.id}">Remover</button>` }],
      carrinho, "Nenhum item adicionado.")
      + `<div class="total">Subtotal dos itens: <b>${moeda(subtotal / 100)}</b> <small>(${carrinho.length} ${carrinho.length === 1 ? "item" : "itens"})</small></div>`;
    atualizarTotais();
  };

  $el("#venda-adicionar").addEventListener("click", () => {
    const produto = ativos.find(p => p.id === Number($el("#venda-produto").value));
    const quantidade = Number(soDigitos($el("#venda-quantidade").value));
    if (!produto || quantidade < 1) return;
    const existente = carrinho.find(i => i.produto.id === produto.id);
    if (existente) existente.quantidade += quantidade; else carrinho.push({ produto, quantidade });
    $el("#venda-quantidade").value = "1";
    desenharCarrinho();
  });
  $el("#venda-carrinho").addEventListener("change", evento => {
    const campo = evento.target.closest(".qtd-item");
    if (!campo) return;
    const item = carrinho.find(i => i.produto.id === Number(campo.dataset.item));
    const quantidade = Number(soDigitos(campo.value));
    if (item && quantidade >= 1) item.quantidade = quantidade;
    desenharCarrinho();
  });
  $el("#venda-carrinho").addEventListener("click", evento => {
    const botao = evento.target.closest("[data-remover]");
    if (!botao) return;
    carrinho.splice(carrinho.findIndex(i => i.produto.id === Number(botao.dataset.remover)), 1);
    desenharCarrinho();
  });
  $el("#venda-descartar").addEventListener("click", () => {
    if ((carrinho.length || pagamentos.length) && !confirm("Descartar o pedido? Os itens e pagamentos incluídos serão apagados.")) return;
    carrinho.length = 0;
    pagamentos.length = 0;
    form.reset();
    desenharCarrinho();
  });
  let espera;
  form.querySelectorAll("[name=cupom_codigo], [name=desconto_direto_gerente]").forEach(campo =>
    campo.addEventListener("input", () => { clearTimeout(espera); espera = setTimeout(atualizarTotais, 400); }));
  $el("#pag-forma").addEventListener("change", desenharParcelas);
  $el("#pag-valor").addEventListener("input", desenharParcelas);
  $el("#pag-incluir").addEventListener("click", () => {
    limparAviso();
    const total = totalCentavos();
    const valor = centavos(numero($el("#pag-valor").value));
    const restante = total == null ? 0 : total - pagoCentavos();
    if (total == null) return mensagem("#aviso", "alerta", "Adicione itens ao pedido", "O total precisa estar calculado antes de incluir pagamentos.");
    if (!(valor > 0)) return mensagem("#aviso", "alerta", "Valor inválido", "Informe um valor maior que zero.");
    if (valor > restante) return mensagem("#aviso", "alerta", "Valor acima do restante", `Falta pagar ${moeda(restante / 100)}; o valor informado passa disso.`);
    pagamentos.push({ modalidade_pagamento: $el("#pag-forma").value, valor: deCentavos(valor), parcelas: Number($el("#pag-parcelas").value) || 1 });
    desenharPagamentos();
  });
  $el("#pag-lista").addEventListener("click", evento => {
    const botao = evento.target.closest("[data-remover-pag]");
    if (!botao) return;
    pagamentos.splice(Number(botao.dataset.removerPag), 1);
    desenharPagamentos();
  });

  form.addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(form);
      const r = await api("POST", "/vendas", {
        caixa_id: caixa.caixa_id, cliente_id: d.cliente_id ? Number(d.cliente_id) : undefined,
        cupom_codigo: d.cupom_codigo, desconto_direto_gerente: numero(d.desconto_direto_gerente),
        senha_autorizacao_gerente: d.senha_autorizacao_gerente, observacao: d.observacao,
        pagamentos: pagamentos.map(p => ({ modalidade_pagamento: p.modalidade_pagamento, valor: Number(p.valor), parcelas: p.parcelas })),
        itens: carrinho.map(i => ({ produto_id: i.produto.id, quantidade: i.quantidade })),
      });
      await telaNovaVenda(el);
      const itens = [`Subtotal: ${moeda(r.valor_subtotal)}`];
      if (Number(r.desconto_cupom)) itens.push(`Desconto do cupom: ${moeda(r.desconto_cupom)}`);
      if (Number(r.desconto_gerente)) itens.push(`Desconto do gerente: ${moeda(r.desconto_gerente)}`);
      itens.push(`Total pago: ${moeda(r.valor_total)}`);
      r.pagamentos.forEach(p => itens.push(`Pagamento: ${descreverPagamento(p)}`));
      if (r.observacao) itens.push(`Observação: ${r.observacao}`);
      itens.push(`Comissão do vendedor: ${moeda(r.comissao_vendedor)}`);
      (r.alertas_estoque_critico || []).forEach(a => itens.push(`Alerta: ${a.nome} ficou com ${a.estoque_atual} un. (mínimo ${a.estoque_minimo})`));
      sucesso(`Venda #${r.venda_id} registrada com sucesso`, `Status ${r.status} em ${dataHora(r.data_venda)}.`, itens, "HTTP 201 · POST /vendas");
    });
  });
  desenharCarrinho();
}

// ---------- vendas
async function telaVendas(el) {
  const pagina = await api("GET", "/vendas?limite=100");
  el.innerHTML = `
    <h2>Vendas (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "Nº", valor: v => v.venda_id }, { titulo: "Data", valor: v => dataHora(v.data_venda) },
      { titulo: "Cliente", valor: v => esc(v.cliente) }, { titulo: "Vendedor", valor: v => esc(v.vendedor) },
      { titulo: "Pagamento", valor: v => FORMAS[v.modalidade_pagamento] || v.modalidade_pagamento }, { titulo: "Total", classe: "num", valor: v => moeda(v.valor_total) },
      { titulo: "Status", valor: v => tag(v.status, v.status === "CONCLUIDA" ? "ok" : "erro") },
      { titulo: "", valor: v => `<button type="button" data-ver="${v.venda_id}">Detalhes</button>` }], pagina.dados)}
    <div id="detalhe-venda"></div>`;
  el.onclick = async evento => {
    const botao = evento.target.closest("[data-ver]");
    if (!botao) return;
    try { await detalharVenda(el, Number(botao.dataset.ver)); } catch (e) { erro(e); }
  };
}

/** Detalhe da venda: itens, pagamentos (com parcelas), observação e cancelamento (RF-11). */
async function detalharVenda(el, id) {
  const v = await api("GET", `/vendas/${id}`);
  const alvo = el.querySelector("#detalhe-venda");
  const podeCancelar = v.status === "CONCLUIDA";
  alvo.innerHTML = `<div class="cartao"><h2>Venda #${v.venda_id} — ${tag(v.status, v.status === "CONCLUIDA" ? "ok" : "erro")}</h2>
    <p>${dataHora(v.data_venda)} · cliente ${esc(v.cliente)} · vendedor ${esc(v.vendedor)}</p>
    ${tabela([
      { titulo: "Produto", valor: i => esc(i.produto) }, { titulo: "Qtd.", classe: "num", valor: i => i.quantidade },
      { titulo: "Unitário", classe: "num", valor: i => moeda(i.preco_unitario) }, { titulo: "Subtotal", classe: "num", valor: i => moeda(i.subtotal) }], v.itens)}
    <div class="totais">
      <div class="linha-total"><span>Subtotal</span><b>${moeda(v.valor_subtotal)}</b></div>
      ${Number(v.desconto_cupom) ? `<div class="linha-total"><span>Desconto do cupom</span><b>− ${moeda(v.desconto_cupom)}</b></div>` : ""}
      ${Number(v.desconto_gerente) ? `<div class="linha-total"><span>Desconto do gerente</span><b>− ${moeda(v.desconto_gerente)}</b></div>` : ""}
      <div class="linha-total destaque"><span>Total</span><b>${moeda(v.valor_total)}</b></div>
    </div>
    <h2>Pagamentos</h2><ul>${v.pagamentos.map(p => `<li>${esc(descreverPagamento(p))}</li>`).join("")}</ul>
    ${v.observacao ? `<p><b>Observação:</b> ${esc(v.observacao)}</p>` : ""}
    ${v.status === "CANCELADA" ? `<p><b>Cancelada</b> em ${dataHora(v.data_cancelamento)}: ${esc(v.motivo_cancelamento)}</p>` : ""}
    </div>
    ${podeCancelar ? `<form id="form-cancelar" class="cartao" novalidate><h2>Cancelar venda #${v.venda_id}</h2>
      <p>${ehGerente() ? "O estoque dos itens volta e a venda sai dos totais do caixa e das comissões."
        : "Para cancelar, um gerente precisa digitar a senha dele abaixo (autorização)."}</p>
      <div class="grade"><label>Motivo<input name="motivo_cancelamento" placeholder="mínimo 5 caracteres"></label>
      ${ehGerente() ? "" : `<label>Senha do gerente<input name="senha_autorizacao_gerente" type="password" autocomplete="off"></label>`}</div>
      <div class="acoes"><button class="perigo">Cancelar venda</button></div></form>` : ""}`;
  alvo.scrollIntoView({ behavior: "smooth" });
  alvo.querySelector("#form-cancelar")?.addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", `/vendas/${v.venda_id}/cancelar`, { motivo_cancelamento: d.motivo_cancelamento, senha_autorizacao_gerente: d.senha_autorizacao_gerente });
      await telaVendas(el);
      sucesso("Venda cancelada com sucesso", r.mensagem, [`Venda #${r.venda_id} — status ${r.status}. O estoque dos itens foi devolvido.`], `HTTP 200 · POST /vendas/${r.venda_id}/cancelar`);
    });
  });
}

// ---------- cupons
async function telaCupons(el) {
  const pagina = await api("GET", "/cupons?limite=100");
  const hoje = new Date();
  const dia = d => d.toISOString().slice(0, 10);
  el.innerHTML = `
    <form id="form-cupom" class="cartao" novalidate><h2>Criar cupom de desconto</h2>
      <p class="dica">O vendedor aplica o cupom na Frente de caixa, sem precisar de senha.</p><div class="grade">
      <label>Código<input name="codigo" placeholder="ex.: NATAL10"></label>
      <label>Tipo<select name="tipo_desconto"><option value="PERCENTUAL">Percentual (%)</option><option value="VALOR_FIXO">Valor fixo (R$)</option></select></label>
      <label>Valor do desconto<input name="valor_desconto" inputmode="decimal"></label>
      <label>Início<input name="inicio" type="date" value="${dia(hoje)}"></label>
      <label>Término<input name="termino" type="date" value="${dia(new Date(hoje.getTime() + 30 * 864e5))}"></label>
    </div><div class="acoes"><button class="primario">Criar cupom</button></div></form>
    <h2>Cupons (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "Código", valor: c => esc(c.codigo) }, { titulo: "Tipo", valor: c => c.tipo_desconto },
      { titulo: "Desconto", classe: "num", valor: c => c.tipo_desconto === "PERCENTUAL" ? `${c.valor_desconto}%` : moeda(c.valor_desconto) },
      { titulo: "Validade", valor: c => `${dataHora(c.data_inicio_validade)} a ${dataHora(c.data_termino_validade)}` },
      { titulo: "Situação", valor: c => c.vigente ? tag("Vigente", "ok") : tag("Fora da validade", "alerta") },
      { titulo: "", valor: c => `<button type="button" class="perigo" data-inativar-cupom="${c.id}">Inativar</button>` }], pagina.dados)}`;
  el.querySelector("#form-cupom").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", "/cupons", { codigo: d.codigo, tipo_desconto: d.tipo_desconto, valor_desconto: numero(d.valor_desconto),
        data_inicio_validade: d.inicio ? `${d.inicio}T00:00:00-03:00` : null, data_termino_validade: d.termino ? `${d.termino}T23:59:59-03:00` : null });
      await telaCupons(el);
      sucesso("Cupom criado com sucesso", `${r.codigo}: ${r.tipo_desconto} de ${r.valor_desconto}.`, [], "HTTP 201 · POST /cupons");
    });
  });
  el.onclick = evento => {
    const botao = evento.target.closest("[data-inativar-cupom]");
    if (!botao || !confirm("Inativar este cupom? Ele deixa de valer nas próximas vendas.")) return;
    executar(async () => {
      await api("DELETE", `/cupons/${botao.dataset.inativarCupom}`);
      await telaCupons(el);
      sucesso("Cupom inativado", "O cupom não pode mais ser usado em novas vendas.", [], `HTTP 200 · DELETE /cupons/${botao.dataset.inativarCupom}`);
    });
  };
}

// ---------- comissões
async function telaComissoes(el) {
  const resposta = await api("GET", "/vendas/comissoes");
  const linhas = Array.isArray(resposta) ? resposta : [resposta];
  el.innerHTML = `<p>${ehGerente() ? "Comissões de todos os vendedores." : "Suas comissões acumuladas."}</p>` + tabela([
    { titulo: "Vendedor", valor: c => esc(c.vendedor_nome) }, { titulo: "Vendas", classe: "num", valor: c => c.total_vendas_realizadas },
    { titulo: "Total vendido", classe: "num", valor: c => moeda(c.valor_total_vendido) },
    { titulo: "%", classe: "num", valor: c => c.percentual_comissao }, { titulo: "Comissão", classe: "num", valor: c => moeda(c.total_comissao_acumulada) }],
    linhas.filter(Boolean));
}

// ---------- configurações (gerente)
async function telaConfiguracoes(el) {
  const lista = await api("GET", "/configuracoes/parcelamento");
  el.innerHTML = `<form id="form-parcelamento" class="cartao" novalidate><h2>Parcelamento sem juros</h2>
      <p class="dica">Escolha o número máximo de parcelas que o vendedor pode oferecer em cada forma de pagamento (1 = só à vista; limite do sistema: 12x).
      A comissão é sempre calculada sobre o valor da venda, independentemente das parcelas.</p>
      <div class="grade">${lista.map(c => `<label>${FORMAS[c.modalidade_pagamento]}<select name="${c.modalidade_pagamento}">
        ${Array.from({ length: 12 }, (_, i) => i + 1).map(n => `<option value="${n}" ${n === c.max_parcelas ? "selected" : ""}>${n === 1 ? "à vista (1x)" : `até ${n}x`}</option>`).join("")}
      </select></label>`).join("")}</div>
      <div class="acoes"><button class="primario">Salvar parcelamento</button></div></form>
    ${tabela([
      { titulo: "Forma", valor: c => FORMAS[c.modalidade_pagamento] }, { titulo: "Máximo", classe: "num", valor: c => `${c.max_parcelas}x` },
      { titulo: "Última alteração", valor: c => dataHora(c.atualizado_em) }], lista)}`;
  el.querySelector("#form-parcelamento").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const alterados = lista.filter(c => Number(d[c.modalidade_pagamento]) !== c.max_parcelas);
      for (const c of alterados) {
        await api("PUT", `/configuracoes/parcelamento/${c.modalidade_pagamento}`, { max_parcelas: Number(d[c.modalidade_pagamento]) });
      }
      await telaConfiguracoes(el);
      sucesso(alterados.length ? "Parcelamento atualizado" : "Nada mudou",
        alterados.length ? "Os vendedores já veem as novas opções na Frente de caixa." : "Os limites continuam os mesmos.",
        alterados.map(c => `${FORMAS[c.modalidade_pagamento]}: até ${d[c.modalidade_pagamento]}x`), alterados.length ? "HTTP 200 · PUT /configuracoes/parcelamento/{forma}" : "");
    });
  });
}

// ---------- funcionários (gerente)
async function telaUsuarios(el) {
  const pagina = await api("GET", "/usuarios?limite=100");
  el.innerHTML = `
    <form id="form-usuario" class="cartao" novalidate><h2>Cadastrar funcionário</h2><div class="grade">
      <label>CPF (login)<input name="cpf"></label>
      <label>Nome<input name="nome"></label>
      <label>Perfil<select name="perfil"><option>VENDEDOR</option><option>GERENTE</option></select></label>
      <label>Comissão (%)<input name="percentual_comissao" value="5" inputmode="decimal"></label>
      <label>Senha inicial<input name="senha" type="password" autocomplete="new-password"></label>
    </div><div class="acoes"><button class="primario">Cadastrar funcionário</button></div></form>
    <h2>Funcionários ativos (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "Nome", valor: u => esc(u.nome) }, { titulo: "CPF (login)", valor: u => cpfFormatado(u.cpf) },
      { titulo: "Perfil", valor: u => u.perfil }, { titulo: "Comissão", classe: "num", valor: u => `${u.percentual_comissao}%` },
      { titulo: "Situação", valor: u => ativo(u.ativo) },
      { titulo: "", valor: u => `<button type="button" data-ver-usuario="${u.id}">Ver e editar</button>` }], pagina.dados)}
    <div id="ficha-usuario"></div>`;
  el.querySelector("#form-usuario").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", "/usuarios", { ...d, cpf: soDigitos(d.cpf) || d.cpf, percentual_comissao: numero(d.percentual_comissao) });
      await telaUsuarios(el);
      sucesso("Funcionário cadastrado com sucesso", `${r.nome} (${r.perfil}) — ID ${r.id}.`, [], "HTTP 201 · POST /usuarios");
    });
  });
  // onclick (e não addEventListener): a tela é redesenhada no mesmo container e não pode acumular ouvintes
  el.onclick = evento => {
    const botao = evento.target.closest("[data-ver-usuario]");
    if (!botao) return;
    fichaFuncionario(el, pagina.dados.find(x => x.id === Number(botao.dataset.verUsuario))).catch(e => erro(e));
  };
}

/** Ficha do funcionário: dados, desempenho (vendas e comissão) e edição completa (RF-13). */
async function fichaFuncionario(el, u) {
  const alvo = el.querySelector("#ficha-usuario");
  const [comissao, vendas] = await Promise.all([
    api("GET", `/vendas/comissoes?vendedor_id=${u.id}`),
    api("GET", `/vendas?usuario_id=${u.id}&limite=5`)]);
  const ehProprio = u.id === sessao.usuario?.id;
  alvo.innerHTML = `<div class="cartao"><h2>${esc(u.nome)} — ${u.perfil}</h2>
    <div class="indicadores">
      <div class="indicador"><small>CPF (login)</small><b class="pequeno">${cpfFormatado(u.cpf)}</b></div>
      <div class="indicador"><small>Comissão atual</small><b>${u.percentual_comissao}%</b></div>
      <div class="indicador"><small>Vendas concluídas</small><b>${comissao.total_vendas_realizadas}</b></div>
      <div class="indicador"><small>Total vendido</small><b>${moeda(comissao.valor_total_vendido)}</b></div>
      <div class="indicador"><small>Comissão acumulada</small><b>${moeda(comissao.total_comissao_acumulada)}</b></div>
      <div class="indicador"><small>Última alteração</small><b class="pequeno">${dataHora(u.atualizado_em)}</b></div>
    </div>
    <h2>Últimas vendas</h2>
    ${tabela([{ titulo: "Nº", valor: v => v.venda_id }, { titulo: "Data", valor: v => dataHora(v.data_venda) },
      { titulo: "Total", classe: "num", valor: v => moeda(v.valor_total) }, { titulo: "Status", valor: v => v.status }], vendas.dados, "Nenhuma venda registrada.")}
    </div>
    <form id="form-edicao" class="cartao" novalidate><h2>Editar ${esc(u.nome)}</h2>
      <p class="dica">Trocar o CPF (login) ou definir uma nova senha encerra as sessões abertas do funcionário: ele precisa entrar de novo.
      A senha atual nunca é exibida.</p><div class="grade">
      <label>Nome<input name="nome" value="${esc(u.nome)}"></label>
      <label>CPF (login)<input name="cpf" value="${cpfFormatado(u.cpf)}"></label>
      <label>Perfil<select name="perfil" ${ehProprio ? "disabled" : ""}><option ${u.perfil === "VENDEDOR" ? "selected" : ""}>VENDEDOR</option><option ${u.perfil === "GERENTE" ? "selected" : ""}>GERENTE</option></select></label>
      <label>Comissão (%)<input name="percentual_comissao" value="${u.percentual_comissao}" inputmode="decimal"></label>
      <label>Nova senha<input name="nova_senha" type="password" autocomplete="new-password" placeholder="deixe em branco para manter"></label>
    </div><div class="acoes"><button class="primario">Salvar alterações</button>
      ${ehProprio ? "" : `<button type="button" class="perigo" id="inativar-usuario">Inativar funcionário</button>`}
      <button type="button" id="fechar-ficha">Fechar</button></div></form>`;
  alvo.scrollIntoView({ behavior: "smooth" });
  alvo.querySelector("#fechar-ficha").addEventListener("click", () => { alvo.innerHTML = ""; });
  alvo.querySelector("#form-edicao").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const mudancas = { nome: d.nome, percentual_comissao: numero(d.percentual_comissao) };
      if (!ehProprio) mudancas.perfil = d.perfil;
      if (soDigitos(d.cpf) !== u.cpf) mudancas.cpf = soDigitos(d.cpf) || d.cpf;
      if (d.nova_senha) mudancas.nova_senha = d.nova_senha;
      const r = await api("PATCH", `/usuarios/${u.id}`, mudancas);
      const avisos = [];
      if (mudancas.cpf) avisos.push(`Novo login: CPF ${cpfFormatado(r.cpf)}.`);
      if (mudancas.nova_senha) avisos.push("Senha redefinida. Informe a nova senha ao funcionário.");
      if (mudancas.cpf || mudancas.nova_senha || r.perfil !== u.perfil) avisos.push("As sessões abertas desse funcionário foram encerradas.");
      if (ehProprio && (mudancas.cpf || mudancas.nova_senha)) { sessao.limpar(); mostrarLogin(); mensagem("#aviso-login", "sucesso", "Dados alterados", "Entre novamente com o novo CPF ou senha."); return; }
      await telaUsuarios(el);
      sucesso("Funcionário atualizado com sucesso", `${r.nome}: perfil ${r.perfil}, comissão ${r.percentual_comissao}%.`, avisos, `HTTP 200 · PATCH /usuarios/${u.id}`);
    });
  });
  alvo.querySelector("#inativar-usuario")?.addEventListener("click", () => {
    if (!confirm(`Inativar ${u.nome}? O funcionário não conseguirá mais entrar no sistema.`)) return;
    executar(async () => {
      const r = await api("DELETE", `/usuarios/${u.id}`);
      await telaUsuarios(el);
      sucesso("Funcionário inativado", r.mensagem, [], `HTTP 200 · DELETE /usuarios/${u.id}`);
    });
  });
}

// ---------- auditoria
async function telaAuditoria(el) {
  const pagina = await api("GET", "/auditoria?limite=50");
  el.innerHTML = `<p>Últimas ações sensíveis registradas (${pagina.total_registros} no total).</p>` + tabela([
    { titulo: "Data", valor: a => dataHora(a.data_hora) }, { titulo: "Ação", valor: a => esc(a.tipo_acao) },
    { titulo: "Usuário (ID)", valor: a => esc(a.usuario_id ?? "—") },
    { titulo: "Registro", valor: a => esc(a.entidade ? `${a.entidade} #${a.entidade_id ?? ""}` : "—") },
    { titulo: "Detalhes", valor: a => esc(a.detalhes ?? "") }], pagina.dados);
}

// ------------------------------------------------------------------ início
if (sessao.token) iniciarApp(); else mostrarLogin();
