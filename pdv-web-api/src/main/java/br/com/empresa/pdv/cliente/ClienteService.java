package br.com.empresa.pdv.cliente;

import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.cliente.ClienteDtos.ClientePatch;
import br.com.empresa.pdv.cliente.ClienteDtos.ClienteRequisicao;
import br.com.empresa.pdv.cliente.ClienteDtos.ClienteResposta;
import br.com.empresa.pdv.comum.Documentos;
import br.com.empresa.pdv.comum.Pagina;
import br.com.empresa.pdv.comum.Paginacao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.seguranca.UsuarioLogado;

@Service
public class ClienteService {

    private final ClienteRepository repository;
    private final AuditoriaService auditoria;
    private final Relogio relogio;

    public ClienteService(ClienteRepository repository, AuditoriaService auditoria, Relogio relogio) {
        this.repository = repository;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional
    public ClienteResposta cadastrar(ClienteRequisicao dados) {
        String documento = validarDocumento(dados.tipo(), dados.cpfCnpj());
        if (repository.existsByCpfCnpj(documento)) {
            throw ApiException.conflito("Já existe um cliente com este CPF/CNPJ.", "cpf_cnpj", "CPF/CNPJ já cadastrado.");
        }
        Cliente cliente = new Cliente(relogio.agora());
        cliente.setTipo(dados.tipo());
        cliente.setCpfCnpj(documento);
        preencherContato(cliente, dados.nomeRazaoSocial(), dados.email(), dados.telefone(), dados.cep(),
                dados.endereco(), dados.bairro(), dados.cidade(), dados.uf(), true);
        cliente = repository.save(cliente);
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.CLIENTE_CREATE, "CLIENTE", cliente.getId(),
                "Cliente " + cliente.getTipo() + " " + cliente.getNomeRazaoSocial() + " cadastrado.");
        return resposta(cliente);
    }

