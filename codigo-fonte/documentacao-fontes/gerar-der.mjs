// Gera o SVG do Diagrama Entidade-Relacionamento a partir das colunas do esquema (migrações Flyway V1 e V3)
// e o insere no lugar do marcador <!--DER--> do arquivo 05-diagrama-entidade-relacionamento.html.
// Uso: node gerar-der.mjs   (rodar novamente sempre que o esquema mudar)
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const E = {
  usuarios: { x: 20, y: 10, cols: [["PK", "id", "BIGSERIAL"], ["UK", "cpf", "VARCHAR(11)"], ["", "nome", "VARCHAR(100)"], ["", "senha_hash", "VARCHAR(100)"], ["", "perfil", "VARCHAR(10)"], ["", "percentual_comissao", "NUMERIC(5,2)"], ["", "ativo", "BOOLEAN"], ["", "criado_em", "TIMESTAMPTZ"], ["", "atualizado_em", "TIMESTAMPTZ"]] },
  caixas: { x: 20, y: 195, cols: [["PK", "id", "BIGSERIAL"], ["FK", "usuario_id", "BIGINT"], ["", "status", "VARCHAR(7)"], ["", "suprimento_inicial", "NUMERIC(10,2)"], ["", "data_abertura", "TIMESTAMPTZ"], ["", "data_fechamento", "TIMESTAMPTZ"], ["", "valor_final_informado", "NUMERIC(10,2)"], ["", "saldo_esperado", "NUMERIC(10,2)"], ["", "diferenca", "NUMERIC(10,2)"]] },
  movimentacoes_caixa: { x: 20, y: 380, cols: [["PK", "id", "BIGSERIAL"], ["FK", "caixa_id", "BIGINT"], ["FK", "usuario_gerente_id", "BIGINT"], ["", "tipo", "VARCHAR(10)"], ["", "valor", "NUMERIC(10,2)"], ["", "observacao", "VARCHAR(255)"], ["", "data_hora", "TIMESTAMPTZ"]] },
  logs_auditoria: { x: 20, y: 540, cols: [["PK", "id", "BIGSERIAL"], ["FK", "usuario_id", "BIGINT"], ["", "data_hora", "TIMESTAMPTZ"], ["", "tipo_acao", "VARCHAR(40)"], ["", "entidade", "VARCHAR(40)"], ["", "entidade_id", "BIGINT"], ["", "detalhes", "VARCHAR(1000)"], ["", "valor_anterior", "TEXT"], ["", "valor_novo", "TEXT"]] },
  clientes: { x: 280, y: 10, cols: [["PK", "id", "BIGSERIAL"], ["", "tipo", "VARCHAR(2)"], ["UK", "cpf_cnpj", "VARCHAR(14)"], ["", "nome_razao_social", "VARCHAR(150)"], ["", "email", "VARCHAR(150)"], ["", "telefone", "VARCHAR(20)"], ["", "cep", "VARCHAR(8)"], ["", "endereco", "VARCHAR(200)"], ["", "bairro", "VARCHAR(100)"], ["", "cidade", "VARCHAR(100)"], ["", "uf", "VARCHAR(2)"], ["", "ativo", "BOOLEAN"], ["", "criado_em", "TIMESTAMPTZ"], ["", "atualizado_em", "TIMESTAMPTZ"]] },
  vendas: { x: 280, y: 255, cols: [["PK", "id", "BIGSERIAL"], ["FK", "usuario_id", "BIGINT"], ["FK", "cliente_id", "BIGINT"], ["FK", "caixa_id", "BIGINT"], ["FK", "cupom_id", "BIGINT"], ["FK", "gerente_autorizador_id", "BIGINT"], ["", "valor_subtotal", "NUMERIC(10,2)"], ["", "desconto_cupom", "NUMERIC(10,2)"], ["", "desconto_gerente", "NUMERIC(10,2)"], ["", "valor_total", "NUMERIC(10,2)"], ["", "modalidade_pagamento", "VARCHAR(15)"], ["", "status", "VARCHAR(10)"], ["", "percentual_comissao", "NUMERIC(5,2)"], ["", "valor_comissao", "NUMERIC(10,2)"], ["", "data_venda", "TIMESTAMPTZ"], ["", "motivo_cancelamento", "VARCHAR(255)"], ["", "data_cancelamento", "TIMESTAMPTZ"], ["FK", "usuario_cancelamento_id", "BIGINT"]] },
  itens_venda: { x: 280, y: 560, cols: [["PK", "id", "BIGSERIAL"], ["FK", "venda_id", "BIGINT"], ["FK", "produto_id", "BIGINT"], ["", "quantidade", "INTEGER"], ["", "preco_unitario", "NUMERIC(10,2)"], ["", "subtotal", "NUMERIC(10,2)"]] },
  cupons_desconto: { x: 540, y: 10, cols: [["PK", "id", "BIGSERIAL"], ["UK", "codigo", "VARCHAR(30)"], ["", "tipo_desconto", "VARCHAR(12)"], ["", "valor_desconto", "NUMERIC(10,2)"], ["", "data_inicio_validade", "TIMESTAMPTZ"], ["", "data_termino_validade", "TIMESTAMPTZ"], ["", "ativo", "BOOLEAN"], ["", "criado_em", "TIMESTAMPTZ"], ["", "atualizado_em", "TIMESTAMPTZ"]] },
  tokens_revogados: { x: 20, y: 715, cols: [["PK", "jti", "VARCHAR(64)"], ["", "expira_em", "TIMESTAMPTZ"], ["", "revogado_em", "TIMESTAMPTZ"]] },
  tentativas_acesso: { x: 280, y: 715, cols: [["PK", "chave", "VARCHAR(80)"], ["", "falhas", "INTEGER"], ["", "janela_inicio", "TIMESTAMPTZ"], ["", "bloqueado_ate", "TIMESTAMPTZ"]] },
  produtos: { x: 540, y: 470, cols: [["PK", "id", "BIGSERIAL"], ["UK", "sku", "VARCHAR(40)"], ["", "nome", "VARCHAR(120)"], ["", "fornecedor", "VARCHAR(120)"], ["", "unidade_medida", "VARCHAR(10)"], ["UK", "codigo_barras", "VARCHAR(14)"], ["", "ncm", "VARCHAR(8)"], ["", "cest", "VARCHAR(7)"], ["", "preco_venda", "NUMERIC(10,2)"], ["", "estoque_atual", "INTEGER"], ["", "estoque_minimo", "INTEGER"], ["", "ativo", "BOOLEAN"], ["", "criado_em", "TIMESTAMPTZ"], ["", "atualizado_em", "TIMESTAMPTZ"]] },
};

