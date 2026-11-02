package br.com.empresa.pdv.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    boolean existsByIdAndAtivoTrueAndPerfil(Long id, Perfil perfil);

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);

    List<Usuario> findByPerfilAndAtivoTrue(Perfil perfil);

    Page<Usuario> findByAtivoTrue(Pageable pageable);
}
