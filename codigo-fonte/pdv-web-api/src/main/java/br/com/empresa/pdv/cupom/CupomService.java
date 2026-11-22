package br.com.empresa.pdv.cupom;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.comum.Dinheiro;
import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.comum.Paginacao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.cupom.CupomDtos.CupomPatch;
import br.com.empresa.pdv.cupom.CupomDtos.CupomRequisicao;
import br.com.empresa.pdv.cupom.CupomDtos.CupomResposta;
import br.com.empresa.pdv.seguranca.UsuarioLogado;

@Service
public class CupomService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final CupomRepository repository;
    private final AuditoriaService auditoria;
    private final Relogio relogio;

    public CupomService(CupomRepository repository, AuditoriaService auditoria, Relogio relogio) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional
    public CupomResposta emitir(CupomRequisicao dados) {
        String codigo = dados.codigo().toUpperCase(Locale.ROOT);
        if (repository.existsByCodigo(codigo)) {
            throw ApiException.conflito("Já existe um cupom com este código.", "codigo", "Código já cadastrado.");
        }
        validarRegras(dados.tipoDesconto(), dados.valorDesconto(), dados.dataInicioValidade(),
                dados.dataTerminoValidade());
        Cupom cupom = new Cupom(codigo, relogio.agora());
        cupom.setTipoDesconto(dados.tipoDesconto());
        cupom.setValorDesconto(Dinheiro.arredondar(dados.valorDesconto()));
        cupom.setDataInicioValidade(dados.dataInicioValidade());
        cupom.setDataTerminoValidade(dados.dataTerminoValidade());
        cupom = repository.save(cupom);
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.CUPOM_CREATE, "CUPOM", cupom.getId(),
                "Cupom " + cupom.getCodigo() + " emitido: " + cupom.getTipoDesconto() + " " + cupom.getValorDesconto() + ".");
        return resposta(cupom);
    }

    @Transactional(readOnly = true)
    public Pagina<CupomResposta> listar(Integer pagina, Integer limite, String codigo, Boolean vigente) {
        OffsetDateTime agora = relogio.agora();
        Specification<Cupom> filtro = (raiz, consulta, cb) -> cb.conjunction();
        if (codigo != null && !codigo.isBlank()) {
            String codigoNormalizado = codigo.trim().toUpperCase(Locale.ROOT);
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("codigo"), codigoNormalizado));
        }
        if (vigente != null) {
            Specification<Cupom> condicaoVigente = (raiz, consulta, cb) -> cb.and(
                    cb.isTrue(raiz.get("ativo")),
                    cb.lessThanOrEqualTo(raiz.get("dataInicioValidade"), agora),
                    cb.greaterThanOrEqualTo(raiz.get("dataTerminoValidade"), agora));
            filtro = filtro.and(vigente ? condicaoVigente : Specification.not(condicaoVigente));
        }
        var paginacao = Paginacao.de(pagina, limite, Sort.by(Sort.Direction.DESC, "dataInicioValidade").and(Sort.by("id")));
        return Pagina.de(repository.findAll(filtro, paginacao), this::resposta);
    }

    @Transactional(readOnly = true)
    public CupomResposta buscar(Long id) {
        return resposta(obter(id));
    }

    @Transactional
    public CupomResposta substituir(Long id, CupomRequisicao dados) {
        Cupom cupom = obterAtivo(id);
        return aplicar(cupom, dados.codigo(), dados.tipoDesconto(), dados.valorDesconto(),
                dados.dataInicioValidade(), dados.dataTerminoValidade());
    }

    @Transactional
    public CupomResposta atualizarParcial(Long id, CupomPatch dados) {
        Cupom cupom = obterAtivo(id);
        return aplicar(cupom,
                dados.codigo() != null ? dados.codigo() : cupom.getCodigo(),
                dados.tipoDesconto() != null ? dados.tipoDesconto() : cupom.getTipoDesconto(),
                dados.valorDesconto() != null ? dados.valorDesconto() : cupom.getValorDesconto(),
                dados.dataInicioValidade() != null ? dados.dataInicioValidade() : cupom.getDataInicioValidade(),
                dados.dataTerminoValidade() != null ? dados.dataTerminoValidade() : cupom.getDataTerminoValidade());
    }

    @Transactional
    public void inativar(Long id) {
        Cupom cupom = obterAtivo(id);
        cupom.setAtivo(false);
        cupom.setAtualizadoEm(relogio.agora());
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.CUPOM_INATIVACAO, "CUPOM", id,
                "Cupom " + cupom.getCodigo() + " inativado.");
    }

    /** UC06: localiza um cupom utilizável numa venda (existente, ativo e dentro da vigência). */
    @Transactional(readOnly = true)
    public Cupom obterVigente(String codigo) {
        OffsetDateTime agora = relogio.agora();
        return repository.findByCodigo(codigo.trim().toUpperCase(Locale.ROOT))
                .filter(c -> c.isVigente(agora))
                .orElseThrow(() -> ApiException.regraNegocio("cupom-invalido", "Cupom Inválido",
                        "O cupom '" + codigo + "' não existe, está inativo ou fora do período de validade."));
    }

    private CupomResposta aplicar(Cupom cupom, String codigo, TipoDesconto tipo, BigDecimal valor,
                                  OffsetDateTime inicio, OffsetDateTime termino) {
        String codigoNormalizado = codigo.toUpperCase(Locale.ROOT);
        if (repository.existsByCodigoAndIdNot(codigoNormalizado, cupom.getId())) {
            throw ApiException.conflito("Já existe um cupom com este código.", "codigo", "Código já cadastrado.");
        }
        validarRegras(tipo, valor, inicio, termino);
        String antes = descrever(cupom);
        cupom.setCodigo(codigoNormalizado);
        cupom.setTipoDesconto(tipo);
        cupom.setValorDesconto(Dinheiro.arredondar(valor));
        cupom.setDataInicioValidade(inicio);
        cupom.setDataTerminoValidade(termino);
        cupom.setAtualizadoEm(relogio.agora());
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.CUPOM_UPDATE, "CUPOM", cupom.getId(),
                "Cupom " + cupom.getCodigo() + " alterado.", antes, descrever(cupom));
        return resposta(cupom);
    }

    static void validarRegras(TipoDesconto tipo, BigDecimal valor, OffsetDateTime inicio, OffsetDateTime termino) {
        if (tipo == TipoDesconto.PERCENTUAL && valor.compareTo(CEM) > 0) {
            throw ApiException.validacao("valor_desconto", "Desconto percentual não pode ser maior que 100.");
        }
        if (!termino.isAfter(inicio)) {
            throw ApiException.validacao("data_termino_validade", "Deve ser posterior à data de início.");
        }
    }

    private Cupom obter(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Cupom", id));
    }

    private Cupom obterAtivo(Long id) {
        Cupom cupom = obter(id);
        if (!cupom.isAtivo()) {
            throw ApiException.naoEncontrado("Cupom", id);
        }
        return cupom;
    }

    private static String descrever(Cupom c) {
        return "codigo=" + c.getCodigo() + "; tipo=" + c.getTipoDesconto() + "; valor=" + c.getValorDesconto()
                + "; inicio=" + c.getDataInicioValidade() + "; termino=" + c.getDataTerminoValidade();
    }

    private CupomResposta resposta(Cupom c) {
        return new CupomResposta(c.getId(), c.getCodigo(), c.getTipoDesconto(), c.getValorDesconto(),
                relogio.noFuso(c.getDataInicioValidade()), relogio.noFuso(c.getDataTerminoValidade()),
                c.isVigente(relogio.agora()), c.isAtivo());
    }
}
