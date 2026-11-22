// Executa o roteiro de uso do front-end do PDV Web e tira um print de cada passo (feedback visual da aplicação).
// Requer a API no modo demonstração rodando em http://localhost:8080 (codigo-fonte/pdv-web-api: bash demo.sh).
// Saída: telas/NN-nome.png e telas/roteiro.json (o documento geral lê esse JSON e monta a seção de evidências).
// Uso: node capturar-telas.mjs [url-base]
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import puppeteer from "puppeteer-core";

const FONTES = path.dirname(fileURLToPath(import.meta.url));
const SAIDA = path.join(FONTES, "telas");
const BASE = process.argv[2] || "http://localhost:8080";
const CHROME = process.env.CHROME_PATH
  || ["C:/Program Files/Google/Chrome/Application/chrome.exe",
      "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
      "/usr/bin/google-chrome", "/usr/bin/chromium"].find(c => fs.existsSync(c));

const GERENTE = { cpf: "111.444.777-35", senha: "Gerente@Demo1" };
const VENDEDOR = { cpf: "529.982.247-25", senha: "Vendedor@123" };

fs.rmSync(SAIDA, { recursive: true, force: true });
fs.mkdirSync(SAIDA, { recursive: true });

const navegador = await puppeteer.launch({ executablePath: CHROME, headless: true, defaultViewport: { width: 1280, height: 800 } });
const pagina = await navegador.newPage();
const roteiro = [];
const espera = ms => new Promise(r => setTimeout(r, ms));

/** Aguarda a requisição terminar e a mensagem de retorno aparecer. */
async function aguardarMensagem(seletor = "#aviso .mensagem, #aviso-login .mensagem") {
  await pagina.waitForSelector(seletor, { visible: true, timeout: 15000 });
  await espera(250);
}

async function limparEPreencher(seletor, valor) {
  await pagina.$eval(seletor, el => { el.value = ""; });
  if (valor !== "") await pagina.type(seletor, String(valor));
}

async function preencher(form, campos) {
  for (const [nome, valor] of Object.entries(campos)) {
    const seletor = `${form} [name="${nome}"]`;
    const tag = await pagina.$eval(seletor, el => el.tagName);
    if (tag === "SELECT") await pagina.select(seletor, String(valor));
    else await limparEPreencher(seletor, valor);
  }
}

async function enviar(form) {
  await pagina.$eval("#aviso", el => { el.innerHTML = ""; }).catch(() => {});
  await pagina.$eval("#aviso-login", el => { el.innerHTML = ""; }).catch(() => {});
  await pagina.click(`${form} button`);
  await aguardarMensagem();
}

async function irPara(tela) {
  // se já está na tela, a troca de hash não dispara o evento: força a recarga (carrinho vazio, formulários limpos)
  await pagina.evaluate(t => { if (location.hash === `#/${t}`) { limparAviso(); abrirTela(); } else location.hash = `#/${t}`; }, tela);
  await pagina.waitForFunction(() => !document.querySelector("#tela .vazio")?.textContent.includes("Carregando"), { timeout: 15000 });
  await espera(300);
}

async function login(usuario) {
  await limparEPreencher("#login-cpf", usuario.cpf);
  await limparEPreencher("#login-senha", usuario.senha);
  await enviar("#form-login");
}

async function sair() {
  await pagina.click("#botao-sair");
  await pagina.waitForSelector("#tela-login:not([hidden])");
  await espera(200);
}

/**
 * Registra um passo do roteiro: o que foi feito, o resultado esperado, o print e o texto que a aplicação mostrou.
 * tipo: "sucesso" (fluxo correto) ou "erro" (entrada inválida ou operação proibida).
 */
async function passo(id, { titulo, tipo, acao, esperado, executar }) {
  await executar();
  // o print só é tirado depois que a tela terminou de carregar os dados
  await pagina.waitForFunction(() => !document.querySelector("#tela-app:not([hidden]) #tela")?.textContent.includes("Carregando"),
    { timeout: 15000 });
  await espera(300);
  const arquivo = `${id}.png`;
  await pagina.screenshot({ path: path.join(SAIDA, arquivo), fullPage: false });
  const obtido = await pagina.evaluate(() => {
    const m = document.querySelector("#tela-app:not([hidden]) #aviso .mensagem, #tela-login:not([hidden]) #aviso-login .mensagem");
    return m ? m.innerText.trim() : "";
  });
  const classe = await pagina.evaluate(() => document.querySelector("#aviso .mensagem, #aviso-login .mensagem")?.className || "");
  const obteve = classe.includes("sucesso") ? "sucesso" : classe.includes("erro") ? "erro" : "tela";
  roteiro.push({ id, titulo, tipo, acao, esperado, obtido, arquivo, aprovado: tipo === "tela" || obteve === tipo });
  console.log(`${obteve === tipo || tipo === "tela" ? "ok " : "FALHOU"} ${id} ${titulo}`);
}

