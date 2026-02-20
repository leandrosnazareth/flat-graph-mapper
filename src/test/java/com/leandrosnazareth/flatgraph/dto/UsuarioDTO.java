package com.leandrosnazareth.flatgraph.dto;

import com.leandrosnazareth.flatgraph.annotation.ChildField;
import com.leandrosnazareth.flatgraph.annotation.ParentField;
import com.leandrosnazareth.flatgraph.domain.Permissao;
import com.leandrosnazareth.flatgraph.domain.Role;
import com.leandrosnazareth.flatgraph.domain.Usuario;

/**
 * Flat DTO representing a single row from a SQL JOIN of USUARIO → ROLE → PERMISSAO.
 *
 * <p>Each row may duplicate the Usuario and Role data — the engine de-duplicates
 * automatically using the IDs annotated on the "id" fields.
 */
public class UsuarioDTO {

    // ---- Root: Usuario ----------------------------------------------------------

    @ParentField(target = Usuario.class, field = "id")
    private Long idUsuario;

    @ParentField(target = Usuario.class, field = "matricula")
    private String usuarioMatricula;

    @ParentField(target = Usuario.class, field = "nome")
    private String usuarioNome;

    @ParentField(target = Usuario.class, field = "email")
    private String usuarioEmail;

    @ParentField(target = Usuario.class, field = "ativo")
    private String usuarioAtivo;

    // ---- Child level 1: Role (parent = Usuario) ---------------------------------

    @ChildField(target = Role.class, field = "id", parent = Usuario.class)
    private Long roleId;

    @ChildField(target = Role.class, field = "nome", parent = Usuario.class)
    private String roleNome;

    // ---- Child level 2: Permissao (parent = Role) -------------------------------

    @ChildField(target = Permissao.class, field = "id", parent = Role.class)
    private Long permissaoId;

    @ChildField(target = Permissao.class, field = "nome", parent = Role.class)
    private String permissaoNome;

    // ---- Builder-style factory method for tests ---------------------------------

    public static UsuarioDTO of(Long idUsuario, String matricula, String nome,
                                 String email,   String ativo,
                                 Long roleId,    String roleNome,
                                 Long permissaoId, String permissaoNome) {
        UsuarioDTO dto = new UsuarioDTO();
        dto.idUsuario        = idUsuario;
        dto.usuarioMatricula = matricula;
        dto.usuarioNome      = nome;
        dto.usuarioEmail     = email;
        dto.usuarioAtivo     = ativo;
        dto.roleId           = roleId;
        dto.roleNome         = roleNome;
        dto.permissaoId      = permissaoId;
        dto.permissaoNome    = permissaoNome;
        return dto;
    }
}