const LARGURA = 220, CABECALHO = 22, LINHA = 13;
const COR = { PK: "#b42318", FK: "#1f5a96", UK: "#1b8a5a" };

let entidades = "";
for (const [nome, e] of Object.entries(E)) {
  const h = CABECALHO + e.cols.length * LINHA + 6;
  entidades += `<g><rect x="${e.x}" y="${e.y}" width="${LARGURA}" height="${h}" rx="5" fill="#fff" stroke="#12355b" stroke-width="1.2"/>`;
  entidades += `<path d="M${e.x} ${e.y + 5} a5 5 0 0 1 5 -5 h${LARGURA - 10} a5 5 0 0 1 5 5 v${CABECALHO - 5} h-${LARGURA} z" fill="#12355b"/>`;
  entidades += `<text x="${e.x + LARGURA / 2}" y="${e.y + 15}" text-anchor="middle" font-size="11" font-weight="700" fill="#fff">${nome}</text>`;
  e.cols.forEach(([chave, coluna, tipo], i) => {
    const y = e.y + CABECALHO + LINHA * i + 10;
    if (i % 2 === 1) entidades += `<rect x="${e.x + 1}" y="${y - 9.5}" width="${LARGURA - 2}" height="${LINHA}" fill="#f4f7fa"/>`;
    if (chave) entidades += `<text x="${e.x + 6}" y="${y}" font-size="7.5" font-weight="700" fill="${COR[chave]}">${chave}</text>`;
    entidades += `<text x="${e.x + 26}" y="${y}" font-size="9" fill="#1d2733"${chave === "PK" ? ' font-weight="700"' : ""}>${coluna}</text>`;
    entidades += `<text x="${e.x + LARGURA - 6}" y="${y}" font-size="8" fill="#5b6573" text-anchor="end">${tipo}</text>`;
  });
  entidades += "</g>";
}

// Relacionamentos: o caminho sai do lado "1" (traços duplos) e chega no lado "N" (pé de galinha)
const RELACOES = [
  { d: "M130 155 V195", rotulo: [136, 178] },                         // usuarios → caixas
  { d: "M130 340 V380", rotulo: [136, 363] },                         // caixas → movimentacoes_caixa
  { d: "M20 120 H8 V440 H20", semInicio: false },                     // usuarios → movimentacoes (gerente)
  { d: "M8 440 V610 H20", semInicioMarcador: true },                  // ramo → logs_auditoria
  { d: "M240 70 C262 70 258 300 280 300", rotulo: [244, 185] },      // usuarios → vendas
  { d: "M240 260 C262 260 258 340 280 340", rotulo: [244, 315] },    // caixas → vendas
  { d: "M390 220 V255", rotulo: [396, 241] },                         // clientes → vendas
  { d: "M540 90 C515 90 525 360 500 360", rotulo: [503, 215] },      // cupons → vendas
  { d: "M390 517 V560", rotulo: [396, 542] },                         // vendas → itens
  { d: "M540 620 H500", rotulo: [507, 614] },                         // produtos → itens
];
let relacoes = "";
for (const r of RELACOES) {
  const inicio = r.semInicioMarcador ? "" : ' marker-start="url(#um)"';
  relacoes += `<path d="${r.d}" fill="none" stroke="#33404f" stroke-width="1.2"${inicio} marker-end="url(#muitos)"/>`;
  if (r.rotulo) relacoes += `<text x="${r.rotulo[0]}" y="${r.rotulo[1]}" font-size="8" fill="#33404f">1 : N</text>`;
}
relacoes += `<text x="5" y="330" font-size="8" fill="#33404f" text-anchor="middle" transform="rotate(-90 5 330)">gerente da movimentação · autor do log</text>`;

