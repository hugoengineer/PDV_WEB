package br.com.empresa.pdv.venda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.caixa.Caixa;
import br.com.empresa.pdv.caixa.CaixaService;
import br.com.empresa.pdv.cliente.Cliente;
import br.com.empresa.pdv.cliente.ClienteRepository;
import br.com.empresa.pdv.cliente.ClienteService;
import br.com.empresa.pdv.comum.Dinheiro;
import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.comum.Paginacao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.comum.erro.ParametroInvalido;
import br.com.empresa.pdv.cupom.Cupom;
import br.com.empresa.pdv.cupom.CupomService;
import br.com.empresa.pdv.produto.Produto;
import br.com.empresa.pdv.produto.ProdutoRepository;
import br.com.empresa.pdv.seguranca.LimitadorDeTentativas;
import br.com.empresa.pdv.seguranca.UsuarioLogado;
import br.com.empresa.pdv.usuario.Perfil;
import br.com.empresa.pdv.usuario.Usuario;
import br.com.empresa.pdv.usuario.UsuarioRepository;
import br.com.empresa.pdv.venda.VendaDtos.AlertaEstoque;
import br.com.empresa.pdv.venda.VendaDtos.ComissaoResposta;
import br.com.empresa.pdv.venda.VendaDtos.ItemDetalhe;
import br.com.empresa.pdv.venda.VendaDtos.ItemRequisicao;
import br.com.empresa.pdv.venda.VendaDtos.VendaDetalhe;
import br.com.empresa.pdv.venda.VendaDtos.VendaRegistradaResposta;
import br.com.empresa.pdv.venda.VendaDtos.VendaRequisicao;
import br.com.empresa.pdv.venda.VendaDtos.VendaResumo;

@Service
public class VendaService {