    @Transactional(readOnly = true)
    public Pagina<ClienteResposta> listar(Integer pagina, Integer limite, String cpfCnpj, String nome) {
        Specification<Cliente> filtro = (raiz, consulta, cb) -> cb.isTrue(raiz.get("ativo"));
        if (cpfCnpj != null && !cpfCnpj.isBlank()) {
            String documento = Documentos.somenteDigitos(cpfCnpj);
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("cpfCnpj"), documento));
        }
        if (nome != null && !nome.isBlank()) {
            String padrao = "%" + nome.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
            filtro = filtro.and((raiz, consulta, cb) -> cb.like(cb.lower(raiz.get("nomeRazaoSocial")), padrao, '\\'));
        }
        var paginacao = Paginacao.de(pagina, limite, Sort.by("nomeRazaoSocial", "id"));
        return Pagina.de(repository.findAll(filtro, paginacao), this::resposta);
    }

    @Transactional(readOnly = true)
    public ClienteResposta buscar(Long id) {
        return resposta(obterAtivo(id));
    }

    @Transactional
    public ClienteResposta substituir(Long id, ClienteRequisicao dados) {
        Cliente cliente = obterEditavel(id);
        String antes = descrever(cliente);
        String documento = validarDocumento(dados.tipo(), dados.cpfCnpj());
        validarUnicidade(id, documento);
        cliente.setTipo(dados.tipo());
        cliente.setCpfCnpj(documento);
        preencherContato(cliente, dados.nomeRazaoSocial(), dados.email(), dados.telefone(), dados.cep(),
                dados.endereco(), dados.bairro(), dados.cidade(), dados.uf(), true);
        return concluirAlteracao(cliente, antes);
    }

    @Transactional
    public ClienteResposta atualizarParcial(Long id, ClientePatch dados) {
        Cliente cliente = obterEditavel(id);
        String antes = descrever(cliente);
        if (dados.tipo() != null || dados.cpfCnpj() != null) {
            TipoPessoa tipo = dados.tipo() != null ? dados.tipo() : cliente.getTipo();
            String documento = validarDocumento(tipo, dados.cpfCnpj() != null ? dados.cpfCnpj() : cliente.getCpfCnpj());
            validarUnicidade(id, documento);
            cliente.setTipo(tipo);
            cliente.setCpfCnpj(documento);
        }
        preencherContato(cliente, dados.nomeRazaoSocial(), dados.email(), dados.telefone(), dados.cep(),
                dados.endereco(), dados.bairro(), dados.cidade(), dados.uf(), false);
        return concluirAlteracao(cliente, antes);
    }

    @Transactional
    public void inativar(Long id) {
        Cliente cliente = obterEditavel(id);
        cliente.setAtivo(false);
        cliente.setAtualizadoEm(relogio.agora());
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.CLIENTE_INATIVACAO, "CLIENTE", id,
                "Cliente " + cliente.getNomeRazaoSocial() + " inativado (soft delete).");
    }

    /** Usado pelo módulo de vendas quando a venda não informa cliente. */
    @Transactional(readOnly = true)
    public Cliente consumidorPadrao() {
        return repository.findByCpfCnpj(Cliente.DOCUMENTO_CONSUMIDOR_PADRAO)
                .orElseThrow(() -> new IllegalStateException("Consumidor Padrão ausente: migração V2 não aplicada."));
    }

    /** Valida o documento conforme o tipo e devolve somente os dígitos. */
    static String validarDocumento(TipoPessoa tipo, String valor) {
        String documento = Documentos.somenteDigitos(valor);
        if (tipo == TipoPessoa.PF && !Documentos.cpfValido(documento)) {
            throw ApiException.validacao("cpf_cnpj", "CPF inválido para pessoa física.");
        }
        if (tipo == TipoPessoa.PJ && !Documentos.cnpjValido(documento)) {
            throw ApiException.validacao("cpf_cnpj", "CNPJ inválido para pessoa jurídica.");
        }
        return documento;
    }

    private void validarUnicidade(Long id, String documento) {
        if (repository.existsByCpfCnpjAndIdNot(documento, id)) {
            throw ApiException.conflito("Já existe um cliente com este CPF/CNPJ.", "cpf_cnpj", "CPF/CNPJ já cadastrado.");
        }
    }

    /**
     * {@code substituir = true} (PUT): campos opcionais ausentes são apagados.
     * {@code substituir = false} (PATCH): campos ausentes são mantidos.
     */
    private static void preencherContato(Cliente c, String nome, String email, String telefone, String cep,
                                         String endereco, String bairro, String cidade, String uf, boolean substituir) {
        if (nome != null) {
            c.setNomeRazaoSocial(nome.trim());
        }
        if (substituir || email != null) {
            c.setEmail(textoOuNulo(email));
        }
        if (substituir || telefone != null) {
            c.setTelefone(textoOuNulo(telefone));
        }
        if (substituir || cep != null) {
            c.setCep(cep == null ? null : Documentos.somenteDigitos(cep));
        }
        if (substituir || endereco != null) {
            c.setEndereco(textoOuNulo(endereco));
        }
        if (substituir || bairro != null) {
            c.setBairro(textoOuNulo(bairro));
        }
        if (substituir || cidade != null) {
            c.setCidade(textoOuNulo(cidade));
        }
        if (substituir || uf != null) {
            c.setUf(uf == null ? null : uf.toUpperCase(Locale.ROOT));
        }
    }

    private ClienteResposta concluirAlteracao(Cliente cliente, String antes) {
        cliente.setAtualizadoEm(relogio.agora());
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.CLIENTE_UPDATE, "CLIENTE", cliente.getId(),
                "Dados do cliente ID " + cliente.getId() + " alterados.", antes, descrever(cliente));
        return resposta(cliente);
    }

    private Cliente obterAtivo(Long id) {
        return repository.findById(id).filter(Cliente::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Cliente", id));
    }

    private Cliente obterEditavel(Long id) {
        Cliente cliente = obterAtivo(id);
        if (cliente.isConsumidorPadrao()) {
            throw ApiException.regraNegocio("regra-negocio", "Operação Não Permitida",
                    "O cliente 'CONSUMIDOR PADRÃO' é reservado pelo sistema e não pode ser alterado ou inativado.");
        }
        return cliente;
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String descrever(Cliente c) {
        return "tipo=" + c.getTipo() + "; cpf_cnpj=" + c.getCpfCnpj() + "; nome_razao_social=" + c.getNomeRazaoSocial()
                + "; email=" + c.getEmail() + "; telefone=" + c.getTelefone();
    }

    private ClienteResposta resposta(Cliente c) {
        return new ClienteResposta(c.getId(), c.getTipo(), c.getCpfCnpj(), c.getNomeRazaoSocial(), c.getEmail(),
                c.getTelefone(), c.getCep(), c.getEndereco(), c.getBairro(), c.getCidade(), c.getUf(), c.isAtivo(),
                relogio.noFuso(c.getAtualizadoEm()));
    }
}
