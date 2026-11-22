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

const TELAS = {
  inicio: { titulo: "Início", abrir: telaInicio },
  venda: { titulo: "Nova venda", abrir: telaNovaVenda },
  vendas: { titulo: "Vendas", abrir: telaVendas },
  caixa: { titulo: "Caixa", abrir: telaCaixa },
  produtos: { titulo: "Produtos", abrir: telaProdutos },
  clientes: { titulo: "Clientes", abrir: telaClientes },
  cupons: { titulo: "Cupons de desconto", abrir: telaCupons },
  comissoes: { titulo: "Comissões", abrir: telaComissoes },
  usuarios: { titulo: "Usuários", abrir: telaUsuarios },
  auditoria: { titulo: "Auditoria", abrir: telaAuditoria },
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
  const nome = location.hash.replace(/^#\//, "").split("?")[0] || "inicio";
  const tela = TELAS[nome] || TELAS.inicio;
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
async function telaCaixa(el) {
  const caixa = await caixaAtual();
  if (!caixa) {
    el.innerHTML = `<form id="form-abertura" class="cartao" novalidate><h2>Abrir caixa</h2>
      <p>Nenhum caixa aberto para o seu usuário. Informe o suprimento inicial (troco) para começar o turno.</p>
      <div class="grade"><label>Suprimento inicial (R$)<input name="suprimento_inicial" inputmode="decimal"></label></div>
      <div class="acoes"><button class="primario">Abrir caixa</button></div></form>`;
    el.querySelector("#form-abertura").addEventListener("submit", evento => {
      evento.preventDefault();
      executar(async () => {
        const r = await api("POST", "/caixas/abertura", { suprimento_inicial: numero(dadosDoFormulario(evento.target).suprimento_inicial) });
        await telaCaixa(el);
        sucesso("Caixa aberto com sucesso", `Caixa #${r.caixa_id} aberto em ${dataHora(r.data_abertura)} com ${moeda(r.suprimento_inicial)} de troco.`, [], "HTTP 201 · POST /caixas/abertura");
      });
    });
    return;
  }
  const movimento = tipo => `<form class="cartao" data-mov="${tipo}" novalidate><h2>${tipo === "sangria" ? "Sangria (retirada)" : "Suprimento (reforço)"}</h2>
      <div class="grade"><label>Valor (R$)<input name="valor" inputmode="decimal"></label><label>Observação<input name="observacao"></label></div>
      <div class="acoes"><button>${tipo === "sangria" ? "Registrar sangria" : "Registrar suprimento"}</button></div></form>`;
  el.innerHTML = `
    <div class="indicadores">
      <div class="indicador"><small>Caixa</small><b>#${caixa.caixa_id} ${tag(caixa.status, "ok")}</b></div>
      <div class="indicador"><small>Aberto em</small><b style="font-size:14px">${dataHora(caixa.data_abertura)}</b></div>
      <div class="indicador"><small>Suprimento inicial</small><b>${moeda(caixa.suprimento_inicial)}</b></div>
      <div class="indicador"><small>Total de vendas</small><b>${moeda(caixa.total_vendas)}</b></div>
      <div class="indicador"><small>Sangrias</small><b>${moeda(caixa.total_sangrias)}</b></div>
      <div class="indicador"><small>Dinheiro em caixa</small><b>${moeda(caixa.saldo_em_dinheiro_atual)}</b></div>
    </div>
    ${movimento("sangria")}${movimento("suprimento")}
    <form id="form-fechamento" class="cartao" novalidate><h2>Fechar caixa</h2>
      <div class="grade"><label>Valor contado em dinheiro (R$)<input name="valor_final_dinheiro" inputmode="decimal"></label></div>
      <div class="acoes"><button class="perigo">Fechar caixa</button></div></form>`;
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
  el.querySelector("#form-fechamento").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const r = await api("POST", `/caixas/${caixa.caixa_id}/fechamento`, { valor_final_dinheiro: numero(dadosDoFormulario(evento.target).valor_final_dinheiro) });
      await telaCaixa(el);
      const diferenca = Number(r.diferenca);
      sucesso("Caixa fechado com sucesso", `Caixa #${r.caixa_id} fechado em ${dataHora(r.data_fechamento)}.`, [
        `Total de vendas: ${moeda(r.total_vendas)} (dinheiro: ${moeda(r.total_vendas_dinheiro)})`,
        `Saldo esperado em dinheiro: ${moeda(r.saldo_esperado_dinheiro)} · contado: ${moeda(r.valor_final_dinheiro)}`,
        `Diferença: ${moeda(r.diferenca)}${diferenca === 0 ? " (caixa conferido)" : diferenca < 0 ? " (falta)" : " (sobra)"}`],
        `HTTP 200 · POST /caixas/${caixa.caixa_id}/fechamento`);
    });
  });
}

