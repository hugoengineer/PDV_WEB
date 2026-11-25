package br.com.empresa.pdv.usuario;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCpf(String cpf);

    boolean existsByCpf(String cpf);

    boolean existsByIdAndAtivoTrueAndPerfil(Long id, Perfil perfil);

    /** Token vigente: usuário ativo, com o mesmo perfil e a mesma versão de sessão da emissão. */
    @Query("""
            select count(u) > 0 from Usuario u
            where u.id = :id and u.ativo = true and u.perfil = :perfil and u.versaoSessao = :versao
            """)
    boolean tokenVigente(@Param("id") Long id, @Param("perfil") Perfil perfil, @Param("versao") int versao);

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);

    List<Usuario> findByPerfilAndAtivoTrue(Perfil perfil);

    Page<Usuario> findByAtivoTrue(Pageable pageable);
}
