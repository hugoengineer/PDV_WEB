package br.com.empresa.pdv.usuario;

import java.math.BigDecimal;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import br.com.empresa.pdv.seguranca.UsuarioLogado;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioAtualizacao;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioCriacao;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioPatch;
import br.com.empresa.pdv.usuario.UsuarioDtos.UsuarioResposta;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final Relogio relogio;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, AuditoriaService auditoria,
                          Relogio relogio) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional
    public UsuarioResposta cadastrar(UsuarioCriacao dados) {
        String cpf = Documentos.somenteDigitos(dados.cpf());
        if (!Documentos.cpfValido(cpf)) {
            throw ApiException.validacao("cpf", "CPF inválido.");
        }
        if (repository.existsByCpf(cpf)) {
            throw ApiException.conflito("Já existe um usuário com este CPF.", "cpf", "CPF já cadastrado.");
        }
        Usuario usuario = repository.save(new Usuario(cpf, dados.nome().trim(), passwordEncoder.encode(dados.senha()),
                dados.perfil(), Dinheiro.arredondar(dados.percentualComissao()), relogio.agora()));
        auditoria.registrar(UsuarioLogado.atual().id(), TipoAcao.USUARIO_CREATE, "USUARIO", usuario.getId(),
                "Funcionário " + usuario.getNome() + " cadastrado com perfil " + usuario.getPerfil() + ".");
        return resposta(usuario);
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioResposta> listar(Integer pagina, Integer limite) {
        var paginacao = Paginacao.de(pagina, limite, Sort.by("nome"));
        return Pagina.de(repository.findByAtivoTrue(paginacao), this::resposta);
    }

    @Transactional(readOnly = true)
    public UsuarioResposta buscar(Long id) {
        return resposta(obter(id));
    }

    @Transactional
    public UsuarioResposta substituir(Long id, UsuarioAtualizacao dados) {
        return aplicar(id, dados.nome(), dados.perfil(), dados.percentualComissao(), null, null);
    }

    @Transactional
    public UsuarioResposta atualizarParcial(Long id, UsuarioPatch dados) {
        return aplicar(id, dados.nome(), dados.perfil(), dados.percentualComissao(), dados.cpf(), dados.novaSenha());
    }

    @Transactional
    public void inativar(Long id) {
        Usuario usuario = obter(id);
        UsuarioLogado logado = UsuarioLogado.atual();
        if (usuario.getId().equals(logado.id())) {
            throw ApiException.regraNegocio("regra-negocio", "Operação Não Permitida",
                    "O gerente não pode inativar o próprio usuário.");
        }
        usuario.setAtivo(false);
        usuario.setAtualizadoEm(relogio.agora());
        auditoria.registrar(logado.id(), TipoAcao.USUARIO_INATIVACAO, "USUARIO", id,
                "Funcionário " + usuario.getNome() + " inativado (soft delete).");
    }

    private UsuarioResposta aplicar(Long id, String nome, Perfil perfil, BigDecimal comissao, String cpfNovo,
                                    String novaSenha) {
        Usuario usuario = obter(id);
        UsuarioLogado logado = UsuarioLogado.atual();
        String antes = descrever(usuario);
        boolean encerrarSessoes = false;

        if (cpfNovo != null) {
            String cpf = Documentos.somenteDigitos(cpfNovo);
            if (!Documentos.cpfValido(cpf)) {
                throw ApiException.validacao("cpf", "CPF inválido.");
            }
            if (!cpf.equals(usuario.getCpf())) {
                if (repository.existsByCpf(cpf)) {
                    throw ApiException.conflito("Já existe um usuário com este CPF.", "cpf", "CPF já cadastrado.");
                }
                usuario.setCpf(cpf);
                encerrarSessoes = true;
            }
        }

        if (perfil != null && perfil != usuario.getPerfil() && usuario.isGerente()
                && usuario.getId().equals(logado.id())) {
            // Evita que o sistema fique sem nenhum gerente por engano
            throw ApiException.regraNegocio("regra-negocio", "Operação Não Permitida",
                    "O gerente não pode remover o próprio perfil de GERENTE.");
        }
        if (nome != null) {
            usuario.setNome(nome.trim());
        }
        if (perfil != null) {
            usuario.setPerfil(perfil);
        }
        if (comissao != null) {
            usuario.setPercentualComissao(Dinheiro.arredondar(comissao));
        }
        if (novaSenha != null) {
            usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
            encerrarSessoes = true;
        }
        usuario.setAtualizadoEm(relogio.agora());
        if (encerrarSessoes) {
            usuario.encerrarSessoes();
        }
        auditoria.registrar(logado.id(), TipoAcao.USUARIO_UPDATE, "USUARIO", id,
                "Dados do funcionário " + usuario.getNome() + " alterados"
                        + (novaSenha != null ? "; senha redefinida pelo gerente" : "")
                        + (encerrarSessoes ? "; sessões abertas encerradas" : "") + ".",
                antes, descrever(usuario));
        return resposta(usuario);
    }

    private Usuario obter(Long id) {
        return repository.findById(id).filter(Usuario::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Usuário", id));
    }

    private static String descrever(Usuario u) {
        // A senha nunca vai para a auditoria; o CPF aparece mascarado (só os 4 últimos dígitos)
        return "cpf=***" + u.getCpf().substring(7) + "; nome=" + u.getNome() + "; perfil=" + u.getPerfil()
                + "; percentual_comissao=" + u.getPercentualComissao();
    }

    private UsuarioResposta resposta(Usuario u) {
        return new UsuarioResposta(u.getId(), u.getCpf(), u.getNome(), u.getPerfil(), u.getPercentualComissao(),
                u.isAtivo(), relogio.noFuso(u.getAtualizadoEm()));
    }
}