const legenda = `<g transform="translate(548,175)"><rect width="205" height="262" rx="6" fill="#f4f7fa" stroke="#d8dee6"/>
<text x="12" y="22" font-size="10.5" font-weight="700" fill="#12355b">Legenda</text>
<text x="12" y="44" font-size="8.5" font-weight="700" fill="#b42318">PK</text><text x="34" y="44" font-size="8.5" fill="#1d2733">chave primária</text>
<text x="12" y="60" font-size="8.5" font-weight="700" fill="#1f5a96">FK</text><text x="34" y="60" font-size="8.5" fill="#1d2733">chave estrangeira</text>
<text x="12" y="76" font-size="8.5" font-weight="700" fill="#1b8a5a">UK</text><text x="34" y="76" font-size="8.5" fill="#1d2733">valor único</text>
<path d="M14 98 H62" stroke="#33404f" stroke-width="1.2" fill="none" marker-start="url(#um)" marker-end="url(#muitos)"/>
<text x="70" y="101" font-size="8.5" fill="#1d2733">um (1) para muitos (N)</text>
<text x="12" y="126" font-size="8.5" font-weight="700" fill="#12355b">vendas → usuarios (3 FKs)</text>
<text x="12" y="140" font-size="8.2" fill="#33404f">usuario_id: vendedor</text>
<text x="12" y="153" font-size="8.2" fill="#33404f">gerente_autorizador_id: desconto</text>
<text x="12" y="166" font-size="8.2" fill="#33404f">usuario_cancelamento_id: cancelou</text>
<text x="12" y="190" font-size="8.5" font-weight="700" fill="#12355b">FKs opcionais (aceitam nulo)</text>
<text x="12" y="204" font-size="8.2" fill="#33404f">vendas.cupom_id</text>
<text x="12" y="217" font-size="8.2" fill="#33404f">vendas.gerente_autorizador_id</text>
<text x="12" y="230" font-size="8.2" fill="#33404f">vendas.usuario_cancelamento_id</text>
<text x="12" y="243" font-size="8.2" fill="#33404f">logs_auditoria.usuario_id</text></g>`;

const marcadores = `<defs>
<marker id="um" viewBox="0 0 12 12" refX="1" refY="6" markerWidth="11" markerHeight="11" orient="auto-start-reverse" markerUnits="userSpaceOnUse"><path d="M4 1 V11 M7.5 1 V11" stroke="#33404f" stroke-width="1.2" fill="none"/></marker>
<marker id="muitos" viewBox="0 0 12 12" refX="11" refY="6" markerWidth="12" markerHeight="12" orient="auto" markerUnits="userSpaceOnUse"><path d="M1 6 L11 1 M1 6 L11 6 M1 6 L11 11" stroke="#33404f" stroke-width="1.2" fill="none"/></marker></defs>`;

const seguranca = `<line x1="20" y1="702" x2="750" y2="702" stroke="#d8dee6" stroke-dasharray="4 3"/>
<g transform="translate(540,715)"><text x="0" y="12" font-size="9.5" font-weight="700" fill="#12355b">Tabelas de segurança (migração V3)</text>
<text x="0" y="28" font-size="8.2" fill="#33404f">Sem chaves estrangeiras: guardam</text>
<text x="0" y="41" font-size="8.2" fill="#33404f">tokens revogados (logout/renovação) e</text>
<text x="0" y="54" font-size="8.2" fill="#33404f">tentativas erradas por CPF, IP ou</text>
<text x="0" y="67" font-size="8.2" fill="#33404f">vendedor (chave "cpf:…", "ip:…", "pin:…").</text></g>`;

const svg = `<svg viewBox="0 0 770 805" width="100%"><!--DER-INICIO-->${marcadores}${relacoes}${entidades}${legenda}${seguranca}<!--DER-FIM--></svg>`;
const arquivo = path.join(path.dirname(fileURLToPath(import.meta.url)), "05-diagrama-entidade-relacionamento.html");
const html = fs.readFileSync(arquivo, "utf8");
const atualizado = html.includes("<!--DER-INICIO-->")
  ? html.replace(/<svg viewBox="0 0 770 \d+" width="100%"><!--DER-INICIO-->[\s\S]*?<!--DER-FIM--><\/svg>/, svg)
  : html.replace("<!--DER-->", svg);
fs.writeFileSync(arquivo, atualizado);
console.log("Diagrama ER atualizado em", path.basename(arquivo));