    static final String DETALHE_DESCONTO_SEM_AUTORIZACAO = "Desconto direto exige autorização. Informe "
            + "senha_autorizacao_gerente válida ou realize o registro via conta de GERENTE.";
    private static final OffsetDateTime INICIO_DOS_TEMPOS = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIM_DOS_TEMPOS = OffsetDateTime.of(9999, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    private final VendaRepository vendas;
    private final ProdutoRepository produtos;
    private final ClienteRepository clientes;
    private final ClienteService clienteService;
    private final UsuarioRepository usuarios;
    private final CaixaService caixaService;
    private final CupomService cupomService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final Relogio relogio;
    private final LimitadorDeTentativas limitador;
    private final BaseComissao baseComissao;

    public VendaService(VendaRepository vendas, ProdutoRepository produtos, ClienteRepository clientes,
                        ClienteService clienteService, UsuarioRepository usuarios, CaixaService caixaService,
                        CupomService cupomService, PasswordEncoder passwordEncoder, AuditoriaService auditoria,
                        Relogio relogio, LimitadorDeTentativas limitador,
                        @Value("${pdv.comissao.base:LIQUIDO}") BaseComissao baseComissao) {
        this.limitador = limitador;
        this.baseComissao = baseComissao;
        this.vendas = vendas;
        this.produtos = produtos;
        this.clientes = clientes;
        this.clienteService = clienteService;
        this.usuarios = usuarios;
        this.caixaService = caixaService;
        this.cupomService = cupomService;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    /**
     * UC04 — Registrar Venda. Tudo acontece numa única transação: se qualquer validação falhar
     * (caixa, cliente, autorização, estoque, cupom, desconto), nada é gravado e o estoque não muda.
     */
    @Transactional
    public VendaRegistradaResposta registrar(VendaRequisicao req) {
        UsuarioLogado logado = UsuarioLogado.atual();
        Usuario operador = usuarios.findById(logado.id())
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário", logado.id()));

        // VAL-05: só vende com caixa próprio aberto
        Caixa caixa = caixaService.validarCaixaParaVenda(req.caixaId(), operador.getId());
        Cliente cliente = resolverCliente(req.clienteId());
        // UC07: o desconto direto exige perfil GERENTE ou a senha de um gerente ativo
        BigDecimal descontoGerente = Dinheiro.valorOuZero(req.descontoDiretoGerente());
        Long gerenteAutorizador = descontoGerente.signum() > 0
                ? autorizarDescontoDireto(logado, req.senhaAutorizacaoGerente())
                : null;

        // UC05 / VAL-01: bloqueia os produtos e confere o saldo de todos antes de baixar qualquer um
        Map<Long, Integer> quantidades = agruparQuantidades(req.itens());
        Map<Long, Produto> produtosBloqueados = bloquearEValidarEstoque(quantidades);

        BigDecimal subtotal = quantidades.entrySet().stream()
                .map(e -> produtosBloqueados.get(e.getKey()).getPrecoVenda().multiply(BigDecimal.valueOf(e.getValue())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Cupom cupom = req.cupomCodigo() == null || req.cupomCodigo().isBlank()
                ? null
                : cupomService.obterVigente(req.cupomCodigo());
        BigDecimal descontoCupom = cupom == null ? BigDecimal.ZERO : cupom.calcularDesconto(Dinheiro.arredondar(subtotal));

        ResumoFinanceiro resumo = CalculadoraVenda.calcular(subtotal, descontoCupom, descontoGerente,
                operador.getPercentualComissao(), baseComissao);

        Venda venda = new Venda(operador.getId(), cliente.getId(), caixa.getId(), req.modalidadePagamento(),
                relogio.agora());
        quantidades.forEach((produtoId, quantidade) -> {
            Produto produto = produtosBloqueados.get(produtoId);
            venda.adicionarItem(produtoId, quantidade, produto.getPrecoVenda());
            produto.baixarEstoque(quantidade);
        });
        venda.aplicarValores(resumo, cupom == null ? null : cupom.getId(), gerenteAutorizador);
        Venda salva = vendas.save(venda);

        auditoria.registrar(operador.getId(), TipoAcao.VENDA_REGISTRO, "VENDA", salva.getId(),
                "Venda " + salva.getId() + " registrada: " + quantidades.size() + " produto(s), total R$ "
                        + resumo.total() + " (" + req.modalidadePagamento() + ").");
        if (gerenteAutorizador != null) {
            auditoria.registrar(operador.getId(), TipoAcao.DESCONTO_GERENTE, "VENDA", salva.getId(),
                    "Desconto direto de R$ " + resumo.descontoGerente() + " autorizado pelo gerente ID "
                            + gerenteAutorizador + " na venda " + salva.getId() + ".");
        }

        List<AlertaEstoque> alertas = produtosBloqueados.values().stream()
                .filter(Produto::isEstoqueCritico)
                .map(p -> new AlertaEstoque(p.getId(), p.getNome(), p.getEstoqueAtual(), p.getEstoqueMinimo()))
                .toList();
        return new VendaRegistradaResposta(salva.getId(), resumo.subtotal(), resumo.descontoCupom(),
                resumo.descontoGerente(), resumo.total(), salva.getModalidadePagamento(), salva.getStatus(),
                resumo.comissao(), relogio.noFuso(salva.getDataVenda()), alertas);
    }

    /** UC11 — Cancelar Venda (Gerente): estorna o estoque e retira a venda dos totais financeiros. */
    @Transactional
    public void cancelar(Long vendaId, String motivo) {
        UsuarioLogado gerente = UsuarioLogado.atual();
        Venda venda = vendas.bloquear(vendaId).orElseThrow(() -> ApiException.naoEncontrado("Venda", vendaId));
        if (venda.isCancelada()) {
            throw ApiException.regraNegocio("venda-ja-cancelada", "Venda Já Cancelada",
                    "A venda " + vendaId + " já foi cancelada anteriormente.");
        }
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        venda.getItens().forEach(item -> quantidades.merge(item.getProdutoId(), item.getQuantidade(), Integer::sum));
        produtos.bloquearPorIds(quantidades.keySet())
                .forEach(produto -> produto.estornarEstoque(quantidades.get(produto.getId())));
        venda.cancelar(motivo.trim(), gerente.id(), relogio.agora());

        String itensEstornados = quantidades.entrySet().stream()
                .map(e -> "produto " + e.getKey() + " +" + e.getValue())
                .collect(Collectors.joining(", "));
        auditoria.registrar(gerente.id(), TipoAcao.CANCELAMENTO_VENDA, "VENDA", vendaId,
                "Venda " + vendaId + " cancelada. Motivo: " + venda.getMotivoCancelamento()
                        + ". Estorno: " + itensEstornados + ".",
                StatusVenda.CONCLUIDA.name(), StatusVenda.CANCELADA.name());
    }

    @Transactional(readOnly = true)
    public Pagina<VendaResumo> listar(Integer pagina, Integer limite, LocalDate inicio, LocalDate fim,
                                      Long usuarioId, StatusVenda status) {
        Relogio.validarPeriodo(inicio, fim);
        Long alvo = restringirAoProprioVendedor(usuarioId);
        Specification<Venda> filtro = (raiz, consulta, cb) -> cb.conjunction();
        if (alvo != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("usuarioId"), alvo));
        }
        if (inicio != null) {
            filtro = filtro.and((raiz, consulta, cb) ->
                    cb.greaterThanOrEqualTo(raiz.get("dataVenda"), relogio.inicioDoDia(inicio)));
        }
        if (fim != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.lessThan(raiz.get("dataVenda"), relogio.fimDoDiaExclusivo(fim)));
        }
        if (status != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("status"), status));
        }
        Page<Venda> page = vendas.findAll(filtro,
                Paginacao.de(pagina, limite, Sort.by(Sort.Direction.DESC, "dataVenda", "id")));

