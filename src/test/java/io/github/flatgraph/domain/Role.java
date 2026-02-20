package io.github.flatgraph.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Role {

    private Long id;
    private String nome;
    private List<Permissao> permissoes = new ArrayList<>();

    // ---- getters ----

    public Long getId()                      { return id; }
    public String getNome()                  { return nome; }
    public List<Permissao> getPermissoes()   { return permissoes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role r)) return false;
        return Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Role{id=" + id + ", nome='" + nome + "', permissoes=" + permissoes + "}";
    }
}