try {
  await pagina.goto(`${BASE}/`, { waitUntil: "networkidle0" });

  // ------------------------------------------------ autenticação
  await passo("01-tela-login", { titulo: "Tela de login", tipo: "tela",
    acao: "Acessar o endereço da aplicação (http://localhost:8080). A raiz redireciona para a tela de login.",
    esperado: "Formulário de login com CPF e senha.", executar: async () => {} });

  await passo("02-login-campos-vazios", { titulo: "Login com campos em branco", tipo: "erro",
    acao: "Clicar em Entrar sem preencher CPF nem senha.",
    esperado: "A API recusa com HTTP 400 e informa os campos obrigatórios.",
    executar: () => login({ cpf: "", senha: "" }) });

  await passo("03-login-senha-invalida", { titulo: "Login com credencial inválida", tipo: "erro",
    acao: `Informar o CPF do gerente (${GERENTE.cpf}) com a senha errada "SenhaErrada1".`,
    esperado: "A API recusa com HTTP 401 e mensagem genérica (não revela se o CPF existe).",
    executar: () => login({ cpf: GERENTE.cpf, senha: "SenhaErrada1" }) });

  await passo("04-login-sucesso", { titulo: "Login realizado com sucesso (gerente)", tipo: "sucesso",
    acao: `Informar CPF ${GERENTE.cpf} e a senha correta do gerente.`,
    esperado: "A API devolve o token JWT; a aplicação abre o painel e mostra o nome, o perfil e a validade da sessão.",
    executar: () => login(GERENTE) });

  // ------------------------------------------------ produtos
  await irPara("produtos");
  await passo("05-produto-cadastro-sucesso", { titulo: "Cadastro de produto correto", tipo: "sucesso",
    acao: "Em Produtos, cadastrar SKU PROD-10, \"Boné Aba Reta\", UN, código 7891000000103, R$ 79,90, estoque 20, mínimo 5.",
    esperado: "HTTP 201; o produto aparece na lista com o ID gerado.",
    executar: async () => {
      await preencher("#form-produto", { sku: "PROD-10", nome: "Boné Aba Reta", fornecedor: "Fornecedor Exemplo", unidade_medida: "UN",
        codigo_barras: "7891000000103", preco_venda: "79.90", estoque_atual: "20", estoque_minimo: "5" });
      await enviar("#form-produto");
    } });

  await passo("06-produto-dados-invalidos", { titulo: "Cadastro de produto com dados inválidos", tipo: "erro",
    acao: "Cadastrar produto sem nome, com preço 0, estoque -3 e código de barras \"123\".",
    esperado: "HTTP 400; a aplicação lista cada campo recusado e o motivo.",
    executar: async () => {
      await preencher("#form-produto", { sku: "PROD-11", nome: "", fornecedor: "", unidade_medida: "UN",
        codigo_barras: "123", preco_venda: "0", estoque_atual: "-3", estoque_minimo: "1" });
      await enviar("#form-produto");
    } });

  await passo("07-produto-sku-duplicado", { titulo: "Cadastro de produto com SKU repetido", tipo: "erro",
    acao: "Cadastrar outro produto com o SKU PROD-10, que já existe.",
    esperado: "HTTP 409 (conflito); nenhum produto duplicado é criado.",
    executar: async () => {
      await preencher("#form-produto", { sku: "PROD-10", nome: "Outro boné", fornecedor: "", unidade_medida: "UN",
        codigo_barras: "", preco_venda: "50", estoque_atual: "1", estoque_minimo: "0" });
      await enviar("#form-produto");
    } });

  // ------------------------------------------------ clientes
  await irPara("clientes");
  await passo("08-cliente-cadastro-sucesso", { titulo: "Cadastro de cliente correto", tipo: "sucesso",
    acao: "Em Clientes, cadastrar pessoa física \"Maria Oliveira\", CPF 390.533.447-05, e-mail e cidade.",
    esperado: "HTTP 201; o cliente aparece na lista.",
    executar: async () => {
      await preencher("#form-cliente", { tipo: "PF", cpf_cnpj: "390.533.447-05", nome_razao_social: "Maria Oliveira",
        email: "maria@exemplo.com", telefone: "(11) 98888-7777", cep: "01310-100", cidade: "São Paulo", uf: "SP" });
      await enviar("#form-cliente");
    } });

  await passo("09-cliente-cpf-invalido", { titulo: "Cadastro de cliente com CPF inválido", tipo: "erro",
    acao: "Cadastrar cliente com CPF 123.456.789-00 (dígitos verificadores errados).",
    esperado: "A API recusa o documento: o CPF não passa na validação dos dígitos verificadores.",
    executar: async () => {
      await preencher("#form-cliente", { tipo: "PF", cpf_cnpj: "123.456.789-00", nome_razao_social: "Cliente Teste",
        email: "cliente@exemplo.com", telefone: "", cep: "", cidade: "", uf: "" });
      await enviar("#form-cliente");
    } });

  // ------------------------------------------------ perfil vendedor: caixa e vendas
  await sair();
  await passo("10-login-vendedor", { titulo: "Login realizado com sucesso (vendedor)", tipo: "sucesso",
    acao: `Sair e entrar com o vendedor (CPF ${VENDEDOR.cpf}).`,
    esperado: "Painel do vendedor; o menu esconde Cupons, Usuários e Auditoria, exclusivos do gerente.",
    executar: () => login(VENDEDOR) });

  await irPara("caixa");
  await passo("11-caixa-valor-negativo", { titulo: "Abertura de caixa com valor negativo", tipo: "erro",
    acao: "Em Caixa, tentar abrir o caixa com suprimento inicial de R$ -50,00.",
    esperado: "HTTP 400: o suprimento inicial não pode ser negativo.",
    executar: async () => { await preencher("#form-abertura", { suprimento_inicial: "-50" }); await enviar("#form-abertura"); } });

  await passo("12-caixa-abertura-sucesso", { titulo: "Abertura de caixa", tipo: "sucesso",
    acao: "Abrir o caixa com R$ 200,00 de troco.",
    esperado: "HTTP 201; a tela passa a mostrar o caixa aberto e os totais do turno.",
    executar: async () => { await preencher("#form-abertura", { suprimento_inicial: "200" }); await enviar("#form-abertura"); } });

  async function montarVenda(itens, pagamento) {
    await irPara("venda");
    for (const [sku, quantidade] of itens) {
      const valor = await pagina.$$eval("#venda-produto option", (opcoes, s) => opcoes.find(o => o.textContent.startsWith(s + " "))?.value, sku);
      await pagina.select("#venda-produto", valor);
      await limparEPreencher("#venda-quantidade", quantidade);
      await pagina.click("#venda-adicionar");
    }
    await preencher("#form-venda", pagamento);
  }

  await passo("13-venda-sucesso-cupom", { titulo: "Venda com cupom de desconto", tipo: "sucesso",
    acao: "Em Nova venda, adicionar 2 × Camiseta P e 1 × Boné Aba Reta, cliente Maria Oliveira, cupom DESCONTO10, pagamento PIX.",
    esperado: "HTTP 201; a aplicação mostra subtotal, desconto de 10%, total e a comissão de 5% do vendedor.",
    executar: async () => {
      await montarVenda([["PROD-01", 2], ["PROD-10", 1]], { cupom_codigo: "DESCONTO10", modalidade_pagamento: "PIX" });
      const cliente = await pagina.$$eval("#form-venda [name=cliente_id] option", o => o.find(x => x.textContent.includes("Maria"))?.value);
      await pagina.select("#form-venda [name=cliente_id]", cliente);
      await enviar("#form-venda");
    } });

  await passo("14-venda-estoque-insuficiente", { titulo: "Venda acima do estoque disponível", tipo: "erro",
    acao: "Tentar vender 5 × Tênis 41, que tem apenas 1 unidade em estoque.",
    esperado: "HTTP 422; a venda é recusada inteira, nada é baixado do estoque, e a mensagem informa o saldo disponível.",
    executar: async () => { await montarVenda([["PROD-02", 5]], { modalidade_pagamento: "DINHEIRO" }); await enviar("#form-venda"); } });

  await passo("15-venda-cupom-inexistente", { titulo: "Venda com cupom inexistente", tipo: "erro",
    acao: "Vender 1 × Meia informando o cupom \"NATAL50\", que não existe.",
    esperado: "HTTP 422; a venda é recusada e a aplicação informa que o cupom é inválido.",
    executar: async () => { await montarVenda([["PROD-03", 1]], { cupom_codigo: "NATAL50", modalidade_pagamento: "DINHEIRO" }); await enviar("#form-venda"); } });

  await passo("16-venda-desconto-senha-errada", { titulo: "Desconto manual com senha de gerente errada", tipo: "erro",
    acao: "Vender 1 × Teclado Mecânico com desconto de R$ 30,00, informando uma senha de gerente incorreta.",
    esperado: "HTTP 403; a venda é recusada: o vendedor só pode dar desconto com a senha válida de um gerente.",
    executar: async () => {
      await montarVenda([["PROD-ELE-001", 1]], { desconto_direto_gerente: "30", senha_autorizacao_gerente: "senhaerrada", modalidade_pagamento: "CARTAO_CREDITO" });
      await enviar("#form-venda");
    } });

  await passo("17-venda-desconto-autorizado", { titulo: "Desconto manual autorizado pelo gerente", tipo: "sucesso",
    acao: "Repetir a venda do Teclado Mecânico com desconto de R$ 30,00, agora com a senha correta do gerente.",
    esperado: "HTTP 201; total de R$ 220,00; o gerente que autorizou fica registrado na venda e na auditoria.",
    executar: async () => {
      await montarVenda([["PROD-ELE-001", 1]], { desconto_direto_gerente: "30", senha_autorizacao_gerente: GERENTE.senha, modalidade_pagamento: "CARTAO_CREDITO" });
      await enviar("#form-venda");
    } });

  await passo("18-venda-dinheiro-alerta", { titulo: "Venda em dinheiro com alerta de estoque crítico", tipo: "sucesso",
    acao: "Vender 2 × Meia (estoque 3, mínimo 2) em dinheiro para o consumidor padrão.",
    esperado: "HTTP 201; a venda é registrada e a aplicação avisa que a Meia ficou abaixo do estoque mínimo.",
    executar: async () => { await montarVenda([["PROD-03", 2]], { modalidade_pagamento: "DINHEIRO" }); await enviar("#form-venda"); } });

  await irPara("caixa");
  await passo("19-vendedor-sangria-negada", { titulo: "Vendedor tenta fazer sangria", tipo: "erro",
    acao: "Na tela do Caixa, o vendedor tenta registrar uma sangria de R$ 50,00.",
    esperado: "HTTP 403: sangria é exclusiva do gerente.",
    executar: async () => { await preencher('[data-mov="sangria"]', { valor: "50", observacao: "Retirada" }); await enviar('[data-mov="sangria"]'); } });

  await passo("20-vendedor-acesso-negado", { titulo: "Vendedor tenta abrir tela de gerente", tipo: "erro",
    acao: "Digitar na barra de endereço a tela de Usuários (#/usuarios), exclusiva do gerente.",
    esperado: "HTTP 403 (acesso negado); nenhum dado é exibido.",
    executar: async () => {
      await pagina.$eval("#aviso", el => { el.innerHTML = ""; });
      await pagina.evaluate(() => { location.hash = "#/usuarios"; });
      await aguardarMensagem("#aviso .mensagem");
    } });

  await irPara("comissoes");
  await passo("21-comissoes-vendedor", { titulo: "Comissões do vendedor", tipo: "tela",
    acao: "Abrir Comissões com o perfil vendedor.",
    esperado: "O vendedor vê apenas as próprias vendas e a comissão acumulada (5% do valor vendido).",
    executar: async () => {} });

  // ------------------------------------------------ perfil gerente: cancelamento, sangria e fechamento
  await sair();
  await login(GERENTE);
  await irPara("vendas");
  const ultimaVenda = await pagina.$eval("#tela tbody tr td", td => td.textContent.trim());
  await passo("22-cancelamento-sem-motivo", { titulo: "Cancelamento de venda sem motivo", tipo: "erro",
    acao: `Entrar como gerente e, em Vendas, tentar cancelar a venda nº ${ultimaVenda} com o motivo "x".`,
    esperado: "HTTP 400: o motivo deve ter entre 5 e 255 caracteres.",
    executar: async () => { await preencher("#form-cancelar", { venda_id: ultimaVenda, motivo_cancelamento: "x" }); await enviar("#form-cancelar"); } });

  await passo("23-cancelamento-sucesso", { titulo: "Cancelamento de venda", tipo: "sucesso",
    acao: `Cancelar a venda nº ${ultimaVenda} com o motivo "Cliente desistiu da compra".`,
    esperado: "HTTP 200; a venda fica CANCELADA, o estoque é devolvido e a comissão deixa de contar.",
    executar: async () => { await preencher("#form-cancelar", { venda_id: ultimaVenda, motivo_cancelamento: "Cliente desistiu da compra" }); await enviar("#form-cancelar"); } });

  await irPara("caixa");
  await preencher("#form-abertura", { suprimento_inicial: "300" });
  await enviar("#form-abertura");
  await passo("24-sangria-acima-do-saldo", { titulo: "Sangria maior que o dinheiro em caixa", tipo: "erro",
    acao: "Com o caixa do gerente aberto com R$ 300,00, registrar sangria de R$ 5.000,00.",
    esperado: "HTTP 422: não é possível retirar mais do que o saldo em dinheiro.",
    executar: async () => { await preencher('[data-mov="sangria"]', { valor: "5000", observacao: "Teste de retirada" }); await enviar('[data-mov="sangria"]'); } });

  await passo("25-sangria-sucesso", { titulo: "Sangria registrada", tipo: "sucesso",
    acao: "Registrar sangria de R$ 100,00 com a observação \"Depósito no cofre\".",
    esperado: "HTTP 201; o saldo em dinheiro do caixa cai para R$ 200,00.",
    executar: async () => { await preencher('[data-mov="sangria"]', { valor: "100", observacao: "Depósito no cofre" }); await enviar('[data-mov="sangria"]'); } });

  await passo("26-fechamento-caixa", { titulo: "Fechamento de caixa com diferença", tipo: "sucesso",
    acao: "Fechar o caixa informando R$ 190,00 contados em dinheiro (R$ 10,00 a menos que o esperado).",
    esperado: "HTTP 200; a aplicação mostra saldo esperado, valor contado e a diferença de R$ -10,00 (falta).",
    executar: async () => { await preencher("#form-fechamento", { valor_final_dinheiro: "190" }); await enviar("#form-fechamento"); } });

  await irPara("comissoes");
  await passo("27-comissoes-gerente", { titulo: "Relatório de comissões", tipo: "tela",
    acao: "Abrir Comissões com o perfil gerente.",
    esperado: "Quadro de todos os vendedores; a venda cancelada não entra no total.",
    executar: async () => {} });

  await irPara("auditoria");
  await passo("28-auditoria", { titulo: "Registro de auditoria", tipo: "tela",
    acao: "Abrir Auditoria (exclusivo do gerente).",
    esperado: "Lista das ações sensíveis: logins e falhas, cadastros, vendas, desconto autorizado, cancelamento, sangria e fechamento.",
    executar: async () => {} });

  await sair();
  await passo("29-logout", { titulo: "Logout", tipo: "sucesso",
    acao: "Clicar em Sair.",
    esperado: "O token é revogado no servidor e a aplicação volta para o login.",
    executar: async () => {} });

  // ------------------------------------------------ Swagger
  // Swagger UI em tema claro, página inteira cortada em partes que cabem numa página A4
  await pagina.emulateMediaFeatures([{ name: "prefers-color-scheme", value: "light" }]);
  await pagina.goto(`${BASE}/swagger-ui.html`, { waitUntil: "networkidle0" });
  await pagina.waitForSelector(".opblock", { timeout: 20000 });
  await espera(800);
  const altura = await pagina.evaluate(() => document.documentElement.scrollHeight);
  const PARTE = 1500;
  for (let y = 0, n = 1; y < altura; y += PARTE, n++) {
    await pagina.screenshot({ path: path.join(SAIDA, `swagger-${n}.png`), captureBeyondViewport: true,
      clip: { x: 0, y, width: 1280, height: Math.min(PARTE, altura - y) } });
  }
} finally {
  fs.writeFileSync(path.join(SAIDA, "roteiro.json"), JSON.stringify(roteiro, null, 2));
  await navegador.close();
}
const falhas = roteiro.filter(p => !p.aprovado);
console.log(`${roteiro.length} passos, ${falhas.length} com resultado diferente do esperado`);
if (falhas.length) process.exitCode = 1;
