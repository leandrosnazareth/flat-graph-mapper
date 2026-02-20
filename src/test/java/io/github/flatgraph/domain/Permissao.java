package io.github.flatgraph.domain;

import java.util.Objects;

public class Permissao {

    private Long id;
    private String nome;

    // ---- getters ----

    public Long getId()     { return id; }
    public String getNome() { return nome; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permissao p)) return false;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Permissao{id=" + id + ", nome='" + nome + "'}";
    }
}
