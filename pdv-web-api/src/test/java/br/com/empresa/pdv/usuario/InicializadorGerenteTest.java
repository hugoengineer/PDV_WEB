package br.com.empresa.pdv.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.empresa.pdv.comum.Relogio;

/** Criação do primeiro gerente a partir de PDV_ADMIN_CPF / PDV_ADMIN_SENHA (instalação). */
@ExtendWith(MockitoExtension.class)
@DisplayName("Inicialização do gerente (unitário com Mockito)")
class InicializadorGerenteTest {

    private static final String CPF_VALIDO = "52998224725";

    @Mock private UsuarioRepository repository;
    @Mock private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("CT-USR-U01 Sem gerente ativo e com variáveis válidas, cria o gerente com senha em hash")
    void criaGerente() {
        when(repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(false);
        when(repository.findByCpf(CPF_VALIDO)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SenhaForte1")).thenReturn("hash");

        inicializador("529.982.247-25", "SenhaForte1").run(null);

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(salvo.capture());
        assertThat(salvo.getValue().getCpf()).isEqualTo(CPF_VALIDO);
        assertThat(salvo.getValue().getPerfil()).isEqualTo(Perfil.GERENTE);
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("CT-USR-U02 Já existindo gerente ativo, nada é alterado (reinícios não duplicam)")
    void naoDuplica() {
        when(repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(true);
        inicializador(CPF_VALIDO, "SenhaForte1").run(null);
        verify(repository, never()).save(any());
        verify(repository, never()).findByCpf(any());
    }

    @Test
    @DisplayName("CT-USR-U03 Sem CPF ou com senha de menos de 8 caracteres, não cria e só registra aviso")
    void configuracaoIncompleta() {
        when(repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(false);
        inicializador("", "SenhaForte1").run(null);
        inicializador(CPF_VALIDO, "1234567").run(null);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("CT-USR-U04 CPF inválido na configuração impede a subida da aplicação (falha explícita)")
    void cpfInvalido() {
        when(repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(false);
        assertThatThrownBy(() -> inicializador("12345678900", "SenhaForte1").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PDV_ADMIN_CPF");
    }

    @Test
    @DisplayName("CT-USR-U05 Usuário já cadastrado com o CPF configurado é reativado como gerente")
    void reativaExistente() {
        Usuario existente = new Usuario(CPF_VALIDO, "Ex-gerente", "hash", Perfil.VENDEDOR, BigDecimal.ONE,
                OffsetDateTime.now());
        existente.setAtivo(false);
        when(repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(false);
        when(repository.findByCpf(CPF_VALIDO)).thenReturn(Optional.of(existente));

        inicializador(CPF_VALIDO, "SenhaForte1").run(null);

        assertThat(existente.isAtivo()).isTrue();
        assertThat(existente.getPerfil()).isEqualTo(Perfil.GERENTE);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("CT-USR-U06 Valor-limite: senha com exatamente 8 caracteres é aceita e o gerente é criado")
    void senhaNoLimite() {
        when(repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)).thenReturn(false);
        when(repository.findByCpf(CPF_VALIDO)).thenReturn(Optional.empty());

        inicializador(CPF_VALIDO, "12345678").run(null);

        verify(passwordEncoder).encode("12345678");
        verify(repository).save(any());
    }

    private InicializadorGerente inicializador(String cpf, String senha) {
        return new InicializadorGerente(repository, passwordEncoder, new Relogio(Clock.systemUTC(), "America/Sao_Paulo"),
                cpf, "Gerente", senha);
    }
}
