package io.github.flatgraph.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Usuario {

    private Long id;
    private String matricula;
    private String nome;
    private String email;
    private String ativo;
    private List<Role> roles = new ArrayList<>();

    // ---- getters ----

    public Long getId()          { return id; }
    public String getMatricula() { return matricula; }
    public String getNome()      { return nome; }
    public String getEmail()     { return email; }
    public String getAtivo()     { return ativo; }
    public List<Role> getRoles() { return roles; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Usuario u)) return false;
        return Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", nome='" + nome + "', roles=" + roles + "}";
    }
}