        Map<Long, String> nomesClientes = clientes.findAllById(
                        page.getContent().stream().map(Venda::getClienteId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNomeRazaoSocial));
        Map<Long, String> nomesVendedores = nomesUsuarios(page.getContent().stream().map(Venda::getUsuarioId)
                .collect(Collectors.toSet()));
        return Pagina.de(page, v -> new VendaResumo(v.getId(), relogio.noFuso(v.getDataVenda()),
                nomesClientes.get(v.getClienteId()), nomesVendedores.get(v.getUsuarioId()), v.getValorTotal(),
                v.getModalidadePagamento(), v.getStatus()));
    }

    @Transactional(readOnly = true)
    public VendaDetalhe detalhar(Long id) {
        Venda venda = vendas.findById(id).orElseThrow(() -> ApiException.naoEncontrado("Venda", id));
        UsuarioLogado logado = UsuarioLogado.atual();
        if (!logado.isGerente() && !venda.getUsuarioId().equals(logado.id())) {
            throw ApiException.acessoNegado("O vendedor só pode consultar as próprias vendas.");
        }
        Map<Long, String> nomesProdutos = produtos.findAllById(
                        venda.getItens().stream().map(ItemVenda::getProdutoId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Produto::getId, Produto::getNome));
        String cliente = clientes.findById(venda.getClienteId()).map(Cliente::getNomeRazaoSocial).orElse(null);
        String vendedor = usuarios.findById(venda.getUsuarioId()).map(Usuario::getNome).orElse(null);
        List<ItemDetalhe> itens = venda.getItens().stream()
                .map(i -> new ItemDetalhe(i.getProdutoId(), nomesProdutos.get(i.getProdutoId()), i.getQuantidade(),
                        i.getPrecoUnitario(), i.getSubtotal()))
                .toList();
        return new VendaDetalhe(venda.getId(), relogio.noFuso(venda.getDataVenda()), venda.getCaixaId(),
                venda.getClienteId(), cliente, venda.getUsuarioId(), vendedor, venda.getValorSubtotal(),
                venda.getDescontoCupom(), venda.getDescontoGerente(), venda.getValorTotal(),
                venda.getModalidadePagamento(), venda.getStatus(), venda.getPercentualComissao(),
                venda.getValorComissao(), venda.getCupomId(), venda.getGerenteAutorizadorId(),
                venda.getMotivoCancelamento(), relogio.noFuso(venda.getDataCancelamento()), itens);
    }

    /**
     * UC10 — Comissões. Vendedor: sempre a própria. Gerente: de um vendedor (objeto) ou de todos (lista).
     * Somente vendas CONCLUIDAS entram na soma; o valor de cada venda usa o percentual congelado nela.
     */
    @Transactional(readOnly = true)
    public Object comissoes(LocalDate inicio, LocalDate fim, Long vendedorId) {
        Relogio.validarPeriodo(inicio, fim);
        Long alvo = restringirAoProprioVendedor(vendedorId);
        OffsetDateTime de = inicio == null ? INICIO_DOS_TEMPOS : relogio.inicioDoDia(inicio);
        OffsetDateTime ate = fim == null ? FIM_DOS_TEMPOS : relogio.fimDoDiaExclusivo(fim);
        List<ConsolidadoVendedor> consolidado = vendas.consolidarPorVendedor(de, ate, alvo);

        if (alvo != null) {
            Usuario vendedor = usuarios.findById(alvo).orElseThrow(() -> ApiException.naoEncontrado("Vendedor", alvo));
            ConsolidadoVendedor dados = consolidado.isEmpty()
                    ? new ConsolidadoVendedor(alvo, 0, BigDecimal.ZERO, BigDecimal.ZERO)
                    : consolidado.getFirst();
            return comissao(vendedor, dados);
        }

        Map<Long, ConsolidadoVendedor> porVendedor = consolidado.stream()
                .collect(Collectors.toMap(ConsolidadoVendedor::usuarioId, Function.identity()));
        // Inclui vendedores ativos sem vendas no período, para o gerente ver o quadro completo
        Set<Long> ids = new TreeSet<>(porVendedor.keySet());
        usuarios.findByPerfilAndAtivoTrue(Perfil.VENDEDOR).forEach(u -> ids.add(u.getId()));
        Map<Long, Usuario> vendedores = usuarios.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        List<ComissaoResposta> lista = new ArrayList<>();
        for (Long id : ids) {
            lista.add(comissao(vendedores.get(id), porVendedor.getOrDefault(id,
                    new ConsolidadoVendedor(id, 0, BigDecimal.ZERO, BigDecimal.ZERO))));
        }
        return lista;
    }

    private ComissaoResposta comissao(Usuario vendedor, ConsolidadoVendedor dados) {
        return new ComissaoResposta(vendedor.getId(), vendedor.getNome(), dados.quantidadeVendas(),
                Dinheiro.arredondar(dados.valorTotalVendido()), vendedor.getPercentualComissao(),
                Dinheiro.arredondar(dados.totalComissao()));
    }

    /** Vendedor só consulta os próprios dados; pedir os de outro vendedor é tentativa de acesso indevido. */
    private static Long restringirAoProprioVendedor(Long usuarioIdSolicitado) {
        UsuarioLogado logado = UsuarioLogado.atual();
        if (logado.isGerente()) {
            return usuarioIdSolicitado;
        }
        if (usuarioIdSolicitado != null && !usuarioIdSolicitado.equals(logado.id())) {
            throw ApiException.acessoNegado("O vendedor só pode consultar os próprios dados.");
        }
        return logado.id();
    }

    private Cliente resolverCliente(Long clienteId) {
        if (clienteId == null) {
            return clienteService.consumidorPadrao();
        }
        return clientes.findById(clienteId).filter(Cliente::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Cliente", clienteId));
    }

    /**
     * Vendedor precisa da senha de um gerente ativo. Senhas erradas são contadas por vendedor: ao atingir
     * o limite, novas tentativas recebem 429 por um período (impede adivinhar a senha pela tela de venda).
     */
    private Long autorizarDescontoDireto(UsuarioLogado logado, String senhaGerente) {
        if (logado.isGerente()) {
            return logado.id();
        }
        String chave = LimitadorDeTentativas.chaveSenhaGerente(logado.id());
        limitador.verificar(chave);
        if (senhaGerente != null && !senhaGerente.isBlank()) {
            for (Usuario gerente : usuarios.findByPerfilAndAtivoTrue(Perfil.GERENTE)) {
                if (passwordEncoder.matches(senhaGerente, gerente.getSenhaHash())) {
                    limitador.limpar(chave);
                    return gerente.getId();
                }
            }
            boolean bloqueou = limitador.registrarFalha(chave, limitador.propriedades().maxFalhasSenhaGerente());
            auditoria.registrarIsolado(logado.id(), TipoAcao.AUTORIZACAO_FALHA, "USUARIO", logado.id(),
                    "Senha de gerente incorreta ao solicitar desconto direto"
                            + (bloqueou ? "; autorização bloqueada temporariamente por excesso de tentativas." : "."));
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "acesso-negado", "Acesso Negado", DETALHE_DESCONTO_SEM_AUTORIZACAO,
                List.of(new ParametroInvalido("senha_autorizacao_gerente",
                        "Credenciais de autorização de gerente inválidas ou ausentes.")));
    }

    /** Soma itens repetidos do mesmo produto (ex.: o operador bipou duas vezes). */
    static Map<Long, Integer> agruparQuantidades(List<ItemRequisicao> itens) {
        Map<Long, Integer> quantidades = new LinkedHashMap<>();
        for (ItemRequisicao item : itens) {
            quantidades.merge(item.produtoId(), item.quantidade(), Math::addExact);
        }
        return quantidades;
    }

    private Map<Long, Produto> bloquearEValidarEstoque(Map<Long, Integer> quantidades) {
        Map<Long, Produto> bloqueados = produtos.bloquearPorIds(quantidades.keySet()).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (Long id : quantidades.keySet()) {
            Produto produto = bloqueados.get(id);
            if (produto == null || !produto.isAtivo()) {
                throw ApiException.naoEncontrado("Produto", id);
            }
        }
        List<ParametroInvalido> semSaldo = new ArrayList<>();
        quantidades.forEach((id, quantidade) -> {
            Produto produto = bloqueados.get(id);
            if (!produto.possuiSaldo(quantidade)) {
                semSaldo.add(new ParametroInvalido("produto_id: " + id,
                        "Estoque atual é " + produto.getEstoqueAtual() + ", mas foi solicitado " + quantidade + "."));
            }
        });
        if (!semSaldo.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "estoque-insuficiente", "Estoque Insuficiente",
                    "Quantidade solicitada supera o estoque disponível.", semSaldo);
        }
        return bloqueados;
    }

    private Map<Long, String> nomesUsuarios(Set<Long> ids) {
        return usuarios.findAllById(ids).stream().collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }
}
