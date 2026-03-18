package com.leandrosnazareth.flatgraph.domain;

public class PermissaoDTO {

    private Long id;
    private String nome;

    public PermissaoDTO() {}

    public PermissaoDTO(Long id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    // ---- getters ----

    public Long getId()     { return id; }
    public String getNome() { return nome; }
}
