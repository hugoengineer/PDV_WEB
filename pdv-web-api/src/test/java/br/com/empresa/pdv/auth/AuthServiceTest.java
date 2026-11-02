package br.com.empresa.pdv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.empresa.pdv.auditoria.AuditoriaService;
import br.com.empresa.pdv.auditoria.TipoAcao;
import br.com.empresa.pdv.comum.Relogio;
import br.com.empresa.pdv.comum.erro.ApiException;
import br.com.empresa.pdv.seguranca.LimitadorDeTentativas;
import br.com.empresa.pdv.seguranca.PropriedadesTentativas;
import br.com.empresa.pdv.seguranca.RevogacaoDeTokens;
import br.com.empresa.pdv.seguranca.ServicoDeTokens;
import br.com.empresa.pdv.seguranca.TentativasExcedidasException;
import br.com.empresa.pdv.usuario.Perfil;
import br.com.empresa.pdv.usuario.Usuario;
import br.com.empresa.pdv.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Serviço de autenticação (unitário com Mockito)")
class AuthServiceTest {

    @Mock private UsuarioRepository usuarios;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ServicoDeTokens tokens;
    @Mock private RevogacaoDeTokens revogacao;
    @Mock private AuditoriaService auditoria;
    @Mock private LimitadorDeTentativas limitador;

    private AuthService service;

    @BeforeEach
    void preparar() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-ficticio");
        service = new AuthService(usuarios, passwordEncoder, tokens, revogacao, auditoria,
                new Relogio(Clock.systemUTC(), "America/Sao_Paulo"), limitador);
        lenient().when(limitador.propriedades())
                .thenReturn(new PropriedadesTentativas(null, null, null, null, null));
    }

    @Test
    @DisplayName("CT-AUTH-U01 Senha incorreta: 401, nenhum token emitido e tentativa auditada")
    void senhaIncorreta() {
        Usuario usuario = usuario(true);
        when(usuarios.findByCpf("52998224725")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> service.login("529.982.247-25", "errada", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(401);
                    assertThat(e.getMessage()).isEqualTo(AuthService.CREDENCIAIS_INVALIDAS);
                });
        verify(tokens, never()).emitir(any());
        verify(auditoria).registrarIsolado(any(), eq(TipoAcao.LOGIN_FALHA), eq("USUARIO"), any(), anyString());
    }

    @Test
    @DisplayName("CT-AUTH-U02 CPF inexistente: mesma mensagem e o BCrypt é executado (sem vazamento por tempo)")
    void cpfInexistente() {
        when(usuarios.findByCpf("00011122233")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("00011122233", "qualquer", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).isEqualTo(AuthService.CREDENCIAIS_INVALIDAS));
        verify(passwordEncoder, times(1)).matches("qualquer", "hash-ficticio");
        verify(auditoria).registrarIsolado(isNull(), eq(TipoAcao.LOGIN_FALHA), eq("USUARIO"), isNull(), anyString());
    }

    @Test
    @DisplayName("CT-AUTH-U03 Usuário inativo com senha correta não consegue autenticar")
    void usuarioInativo() {
        Usuario usuario = usuario(false);
        when(usuarios.findByCpf("52998224725")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correta", "hash-real")).thenReturn(true);

        assertThatThrownBy(() -> service.login("52998224725", "correta", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(401));
        verify(tokens, never()).emitir(any());
    }

    @Test
    @DisplayName("CT-AUTH-U04 O LOG de tentativa de login guarda apenas os 4 últimos dígitos do CPF (LGPD)")
    void mascaraCpf() {
        assertThat(AuthService.mascarar("52998224725")).isEqualTo("***4725");
        assertThat(AuthService.mascarar("1234")).as("limite: exatamente 4 dígitos").isEqualTo("***1234");
        assertThat(AuthService.mascarar("123")).isEqualTo("***");
        assertThat(AuthService.mascarar(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("CT-AUTH-U05 CPF ou IP já bloqueado: 429 antes de consultar o usuário e sem executar o BCrypt")
    void bloqueioAntesDaSenha() {
        lenient().doThrow(new TentativasExcedidasException(600)).when(limitador).verificar(LimitadorDeTentativas.chaveCpf("52998224725"));
        assertThatThrownBy(() -> service.login("52998224725", "qualquer", "10.0.0.1"))
                .isInstanceOfSatisfying(TentativasExcedidasException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(429));

        lenient().doThrow(new TentativasExcedidasException(600)).when(limitador).verificar(LimitadorDeTentativas.chaveIp("10.0.0.9"));
        assertThatThrownBy(() -> service.login("11144477735", "qualquer", "10.0.0.9"))
                .isInstanceOf(TentativasExcedidasException.class);

        verify(usuarios, never()).findByCpf(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(limitador, never()).registrarFalha(anyString(), anyInt());
    }

    @ParameterizedTest(name = "{displayName} [{index}] bloqueou CPF={0}, IP={1} → ACESSO_BLOQUEADO auditado={2} {3}")
    @CsvSource({"false, false, false, ''", "true, false, true, CPF ***4725", "false, true, true, IP 10.0.0.1", "true, true, true, CPF ***4725"})
    @DisplayName("CT-AUTH-U06 ACESSO_BLOQUEADO é auditado somente quando a falha atinge o limite do CPF ou do IP")
    void auditaBloqueio(boolean bloqueouCpf, boolean bloqueouIp, boolean auditado, String alvo) {
        when(usuarios.findByCpf("52998224725")).thenReturn(Optional.of(usuario(true)));
        when(passwordEncoder.matches("errada", "hash-real")).thenReturn(false);
        when(limitador.registrarFalha(eq(LimitadorDeTentativas.chaveCpf("52998224725")), anyInt())).thenReturn(bloqueouCpf);
        when(limitador.registrarFalha(eq(LimitadorDeTentativas.chaveIp("10.0.0.1")), anyInt())).thenReturn(bloqueouIp);

        assertThatThrownBy(() -> service.login("52998224725", "errada", "10.0.0.1")).isInstanceOf(ApiException.class);

        verify(auditoria, times(auditado ? 1 : 0))
                .registrarIsolado(any(), eq(TipoAcao.ACESSO_BLOQUEADO), eq("USUARIO"), any(), auditado ? contains(alvo) : anyString());
        verify(limitador, never()).limpar(anyString());
    }

    private static Usuario usuario(boolean ativo) {
        Usuario u = new Usuario("52998224725", "Jean", "hash-real", Perfil.VENDEDOR, BigDecimal.ONE, OffsetDateTime.now());
        u.setAtivo(ativo);
        return u;
    }
}
