package br.com.empresa.pdv.auth;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.auth.AuthDtos.LoginResposta;
import br.com.empresa.pdv.auth.AuthDtos.TokenResposta;
import br.com.empresa.pdv.auth.AuthDtos.UsuarioResumo;
import br.com.empresa.pdv.comum.Documentos;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.seguranca.LimitadorDeTentativas;
import br.com.empresa.pdv.seguranca.RevogacaoDeTokens;
import br.com.empresa.pdv.seguranca.ServicoDeTokens;
import br.com.empresa.pdv.seguranca.ServicoDeTokens.TokenEmitido;
import br.com.empresa.pdv.seguranca.UsuarioLogado;
import br.com.empresa.pdv.usuario.Usuario;
import br.com.empresa.pdv.usuario.UsuarioRepository;

@Service
public class AuthService {

    /** Mensagem única para CPF inexistente, senha errada ou usuário inativo: não revela qual falhou. */
    static final String CREDENCIAIS_INVALIDAS = "CPF ou senha inválidos.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final ServicoDeTokens tokens;
    private final RevogacaoDeTokens revogacao;
    private final AuditoriaService auditoria;
    private final Relogio relogio;
    private final LimitadorDeTentativas limitador;
    /** BCrypt de um valor aleatório descartado; usado só para igualar o custo de tempo. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, ServicoDeTokens tokens,
                       RevogacaoDeTokens revogacao, AuditoriaService auditoria, Relogio relogio,
                       LimitadorDeTentativas limitador) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.revogacao = revogacao;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.limitador = limitador;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Contra força bruta, conta falhas por CPF e por IP. O bloqueio por CPF vale também para CPFs
     * inexistentes, então o 429 não revela se o usuário existe.
     */
    @Transactional
    public LoginResposta login(String cpfInformado, String senha, String ip) {
        String cpf = Documentos.somenteDigitos(cpfInformado);
        String chaveCpf = LimitadorDeTentativas.chaveCpf(cpf);
        String chaveIp = LimitadorDeTentativas.chaveIp(ip);
        limitador.verificar(chaveCpf);
        limitador.verificar(chaveIp);

        Usuario usuario = usuarios.findByCpf(cpf).orElse(null);
        // Sempre executa o BCrypt, mesmo sem usuário: o tempo de resposta não revela se o CPF existe
        String hash = usuario == null ? hashFicticio : usuario.getSenhaHash();
        boolean senhaConfere = passwordEncoder.matches(senha, hash);
        if (usuario == null || !usuario.isAtivo() || !senhaConfere) {
            Long usuarioId = usuario == null ? null : usuario.getId();
            boolean bloqueouCpf = limitador.registrarFalha(chaveCpf, limitador.propriedades().maxFalhasCpf());
            boolean bloqueouIp = limitador.registrarFalha(chaveIp, limitador.propriedades().maxFalhasIp());
            auditoria.registrarIsolado(usuarioId, TipoAcao.LOGIN_FALHA, "USUARIO", usuarioId,
                    "Tentativa de login sem sucesso para o CPF " + mascarar(cpf) + " (IP " + ip + ").");
            if (bloqueouCpf || bloqueouIp) {
                auditoria.registrarIsolado(usuarioId, TipoAcao.ACESSO_BLOQUEADO, "USUARIO", usuarioId,
                        "Login bloqueado temporariamente por excesso de tentativas: "
                                + (bloqueouCpf ? "CPF " + mascarar(cpf) : "IP " + ip) + ".");
            }
            throw credenciaisInvalidas();
        }
        limitador.limpar(chaveCpf);
        TokenEmitido emitido = tokens.emitir(usuario);
        auditoria.registrar(usuario.getId(), TipoAcao.LOGIN_SUCESSO, "USUARIO", usuario.getId(), "Login realizado.");
        return new LoginResposta(true, emitido.token(), noFuso(emitido),
                new UsuarioResumo(usuario.getId(), usuario.getNome(), usuario.getPerfil(),
                        usuario.getPercentualComissao()));
    }

    @Transactional
    public TokenResposta renovar() {
        UsuarioLogado logado = UsuarioLogado.atual();
        Usuario usuario = usuarios.findById(logado.id()).orElseThrow(AuthService::credenciaisInvalidas);
        TokenEmitido emitido = tokens.emitir(usuario);
        // O token antigo deixa de valer: só um token por renovação permanece ativo
        revogacao.revogar(logado.jwt().getId(), logado.jwt().getExpiresAt());
        return new TokenResposta(true, emitido.token(), noFuso(emitido));
    }

    public void logout() {
        UsuarioLogado logado = UsuarioLogado.atual();
        revogacao.revogar(logado.jwt().getId(), logado.jwt().getExpiresAt());
    }

    @Transactional
    public void alterarSenha(String senhaAtual, String novaSenha) {
        UsuarioLogado logado = UsuarioLogado.atual();
        Usuario usuario = usuarios.findById(logado.id()).orElseThrow(AuthService::credenciaisInvalidas);
        if (!passwordEncoder.matches(senhaAtual, usuario.getSenhaHash())) {
            throw ApiException.validacao("senha_atual", "Senha atual incorreta.");
        }
        if (passwordEncoder.matches(novaSenha, usuario.getSenhaHash())) {
            throw ApiException.validacao("nova_senha", "A nova senha deve ser diferente da atual.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setAtualizadoEm(relogio.agora());
        auditoria.registrar(usuario.getId(), TipoAcao.SENHA_ALTERADA, "USUARIO", usuario.getId(),
                "Senha alterada pelo próprio usuário.");
    }

    private OffsetDateTime noFuso(TokenEmitido emitido) {
        return relogio.noFuso(emitido.expiraEm().atOffset(ZoneOffset.UTC));
    }

    private static ApiException credenciaisInvalidas() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "nao-autenticado", "Não Autenticado", CREDENCIAIS_INVALIDAS);
    }

    /** LGPD: o log não guarda o CPF completo de quem tentou entrar. */
    static String mascarar(String cpf) {
        if (cpf == null || cpf.length() < 4) {
            return "***";
        }
        return "***" + cpf.substring(cpf.length() - 4);
    }
}