// ---------- nova venda
async function telaNovaVenda(el) {
  const [caixa, produtos, clientes] = await Promise.all([caixaAtual(), api("GET", "/produtos?limite=100"), api("GET", "/clientes?limite=100")]);
  if (!caixa) {
    el.innerHTML = `<div class="mensagem alerta"><b>Caixa fechado</b>Abra o caixa antes de registrar vendas. <a href="#/caixa">Ir para o caixa</a></div>`;
    return;
  }
  const carrinho = [];
  const ativos = produtos.dados.filter(p => p.ativo);
  el.innerHTML = `
    <div class="cartao"><h2>Itens</h2><div class="grade">
      <label>Produto<select id="venda-produto">${ativos.map(p => `<option value="${p.id}">${esc(p.sku)} — ${esc(p.nome)} (${moeda(p.preco_venda)}, estoque ${p.estoque_atual})</option>`).join("")}</select></label>
      <label>Quantidade<input id="venda-quantidade" value="1" inputmode="numeric"></label>
    </div><div class="acoes"><button id="venda-adicionar">Adicionar item</button></div>
      <div id="venda-carrinho" style="margin-top:12px"></div></div>
    <form id="form-venda" class="cartao" novalidate><h2>Pagamento</h2><div class="grade">
      <label>Cliente<select name="cliente_id"><option value="">Consumidor padrão</option>${clientes.dados.filter(c => c.ativo && c.cpf_cnpj !== "00000000000").map(c => `<option value="${c.id}">${esc(c.nome_razao_social)}</option>`).join("")}</select></label>
      <label>Cupom<input name="cupom_codigo" placeholder="opcional"></label>
      <label>Desconto do gerente (R$)<input name="desconto_direto_gerente" inputmode="decimal" placeholder="opcional"></label>
      <label>Senha do gerente<input name="senha_autorizacao_gerente" type="password" placeholder="se houver desconto"></label>
      <label>Pagamento<select name="modalidade_pagamento"><option>DINHEIRO</option><option>PIX</option><option value="CARTAO_CREDITO">CARTÃO DE CRÉDITO</option><option value="CARTAO_DEBITO">CARTÃO DE DÉBITO</option><option>CREDIARIO</option></select></label>
    </div><div class="acoes"><button class="primario">Finalizar venda</button></div></form>`;

  const desenhar = () => {
    const total = carrinho.reduce((s, i) => s + i.produto.preco_venda * i.quantidade, 0);
    el.querySelector("#venda-carrinho").innerHTML = tabela([
      { titulo: "Produto", valor: i => esc(i.produto.nome) }, { titulo: "Qtd.", classe: "num", valor: i => i.quantidade },
      { titulo: "Unitário", classe: "num", valor: i => moeda(i.produto.preco_venda) },
      { titulo: "Subtotal", classe: "num", valor: i => moeda(i.produto.preco_venda * i.quantidade) }], carrinho, "Nenhum item adicionado.") + `<div class="total">Subtotal: <b>${moeda(total)}</b></div>`;
  };
  desenhar();
  el.querySelector("#venda-adicionar").addEventListener("click", () => {
    const produto = ativos.find(p => p.id === Number(el.querySelector("#venda-produto").value));
    const quantidade = Number(el.querySelector("#venda-quantidade").value);
    if (!produto) return;
    const existente = carrinho.find(i => i.produto.id === produto.id);
    if (existente) existente.quantidade += quantidade; else carrinho.push({ produto, quantidade });
    desenhar();
  });
  el.querySelector("#form-venda").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", "/vendas", {
        caixa_id: caixa.caixa_id, cliente_id: d.cliente_id ? Number(d.cliente_id) : undefined,
        cupom_codigo: d.cupom_codigo, desconto_direto_gerente: numero(d.desconto_direto_gerente),
        senha_autorizacao_gerente: d.senha_autorizacao_gerente, modalidade_pagamento: d.modalidade_pagamento,
        itens: carrinho.map(i => ({ produto_id: i.produto.id, quantidade: i.quantidade })),
      });
      await telaNovaVenda(el);
      const itens = [`Subtotal: ${moeda(r.valor_subtotal)}`];
      if (Number(r.desconto_cupom)) itens.push(`Desconto do cupom: ${moeda(r.desconto_cupom)}`);
      if (Number(r.desconto_gerente)) itens.push(`Desconto do gerente: ${moeda(r.desconto_gerente)}`);
      itens.push(`Total pago (${r.modalidade_pagamento}): ${moeda(r.valor_total)}`, `Comissão do vendedor: ${moeda(r.comissao_vendedor)}`);
      (r.alertas_estoque_critico || []).forEach(a => itens.push(`Alerta: ${a.nome} ficou com ${a.estoque_atual} un. (mínimo ${a.estoque_minimo})`));
      sucesso(`Venda #${r.venda_id} registrada com sucesso`, `Status ${r.status} em ${dataHora(r.data_venda)}.`, itens, "HTTP 201 · POST /vendas");
    });
  });
}

