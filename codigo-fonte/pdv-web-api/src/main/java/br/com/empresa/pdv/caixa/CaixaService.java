package br.com.empresa.pdv.caixa;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.caixa.CaixaDtos.AberturaResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.CaixaResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.FechamentoResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.SangriaResposta;
import br.com.empresa.pdv.caixa.CaixaDtos.SuprimentoResposta;
import br.com.empresa.pdv.comum.Dinheiro;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.seguranca.UsuarioLogado;
import br.com.empresa.pdv.venda.ModalidadePagamento;
import br.com.empresa.pdv.venda.VendaRepository;

@Service
public class CaixaService {

    private final CaixaRepository caixas;
    private final MovimentacaoCaixaRepository movimentacoes;
    private final VendaRepository vendas;
    private final AuditoriaService auditoria;
    private final Relogio relogio;

    public CaixaService(CaixaRepository caixas, MovimentacaoCaixaRepository movimentacoes, VendaRepository vendas,
                        AuditoriaService auditoria, Relogio relogio) {
        this.caixas = caixas;
        this.movimentacoes = movimentacoes;
        this.vendas = vendas;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    /** UC02: abre o caixa do operador logado com o fundo de troco informado. */
    @Transactional
    public AberturaResposta abrir(BigDecimal suprimentoInicial) {
        UsuarioLogado logado = UsuarioLogado.atual();
        if (caixas.existsByUsuarioIdAndStatus(logado.id(), StatusCaixa.ABERTO)) {
            throw new ApiException(HttpStatus.CONFLICT, "caixa-ja-aberto",
                    "Caixa Já Aberto", "O operador já possui um caixa aberto. Feche-o antes de abrir outro.");
        }
        Caixa caixa = caixas.save(new Caixa(logado.id(), Dinheiro.arredondar(suprimentoInicial), relogio.agora()));
        auditoria.registrar(logado.id(), TipoAcao.CAIXA_ABERTURA, "CAIXA", caixa.getId(),
                "Caixa aberto com suprimento inicial de R$ " + caixa.getSuprimentoInicial() + ".");
        return new AberturaResposta(caixa.getId(), relogio.noFuso(caixa.getDataAbertura()),
                caixa.getSuprimentoInicial(), caixa.getStatus());
    }

    @Transactional(readOnly = true)
    public CaixaResposta atual() {
        UsuarioLogado logado = UsuarioLogado.atual();
        Caixa caixa = caixas.findByUsuarioIdAndStatus(logado.id(), StatusCaixa.ABERTO)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "nao-encontrado",
                        "Recurso Não Encontrado", "O operador não possui caixa aberto."));
        return resposta(caixa);
    }

    @Transactional(readOnly = true)
    public CaixaResposta buscar(Long id) {
        Caixa caixa = obter(id);
        verificarAcesso(caixa, UsuarioLogado.atual());
        return resposta(caixa);
    }

    /** UC15: retirada de espécie. Não pode exceder o dinheiro existente no caixa. */
    @Transactional
    public SangriaResposta sangria(Long caixaId, BigDecimal valor, String observacao) {
        UsuarioLogado gerente = UsuarioLogado.atual();
        Caixa caixa = bloquearAberto(caixaId);
        BigDecimal valorSangria = Dinheiro.arredondar(valor);
        BigDecimal saldo = saldoEmDinheiro(caixa);
        if (valorSangria.compareTo(saldo) > 0) {
            throw ApiException.regraNegocio("saldo-caixa-insuficiente", "Saldo do Caixa Insuficiente",
                    "A sangria de R$ " + valorSangria + " supera o saldo em dinheiro do caixa (R$ " + saldo + ").");
        }
        MovimentacaoCaixa mov = movimentacoes.save(new MovimentacaoCaixa(caixa.getId(), gerente.id(),
                TipoMovimentacao.SANGRIA, valorSangria, observacao.trim(), relogio.agora()));
        auditoria.registrar(gerente.id(), TipoAcao.SANGRIA, "CAIXA", caixa.getId(),
                "Sangria de R$ " + valorSangria + " no caixa " + caixa.getId() + ": " + mov.getObservacao(),
                saldo.toPlainString(), saldo.subtract(valorSangria).toPlainString());
        return new SangriaResposta(mov.getId(), caixa.getId(), mov.getValor(), gerente.id(),
                relogio.noFuso(mov.getDataHora()), saldo.subtract(valorSangria));
    }

    /** UC15: entrada de espécie (reforço de troco). Extensão ADR-001 D-10. */
    @Transactional
    public SuprimentoResposta suprimento(Long caixaId, BigDecimal valor, String observacao) {
        UsuarioLogado gerente = UsuarioLogado.atual();
        Caixa caixa = bloquearAberto(caixaId);
        BigDecimal valorSuprimento = Dinheiro.arredondar(valor);
        BigDecimal saldo = saldoEmDinheiro(caixa);
        MovimentacaoCaixa mov = movimentacoes.save(new MovimentacaoCaixa(caixa.getId(), gerente.id(),
                TipoMovimentacao.SUPRIMENTO, valorSuprimento, observacao.trim(), relogio.agora()));
        auditoria.registrar(gerente.id(), TipoAcao.SUPRIMENTO, "CAIXA", caixa.getId(),
                "Suprimento de R$ " + valorSuprimento + " no caixa " + caixa.getId() + ": " + mov.getObservacao(),
                saldo.toPlainString(), saldo.add(valorSuprimento).toPlainString());
        return new SuprimentoResposta(mov.getId(), caixa.getId(), mov.getValor(), gerente.id(),
                relogio.noFuso(mov.getDataHora()), saldo.add(valorSuprimento));
    }

    /** UC03: o operador informa o dinheiro contado; o sistema calcula o esperado e a diferença. */
    @Transactional
    public FechamentoResposta fechar(Long caixaId, BigDecimal valorFinalDinheiro) {
        UsuarioLogado logado = UsuarioLogado.atual();
        Caixa caixa = caixas.bloquear(caixaId).orElseThrow(() -> ApiException.naoEncontrado("Caixa", caixaId));
        verificarAcesso(caixa, logado);
        if (!caixa.isAberto()) {
            throw caixaFechado(caixaId);
        }
        Totais totais = totais(caixa);
        caixa.fechar(Dinheiro.arredondar(valorFinalDinheiro), totais.saldoDinheiro(), relogio.agora());
        auditoria.registrar(logado.id(), TipoAcao.CAIXA_FECHAMENTO, "CAIXA", caixa.getId(),
                "Caixa fechado. Esperado R$ " + caixa.getSaldoEsperado() + ", informado R$ "
                        + caixa.getValorFinalInformado() + ", diferença R$ " + caixa.getDiferenca() + ".");
        return new FechamentoResposta(caixa.getId(), caixa.getStatus(), totais.vendas(), totais.vendasDinheiro(),
                totais.sangrias(), totais.suprimentos(), caixa.getSaldoEsperado(), caixa.getValorFinalInformado(),
                caixa.getDiferenca(), relogio.noFuso(caixa.getDataFechamento()));
    }

    /**
     * VAL-05: usado pelo registro de venda. O caixa precisa existir, estar ABERTO e pertencer ao operador.
     * O bloqueio da linha impede que o caixa seja fechado no meio da venda.
     */
    @Transactional
    public Caixa validarCaixaParaVenda(Long caixaId, Long operadorId) {
        Caixa caixa = caixas.bloquear(caixaId).orElseThrow(() -> ApiException.naoEncontrado("Caixa", caixaId));
        if (!caixa.pertenceA(operadorId)) {
            throw ApiException.acessoNegado("O caixa informado pertence a outro operador.");
        }
        if (!caixa.isAberto()) {
            throw caixaFechado(caixaId);
        }
        return caixa;
    }

    BigDecimal saldoEmDinheiro(Caixa caixa) {
        return totais(caixa).saldoDinheiro();
    }

    private Totais totais(Caixa caixa) {
        BigDecimal vendasTotal = Dinheiro.arredondar(vendas.somarConcluidasDoCaixa(caixa.getId()));
        BigDecimal vendasDinheiro = Dinheiro.arredondar(
                vendas.somarConcluidasDoCaixaPorModalidade(caixa.getId(), ModalidadePagamento.DINHEIRO));
        BigDecimal sangrias = Dinheiro.arredondar(movimentacoes.somarPorTipo(caixa.getId(), TipoMovimentacao.SANGRIA));
        BigDecimal suprimentos = Dinheiro.arredondar(
                movimentacoes.somarPorTipo(caixa.getId(), TipoMovimentacao.SUPRIMENTO));
        BigDecimal saldo = caixa.getSuprimentoInicial().add(suprimentos).add(vendasDinheiro).subtract(sangrias);
        return new Totais(vendasTotal, vendasDinheiro, sangrias, suprimentos, saldo);
    }

    private Caixa bloquearAberto(Long caixaId) {
        Caixa caixa = caixas.bloquear(caixaId).orElseThrow(() -> ApiException.naoEncontrado("Caixa", caixaId));
        if (!caixa.isAberto()) {
            throw caixaFechado(caixaId);
        }
        return caixa;
    }

    private Caixa obter(Long id) {
        return caixas.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Caixa", id));
    }

    /** Vendedor só enxerga/fecha o próprio caixa; gerente acessa qualquer um. */
    private static void verificarAcesso(Caixa caixa, UsuarioLogado logado) {
        if (!logado.isGerente() && !caixa.pertenceA(logado.id())) {
            throw ApiException.acessoNegado("O caixa informado pertence a outro operador.");
        }
    }

    private static ApiException caixaFechado(Long caixaId) {
        return ApiException.regraNegocio("caixa-fechado", "Caixa Fechado",
                "O caixa " + caixaId + " não está aberto. Realize a abertura de caixa do turno (VAL-05).");
    }

    private CaixaResposta resposta(Caixa caixa) {
        Totais t = totais(caixa);
        return new CaixaResposta(caixa.getId(), caixa.getUsuarioId(), caixa.getStatus(),
                relogio.noFuso(caixa.getDataAbertura()), caixa.getSuprimentoInicial(), t.vendas(), t.vendasDinheiro(),
                t.sangrias(), t.suprimentos(), t.saldoDinheiro(), relogio.noFuso(caixa.getDataFechamento()),
                caixa.getValorFinalInformado(), caixa.getDiferenca());
    }

    private record Totais(BigDecimal vendas, BigDecimal vendasDinheiro, BigDecimal sangrias, BigDecimal suprimentos,
                          BigDecimal saldoDinheiro) {
    }
}
