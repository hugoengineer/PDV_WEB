package br.com.empresa.pdv.produto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.comum.Dinheiro;
import br.com.empresa.pdv.comum.Documentos;
import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.comum.Paginacao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoPatch;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoRequisicao;
import br.com.empresa.pdv.produto.ProdutoDtos.ProdutoResposta;
import br.com.empresa.pdv.seguranca.UsuarioLogado;

@Service
public class ProdutoService {

    private final ProdutoRepository repository;
    private final AuditoriaService auditoria;
    private final Relogio relogio;

    public ProdutoService(ProdutoRepository repository, AuditoriaService auditoria, Relogio relogio) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional
    public ProdutoResposta cadastrar(ProdutoRequisicao dados) {
        String sku = dados.sku().trim().toUpperCase(Locale.ROOT);
        if (repository.existsBySku(sku)) {
            throw ApiException.conflito("Já existe um produto com este SKU.", "sku", "SKU já cadastrado.");
        }
        if (dados.codigoBarras() != null && repository.existsByCodigoBarras(dados.codigoBarras())) {
            throw ApiException.conflito("Já existe um produto com este código de barras.", "codigo_barras",
                    "Código de barras já cadastrado.");
        }
        Produto produto = new Produto(sku, relogio.agora());
        preencher(produto, dados);
        produto = repository.save(produto);
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.PRODUTO_CREATE, "PRODUTO", produto.getId(),
                "Produto " + produto.getSku() + " cadastrado com preço R$ " + produto.getPrecoVenda()
                        + " e estoque " + produto.getEstoqueAtual() + ".");
        return resposta(produto);
    }

    @Transactional(readOnly = true)
    public Pagina<ProdutoResposta> listar(Integer pagina, Integer limite, String sku, String nome,
                                          Boolean estoqueCritico, Boolean incluirInativos) {
        Specification<Produto> filtro = (raiz, consulta, cb) -> cb.conjunction();
        if (!Boolean.TRUE.equals(incluirInativos)) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.isTrue(raiz.get("ativo")));
        }
        if (sku != null && !sku.isBlank()) {
            String skuNormalizado = sku.trim().toUpperCase(Locale.ROOT);
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("sku"), skuNormalizado));
        }
        if (nome != null && !nome.isBlank()) {
            String padrao = "%" + nome.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            filtro = filtro.and((raiz, consulta, cb) -> cb.like(cb.lower(raiz.get("nome")), padrao, '\\'));
        }
        if (Boolean.TRUE.equals(estoqueCritico)) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.le(raiz.get("estoqueAtual"), raiz.<Integer>get("estoqueMinimo")));
        }
        var paginacao = Paginacao.de(pagina, limite, Sort.by("nome", "id"));
        return Pagina.de(repository.findAll(filtro, paginacao), this::resposta);
    }

    @Transactional(readOnly = true)
    public ProdutoResposta buscar(Long id) {
        return resposta(obter(id));
    }

    @Transactional
    public ProdutoResposta substituir(Long id, ProdutoRequisicao dados) {
        Produto produto = obterAtivo(id);
        String sku = dados.sku().trim().toUpperCase(Locale.ROOT);
        validarUnicidade(id, sku, dados.codigoBarras());
        Instantaneo antes = new Instantaneo(produto);
        produto.setSku(sku);
        preencher(produto, dados);
        return concluirAlteracao(produto, antes);
    }

    @Transactional
    public ProdutoResposta atualizarParcial(Long id, ProdutoPatch dados) {
        Produto produto = obterAtivo(id);
        String sku = dados.sku() == null ? null : dados.sku().trim().toUpperCase(Locale.ROOT);
        validarUnicidade(id, sku, dados.codigoBarras());
        Instantaneo antes = new Instantaneo(produto);
        if (sku != null) {
            produto.setSku(sku);
        }
        if (dados.nome() != null) {
            produto.setNome(dados.nome().trim());
        }
        if (dados.fornecedor() != null) {
            produto.setFornecedor(textoOuNulo(dados.fornecedor()));
        }
        if (dados.unidadeMedida() != null) {
            produto.setUnidadeMedida(dados.unidadeMedida().toUpperCase(Locale.ROOT));
        }
        if (dados.codigoBarras() != null) {
            produto.setCodigoBarras(dados.codigoBarras());
        }
        if (dados.ncm() != null) {
            produto.setNcm(normalizarCodigoFiscal(dados.ncm(), 8, "ncm"));
        }
        if (dados.cest() != null) {
            produto.setCest(normalizarCodigoFiscal(dados.cest(), 7, "cest"));
        }
        if (dados.precoVenda() != null) {
            produto.setPrecoVenda(Dinheiro.arredondar(dados.precoVenda()));
        }
        if (dados.estoqueAtual() != null) {
            produto.setEstoqueAtual(dados.estoqueAtual());
        }
        if (dados.estoqueMinimo() != null) {
            produto.setEstoqueMinimo(dados.estoqueMinimo());
        }
        return concluirAlteracao(produto, antes);
    }

    @Transactional
    public void inativar(Long id) {
        Produto produto = obterAtivo(id);
        produto.setAtivo(false);
        produto.setAtualizadoEm(relogio.agora());
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.PRODUTO_INATIVACAO, "PRODUTO", id,
                "Produto " + produto.getSku() + " inativado (soft delete).");
    }

    private void preencher(Produto produto, ProdutoRequisicao dados) {
        produto.setNome(dados.nome().trim());
        produto.setFornecedor(textoOuNulo(dados.fornecedor()));
        produto.setUnidadeMedida(dados.unidadeMedida().toUpperCase(Locale.ROOT));
        produto.setCodigoBarras(dados.codigoBarras());
        produto.setNcm(normalizarCodigoFiscal(dados.ncm(), 8, "ncm"));
        produto.setCest(normalizarCodigoFiscal(dados.cest(), 7, "cest"));
        produto.setPrecoVenda(Dinheiro.arredondar(dados.precoVenda()));
        produto.setEstoqueAtual(dados.estoqueAtual());
        produto.setEstoqueMinimo(dados.estoqueMinimo());
    }

    private ProdutoResposta concluirAlteracao(Produto produto, Instantaneo antes) {
        produto.setAtualizadoEm(relogio.agora());
        Long usuarioId = UsuarioLogado.atual().id();
        List<String> mudancas = new ArrayList<>();
        if (antes.preco.compareTo(produto.getPrecoVenda()) != 0) {
            mudancas.add("Preço alterado de R$ " + antes.preco + " para R$ " + produto.getPrecoVenda());
        }
        if (!antes.nome.equals(produto.getNome())) {
            mudancas.add("Nome alterado de '" + antes.nome + "' para '" + produto.getNome() + "'");
        }
        String detalhes = (mudancas.isEmpty() ? "Cadastro atualizado" : String.join("; ", mudancas))
                + " no produto ID " + produto.getId() + ".";
        auditoria.registrar(usuarioId, TipoAcao.PRODUTO_UPDATE, "PRODUTO", produto.getId(), detalhes,
                antes.descricao, Instantaneo.descrever(produto));
        if (antes.estoque != produto.getEstoqueAtual()) {
            auditoria.registrar(usuarioId, TipoAcao.ESTOQUE_AJUSTE, "PRODUTO", produto.getId(),
                    "Estoque ajustado manualmente de " + antes.estoque + " para " + produto.getEstoqueAtual()
                            + " no produto ID " + produto.getId() + ".",
                    String.valueOf(antes.estoque), String.valueOf(produto.getEstoqueAtual()));
        }
        return resposta(produto);
    }

    private void validarUnicidade(Long id, String sku, String codigoBarras) {
        if (sku != null && repository.existsBySkuAndIdNot(sku, id)) {
            throw ApiException.conflito("Já existe um produto com este SKU.", "sku", "SKU já cadastrado.");
        }
        if (codigoBarras != null && repository.existsByCodigoBarrasAndIdNot(codigoBarras, id)) {
            throw ApiException.conflito("Já existe um produto com este código de barras.", "codigo_barras",
                    "Código de barras já cadastrado.");
        }
    }

    /** Aceita NCM/CEST com ou sem pontuação ("8471.60.52" ou "84716052"); grava só os dígitos. */
    static String normalizarCodigoFiscal(String valor, int digitos, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        if (!valor.matches("[\\d.\\s-]+")) {
            throw ApiException.validacao(campo, "Deve conter apenas dígitos (pontuação opcional).");
        }
        String somenteDigitos = Documentos.somenteDigitos(valor);
        if (somenteDigitos.length() != digitos) {
            throw ApiException.validacao(campo, "Deve conter exatamente " + digitos + " dígitos.");
        }
        return somenteDigitos;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    Produto obter(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Produto", id));
    }

    private Produto obterAtivo(Long id) {
        Produto produto = obter(id);
        if (!produto.isAtivo()) {
            throw ApiException.naoEncontrado("Produto", id);
        }
        return produto;
    }

    ProdutoResposta resposta(Produto p) {
        return new ProdutoResposta(p.getId(), p.getSku(), p.getNome(), p.getFornecedor(), p.getUnidadeMedida(),
                p.getCodigoBarras(), p.getNcm(), p.getCest(), p.getPrecoVenda(), p.getEstoqueAtual(),
                p.getEstoqueMinimo(), p.isEstoqueCritico(), p.isAtivo(), relogio.noFuso(p.getAtualizadoEm()));
    }

    /** Estado anterior do produto, para o registro de auditoria com valores anterior/atual. */
    private static final class Instantaneo {
        private final BigDecimal preco;
        private final String nome;
        private final int estoque;
        private final String descricao;

        private Instantaneo(Produto p) {
            this.preco = p.getPrecoVenda();
            this.nome = p.getNome();
            this.estoque = p.getEstoqueAtual();
            this.descricao = descrever(p);
        }

        static String descrever(Produto p) {
            return "sku=" + p.getSku() + "; nome=" + p.getNome() + "; preco_venda=" + p.getPrecoVenda()
                    + "; estoque_atual=" + p.getEstoqueAtual() + "; estoque_minimo=" + p.getEstoqueMinimo();
        }
    }
}
