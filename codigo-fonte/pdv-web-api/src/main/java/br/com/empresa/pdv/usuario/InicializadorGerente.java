package br.com.empresa.pdv.usuario;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.empresa.pdv.comum.Documentos;
import br.com.empresa.pdv.comum.Relogio;

/**
 * Cria o primeiro gerente a partir das variáveis PDV_ADMIN_CPF / PDV_ADMIN_SENHA.
 * Sem um gerente não há como cadastrar os demais usuários, e não há credenciais fixas no código.
 */
@Component
public class InicializadorGerente implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorGerente.class);

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Relogio relogio;
    private final String cpf;
    private final String nome;
    private final String senha;

    public InicializadorGerente(UsuarioRepository repository, PasswordEncoder passwordEncoder, Relogio relogio,
                                @Value("${pdv.admin.cpf:}") String cpf,
                                @Value("${pdv.admin.nome:Gerente Administrador}") String nome,
                                @Value("${pdv.admin.senha:}") String senha) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.relogio = relogio;
        this.cpf = Documentos.somenteDigitos(cpf);
        this.nome = nome;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsByPerfilAndAtivoTrue(Perfil.GERENTE)) {
            return;
        }
        if (cpf == null || cpf.isBlank() || senha == null || senha.length() < 8) {
            log.warn("Nenhum gerente ativo cadastrado. Defina PDV_ADMIN_CPF e PDV_ADMIN_SENHA (mín. 8 caracteres) "
                    + "para criar o gerente inicial.");
            return;
        }
        if (!Documentos.cpfValido(cpf)) {
            throw new IllegalStateException("PDV_ADMIN_CPF não é um CPF válido.");
        }
        repository.findByCpf(cpf).ifPresentOrElse(
                existente -> {
                    existente.setPerfil(Perfil.GERENTE);
                    existente.setAtivo(true);
                    log.info("Usuário {} reativado como gerente inicial.", existente.getId());
                },
                () -> {
                    repository.save(new Usuario(cpf, nome, passwordEncoder.encode(senha), Perfil.GERENTE,
                            BigDecimal.ZERO.setScale(2), relogio.agora()));
                    log.info("Gerente inicial criado.");
                });
    }
}
