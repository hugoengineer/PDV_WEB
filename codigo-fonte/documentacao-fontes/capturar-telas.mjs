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
// Prints do documento sempre no tema claro (melhor para impressão), independentemente do sistema operacional
await pagina.emulateMediaFeatures([{ name: "prefers-color-scheme", value: "light" }]);
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

  // ------------------------------------------------ perfil vendedor: frente de caixa
  await sair();
  await passo("10-login-vendedor", { titulo: "Login realizado com sucesso (vendedor)", tipo: "sucesso",
    acao: `Sair e entrar com o vendedor (CPF ${VENDEDOR.cpf}).`,
    esperado: "Painel do vendedor em azul; o menu esconde Caixa, Cupons, Funcionários, Configurações e Auditoria, exclusivos do gerente.",
    executar: () => login(VENDEDOR) });

  await irPara("venda");
  await passo("11-caixa-valor-negativo", { titulo: "Abertura de caixa com valor negativo", tipo: "erro",
    acao: "Na Frente de caixa (sem caixa aberto), tentar abrir o caixa com suprimento inicial de R$ -50,00.",
    esperado: "HTTP 400: o suprimento inicial não pode ser negativo.",
    executar: async () => { await preencher("#tela form", { suprimento_inicial: "-50" }); await enviar("#tela form"); } });

  await passo("12-caixa-abertura-sucesso", { titulo: "Abertura de caixa pela Frente de caixa", tipo: "sucesso",
    acao: "Abrir o caixa com R$ 200,00 de troco.",
    esperado: "HTTP 201; a Frente de caixa mostra o caixa aberto, o botão Fechar caixa e o pedido vazio.",
    executar: async () => { await preencher("#tela form", { suprimento_inicial: "200" }); await enviar("#tela form"); } });

  /** Monta o pedido na Frente de caixa e espera o total exato calculado pela API (prévia). */
  async function montarPedido(itens, campos = {}) {
    await irPara("venda");
    for (const [sku, quantidade] of itens) {
      const valor = await pagina.$$eval("#venda-produto option", (opcoes, s) => opcoes.find(o => o.textContent.startsWith(s + " "))?.value, sku);
      await pagina.select("#venda-produto", valor);
      await limparEPreencher("#venda-quantidade", quantidade);
      await pagina.click("#venda-adicionar");
    }
    await preencher("#form-venda", campos);
    await pagina.evaluate(() => document.querySelector("#form-venda [name=cupom_codigo]").dispatchEvent(new Event("input")));
    await espera(700);
  }
  const totalCalculado = () => pagina.waitForSelector("#venda-totais .destaque", { timeout: 15000 });
  /** Inclui um pagamento; sem valor, usa o restante sugerido pela tela. */
  async function incluirPagamento(forma, valor, parcelas = 1) {
    await pagina.select("#pag-forma", forma);
    if (valor != null) await limparEPreencher("#pag-valor", valor);
    await espera(100);
    if (parcelas > 1) await pagina.select("#pag-parcelas", String(parcelas));
    await pagina.click("#pag-incluir");
    await espera(150);
  }
  async function finalizar() {
    await pagina.$eval("#aviso", el => { el.innerHTML = ""; });
    await pagina.click("#venda-finalizar");
    await aguardarMensagem();
  }
  async function vender(itens, campos, pagamentos) {
    await montarPedido(itens, campos);
    await totalCalculado();
    for (const p of pagamentos) await incluirPagamento(...p);
    await finalizar();
  }

  await passo("13-venda-sucesso-cupom", { titulo: "Venda com cupom de desconto", tipo: "sucesso",
    acao: "Adicionar 2 × Camiseta P e 1 × Boné Aba Reta, cliente Maria Oliveira, cupom DESCONTO10, pagamento único em PIX.",
    esperado: "HTTP 201; a aplicação mostra subtotal, desconto de 10%, total, o pagamento e a comissão de 5% do vendedor.",
    executar: async () => {
      await montarPedido([["PROD-01", 2], ["PROD-10", 1]], { cupom_codigo: "DESCONTO10" });
      const cliente = await pagina.$$eval("#form-venda [name=cliente_id] option", o => o.find(x => x.textContent.includes("Maria"))?.value);
      await pagina.select("#form-venda [name=cliente_id]", cliente);
      await totalCalculado();
      await incluirPagamento("PIX", null);
      await finalizar();
    } });

  await passo("14-pedido-editado", { titulo: "Pedido editado antes de finalizar", tipo: "tela",
    acao: "Adicionar 1 × Teclado Mecânico e 3 × Camiseta P; mudar a quantidade das camisetas para 1 e remover o teclado.",
    esperado: "O pedido fica só com 1 × Camiseta P; subtotal e total a pagar são recalculados a cada mudança; nada é gravado.",
    executar: async () => {
      await montarPedido([["PROD-ELE-001", 1], ["PROD-01", 3]]);
      await pagina.evaluate(() => {
        const campos = [...document.querySelectorAll(".qtd-item")];
        const camiseta = campos[1];
        camiseta.value = "1";
        camiseta.dispatchEvent(new Event("change", { bubbles: true }));
      });
      await espera(500);
      await pagina.evaluate(() => document.querySelector("[data-remover]").click());
      await totalCalculado();
      await espera(300);
    } });

  await passo("15-venda-pagamento-dividido", { titulo: "Pagamento dividido: dinheiro + crédito parcelado", tipo: "sucesso",
    acao: "Vender 1 × Teclado Mecânico (R$ 250,00): incluir R$ 100,00 em dinheiro e o restante, R$ 150,00, no cartão de crédito em 3x sem juros; observação \"Entregar com nota fiscal\".",
    esperado: "HTTP 201; a venda fica com forma MULTIPLA; o retorno lista R$ 100,00 em dinheiro e R$ 150,00 em 3x de R$ 50,00.",
    executar: async () => {
      await montarPedido([["PROD-ELE-001", 1]], { observacao: "Entregar com nota fiscal" });
      await totalCalculado();
      await incluirPagamento("DINHEIRO", "100");
      await incluirPagamento("CARTAO_CREDITO", null, 3);
      await finalizar();
    } });

  await passo("16-venda-estoque-insuficiente", { titulo: "Venda acima do estoque disponível", tipo: "erro",
    acao: "Tentar vender 5 × Tênis 41, que tem apenas 1 unidade em estoque.",
    esperado: "HTTP 422; a venda é recusada inteira, nada é baixado do estoque, e a mensagem informa o saldo disponível.",
    executar: () => vender([["PROD-02", 5]], {}, [["DINHEIRO", null]]) });

  await passo("17-venda-cupom-inexistente", { titulo: "Venda com cupom inexistente", tipo: "erro",
    acao: "Montar o pedido de 1 × Meia informando o cupom \"NATAL50\", que não existe.",
    esperado: "HTTP 422 já no cálculo do total: o cupom é inválido e a venda não pode ser finalizada.",
    executar: async () => { await montarPedido([["PROD-03", 1]], { cupom_codigo: "NATAL50" }); await aguardarMensagem("#aviso .mensagem"); } });

  await passo("18-venda-desconto-senha-errada", { titulo: "Desconto manual com senha de gerente errada", tipo: "erro",
    acao: "Vender 1 × Teclado Mecânico com desconto de R$ 30,00, informando uma senha de gerente incorreta.",
    esperado: "HTTP 403; a venda é recusada: o vendedor só pode dar desconto com a senha válida de um gerente.",
    executar: () => vender([["PROD-ELE-001", 1]], { desconto_direto_gerente: "30", senha_autorizacao_gerente: "senhaerrada" }, [["CARTAO_CREDITO", null]]) });

  await passo("19-venda-desconto-autorizado", { titulo: "Desconto manual autorizado pelo gerente", tipo: "sucesso",
    acao: "Repetir a venda do Teclado Mecânico com desconto de R$ 30,00, agora com a senha correta do gerente.",
    esperado: "HTTP 201; total de R$ 220,00; o gerente que autorizou fica registrado na venda e na auditoria.",
    executar: () => vender([["PROD-ELE-001", 1]], { desconto_direto_gerente: "30", senha_autorizacao_gerente: GERENTE.senha }, [["CARTAO_CREDITO", null]]) });

  await passo("20-venda-dinheiro-alerta", { titulo: "Venda em dinheiro com alerta de estoque crítico", tipo: "sucesso",
    acao: "Vender 2 × Meia (estoque 3, mínimo 2) em dinheiro para o consumidor padrão.",
    esperado: "HTTP 201; a venda é registrada e a aplicação avisa que a Meia ficou abaixo do estoque mínimo.",
    executar: () => vender([["PROD-03", 2]], {}, [["DINHEIRO", null]]) });

  await passo("21-vendedor-tela-gerente", { titulo: "Vendedor tenta abrir tela de gerente", tipo: "tela",
    acao: "Digitar na barra de endereço a aba Caixa (#/caixa), exclusiva do gerente.",
    esperado: "A aba não abre: o vendedor volta ao Início. Sangria e suprimento continuam bloqueados também na API (403).",
    executar: async () => { await pagina.evaluate(() => { location.hash = "#/caixa"; }); await espera(800); } });

  await irPara("vendas");
  const vendaMeia = await pagina.$eval("#tela tbody tr td", td => td.textContent.trim());
  async function abrirDetalhe(numero) {
    await pagina.evaluate(n => document.querySelector(`[data-ver="${n}"]`).click(), numero);
    await pagina.waitForSelector("#form-cancelar", { timeout: 15000 });
    await espera(300);
  }
  await passo("22-vendedor-cancela-sem-senha", { titulo: "Vendedor tenta cancelar sem a senha do gerente", tipo: "erro",
    acao: `Em Vendas, abrir os detalhes da venda nº ${vendaMeia} e cancelar com o motivo "Cliente desistiu da compra", sem senha.`,
    esperado: "HTTP 403: o vendedor só cancela com a senha de um gerente; a venda continua concluída.",
    executar: async () => {
      await abrirDetalhe(vendaMeia);
      await preencher("#form-cancelar", { motivo_cancelamento: "Cliente desistiu da compra" });
      await enviar("#form-cancelar");
    } });

  await passo("23-vendedor-cancela-com-senha", { titulo: "Cancelamento autorizado pelo gerente", tipo: "sucesso",
    acao: `Repetir o cancelamento da venda nº ${vendaMeia}, agora com a senha do gerente.`,
    esperado: "HTTP 200; a venda fica CANCELADA, o estoque volta e o gerente que autorizou fica registrado.",
    executar: async () => {
      await preencher("#form-cancelar", { motivo_cancelamento: "Cliente desistiu da compra", senha_autorizacao_gerente: GERENTE.senha });
      await enviar("#form-cancelar");
    } });

  await irPara("comissoes");
  await passo("24-comissoes-vendedor", { titulo: "Comissões do vendedor", tipo: "tela",
    acao: "Abrir Comissões com o perfil vendedor.",
    esperado: "O vendedor vê apenas as próprias vendas e a comissão acumulada (5% do valor vendido); a venda cancelada não conta.",
    executar: async () => {} });

  await irPara("venda");
  await pagina.click("#alternar-fechamento");
  await espera(300);
  await passo("25-vendedor-fecha-caixa", { titulo: "Vendedor fecha o próprio caixa", tipo: "sucesso",
    acao: "Na Frente de caixa, clicar em Fechar caixa e informar R$ 300,00 contados (200 de troco + 100 em dinheiro da venda dividida).",
    esperado: "HTTP 200; só a parte paga em dinheiro entra no esperado; diferença R$ 0,00 (caixa conferido).",
    executar: async () => { await preencher("#area-fechamento form", { valor_final_dinheiro: "300" }); await enviar("#area-fechamento form"); } });

  // ------------------------------------------------ perfil gerente
  await sair();
  await login(GERENTE);
  await irPara("vendas");
  const vendaConcluida = await pagina.$$eval("#tela tbody tr", linhas =>
    linhas.find(l => l.textContent.includes("CONCLUIDA"))?.querySelector("td").textContent.trim());
  await passo("26-cancelamento-sem-motivo", { titulo: "Cancelamento de venda sem motivo", tipo: "erro",
    acao: `Entrar como gerente (tela roxa) e, em Vendas, tentar cancelar a venda nº ${vendaConcluida} com o motivo "x".`,
    esperado: "HTTP 400: o motivo deve ter entre 5 e 255 caracteres.",
    executar: async () => {
      await abrirDetalhe(vendaConcluida);
      await preencher("#form-cancelar", { motivo_cancelamento: "x" });
      await enviar("#form-cancelar");
    } });

  await irPara("caixa");
  await preencher("#tela form", { suprimento_inicial: "300" });
  await enviar("#tela form");
  await passo("27-sangria-acima-do-saldo", { titulo: "Sangria maior que o dinheiro em caixa", tipo: "erro",
    acao: "Na aba Caixa (só do gerente), com o caixa aberto com R$ 300,00, registrar sangria de R$ 5.000,00.",
    esperado: "HTTP 422: não é possível retirar mais do que o saldo em dinheiro.",
    executar: async () => { await preencher('[data-mov="sangria"]', { valor: "5000", observacao: "Teste de retirada" }); await enviar('[data-mov="sangria"]'); } });

  await passo("28-sangria-sucesso", { titulo: "Sangria registrada", tipo: "sucesso",
    acao: "Registrar sangria de R$ 100,00 com a observação \"Depósito no cofre\".",
    esperado: "HTTP 201; o saldo em dinheiro do caixa cai para R$ 200,00.",
    executar: async () => { await preencher('[data-mov="sangria"]', { valor: "100", observacao: "Depósito no cofre" }); await enviar('[data-mov="sangria"]'); } });

  await passo("29-fechamento-caixa", { titulo: "Fechamento de caixa com diferença", tipo: "sucesso",
    acao: "Fechar o caixa informando R$ 190,00 contados em dinheiro (R$ 10,00 a menos que o esperado).",
    esperado: "HTTP 200; a aplicação mostra saldo esperado, valor contado e a diferença de R$ -10,00 (falta).",
    executar: async () => { await preencher("#tela form.cartao:last-of-type", { valor_final_dinheiro: "190" }); await enviar("#tela form.cartao:last-of-type"); } });

  await irPara("configuracoes");
  await passo("30-parcelamento-configurado", { titulo: "Gerente limita o parcelamento do crédito", tipo: "sucesso",
    acao: "Em Configurações, definir o crédito em até 6x sem juros e manter o crediário em 12x.",
    esperado: "HTTP 200; a alteração é auditada e a Frente de caixa passa a oferecer de 1x a 6x no crédito.",
    executar: async () => { await preencher("#form-parcelamento", { CARTAO_CREDITO: "6" }); await enviar("#form-parcelamento"); } });

  await irPara("usuarios");
  await passo("31-ficha-funcionario", { titulo: "Ficha do funcionário", tipo: "tela",
    acao: "Em Funcionários, clicar em \"Ver e editar\" no vendedor.",
    esperado: "Dados do vendedor, vendas concluídas, total vendido, comissão acumulada, últimas vendas e o formulário de edição.",
    executar: async () => {
      await pagina.evaluate(cpf => {
        const linha = [...document.querySelectorAll("#tela tbody tr")].find(l => l.textContent.replace(/\D/g, "").includes(cpf.replace(/\D/g, "")));
        linha.querySelector("[data-ver-usuario]").click();
      }, VENDEDOR.cpf);
      await pagina.waitForSelector("#form-edicao", { timeout: 15000 });
      await espera(500);
    } });

  await passo("32-funcionario-editado", { titulo: "Gerente altera comissão e redefine a senha", tipo: "sucesso",
    acao: "Na ficha do vendedor, mudar a comissão para 6% e definir uma nova senha.",
    esperado: "HTTP 200; a senha não é exibida nem auditada e as sessões abertas do vendedor são encerradas.",
    executar: async () => { await preencher("#form-edicao", { percentual_comissao: "6", nova_senha: "NovaSenha@2026" }); await enviar("#form-edicao"); } });

  await irPara("comissoes");
  await passo("33-comissoes-gerente", { titulo: "Relatório de comissões", tipo: "tela",
    acao: "Abrir Comissões com o perfil gerente.",
    esperado: "Quadro de todos os vendedores; a venda cancelada não entra no total.",
    executar: async () => {} });

  await irPara("auditoria");
  await passo("34-auditoria", { titulo: "Registro de auditoria", tipo: "tela",
    acao: "Abrir Auditoria (exclusivo do gerente).",
    esperado: "Lista das ações sensíveis: logins e falhas, cadastros, vendas com as formas de pagamento, desconto autorizado, cancelamento com autorização, sangria, fechamento, parcelamento e alteração de funcionário.",
    executar: async () => {} });

  await sair();
  await passo("35-logout", { titulo: "Logout", tipo: "sucesso",
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
