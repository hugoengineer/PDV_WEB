package br.com.empresa.pdv.venda;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.seguranca.UsuarioLogado;
import br.com.empresa.pdv.venda.VendaDtos.ParcelamentoResposta;

/** UC17 — Configurar parcelamento: o gerente define o máximo de parcelas sem juros por forma de pagamento. */
@Service
public class ParcelamentoService {

    private final ConfiguracaoParcelamentoRepository repository;
    private final AuditoriaService auditoria;
    private final Relogio relogio;

    public ParcelamentoService(ConfiguracaoParcelamentoRepository repository, AuditoriaService auditoria,
                               Relogio relogio) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public List<ParcelamentoResposta> listar() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getModalidadePagamento().compareTo(b.getModalidadePagamento()))
                .map(this::resposta)
                .toList();
    }

    @Transactional
    public ParcelamentoResposta alterar(ModalidadePagamento modalidade, int maxParcelas) {
        if (!modalidade.permiteParcelamento()) {
            throw ApiException.regraNegocio("parcelamento-invalido", "Parcelamento Inválido",
                    modalidade + " é sempre à vista; só CARTAO_CREDITO e CREDIARIO podem ser parcelados.");
        }
        ConfiguracaoParcelamento config = repository.findById(modalidade)
                .orElseThrow(() -> ApiException.naoEncontrado("Configuração de parcelamento", modalidade));
        int anterior = config.getMaxParcelas();
        Long gerente = UsuarioLogado.atual().id();
        config.alterar(maxParcelas, gerente, relogio.agora());
        auditoria.registrar(gerente, TipoAcao.CONFIGURACAO_PARCELAMENTO, "PARCELAMENTO", null,
                "Máximo de parcelas sem juros de " + modalidade + " alterado de " + anterior + "x para "
                        + maxParcelas + "x.", anterior + "x", maxParcelas + "x");
        return resposta(config);
    }

    private ParcelamentoResposta resposta(ConfiguracaoParcelamento c) {
        return new ParcelamentoResposta(c.getModalidadePagamento(), c.getMaxParcelas(),
                relogio.noFuso(c.getAtualizadoEm()), c.getAtualizadoPor());
    }
}