// ---------- vendas
async function telaVendas(el) {
  const pagina = await api("GET", "/vendas?limite=100");
  el.innerHTML = `
    ${ehGerente() ? `<form id="form-cancelar" class="cartao" novalidate><h2>Cancelar venda</h2><div class="grade">
      <label>Nº da venda<input name="venda_id" inputmode="numeric"></label>
      <label>Motivo<input name="motivo_cancelamento"></label></div>
      <div class="acoes"><button class="perigo">Cancelar venda</button></div></form>` : ""}
    <h2>Vendas (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "Nº", valor: v => v.venda_id }, { titulo: "Data", valor: v => dataHora(v.data_venda) },
      { titulo: "Cliente", valor: v => esc(v.cliente) }, { titulo: "Vendedor", valor: v => esc(v.vendedor) },
      { titulo: "Pagamento", valor: v => v.modalidade_pagamento }, { titulo: "Total", classe: "num", valor: v => moeda(v.valor_total) },
      { titulo: "Status", valor: v => tag(v.status, v.status === "CONCLUIDA" ? "ok" : "erro") }], pagina.dados)}`;
  el.querySelector("#form-cancelar")?.addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", `/vendas/${encodeURIComponent(d.venda_id || "0")}/cancelar`, { motivo_cancelamento: d.motivo_cancelamento });
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
    <form id="form-cupom" class="cartao" novalidate><h2>Cadastrar cupom</h2><div class="grade">
      <label>Código<input name="codigo"></label>
      <label>Tipo<select name="tipo_desconto"><option>PERCENTUAL</option><option>VALOR_FIXO</option></select></label>
      <label>Valor do desconto<input name="valor_desconto" inputmode="decimal"></label>
      <label>Início<input name="inicio" type="date" value="${dia(hoje)}"></label>
      <label>Término<input name="termino" type="date" value="${dia(new Date(hoje.getTime() + 30 * 864e5))}"></label>
    </div><div class="acoes"><button class="primario">Cadastrar cupom</button></div></form>
    <h2>Cupons (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "Código", valor: c => esc(c.codigo) }, { titulo: "Tipo", valor: c => c.tipo_desconto },
      { titulo: "Desconto", classe: "num", valor: c => c.tipo_desconto === "PERCENTUAL" ? `${c.valor_desconto}%` : moeda(c.valor_desconto) },
      { titulo: "Validade", valor: c => `${dataHora(c.data_inicio_validade)} a ${dataHora(c.data_termino_validade)}` },
      { titulo: "Situação", valor: c => c.vigente ? tag("Vigente", "ok") : tag("Fora da validade", "alerta") }], pagina.dados)}`;
  el.querySelector("#form-cupom").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", "/cupons", { codigo: d.codigo, tipo_desconto: d.tipo_desconto, valor_desconto: numero(d.valor_desconto),
        data_inicio_validade: d.inicio ? `${d.inicio}T00:00:00-03:00` : null, data_termino_validade: d.termino ? `${d.termino}T23:59:59-03:00` : null });
      await telaCupons(el);
      sucesso("Cupom cadastrado com sucesso", `${r.codigo}: ${r.tipo_desconto} de ${r.valor_desconto}.`, [], "HTTP 201 · POST /cupons");
    });
  });
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

// ---------- usuários
async function telaUsuarios(el) {
  const pagina = await api("GET", "/usuarios?limite=100");
  el.innerHTML = `
    <form id="form-usuario" class="cartao" novalidate><h2>Cadastrar usuário</h2><div class="grade">
      <label>CPF<input name="cpf"></label>
      <label>Nome<input name="nome"></label>
      <label>Perfil<select name="perfil"><option>VENDEDOR</option><option>GERENTE</option></select></label>
      <label>Comissão (%)<input name="percentual_comissao" value="5" inputmode="decimal"></label>
      <label>Senha inicial<input name="senha" type="password"></label>
    </div><div class="acoes"><button class="primario">Cadastrar usuário</button></div></form>
    <h2>Usuários (${pagina.total_registros})</h2>
    ${tabela([
      { titulo: "ID", valor: u => u.id }, { titulo: "CPF", valor: u => cpfFormatado(u.cpf) }, { titulo: "Nome", valor: u => esc(u.nome) },
      { titulo: "Perfil", valor: u => u.perfil }, { titulo: "Comissão", classe: "num", valor: u => `${u.percentual_comissao}%` },
      { titulo: "Situação", valor: u => ativo(u.ativo) }], pagina.dados)}`;
  el.querySelector("#form-usuario").addEventListener("submit", evento => {
    evento.preventDefault();
    executar(async () => {
      const d = dadosDoFormulario(evento.target);
      const r = await api("POST", "/usuarios", { ...d, cpf: soDigitos(d.cpf) || d.cpf, percentual_comissao: numero(d.percentual_comissao) });
      await telaUsuarios(el);
      sucesso("Usuário cadastrado com sucesso", `${r.nome} (${r.perfil}) — ID ${r.id}.`, [], "HTTP 201 · POST /usuarios");
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
