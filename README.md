# FlatGraphMapper

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Build](https://img.shields.io/badge/build-Maven-blue?logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

FlatGraphMapper é uma pequena biblioteca Java que transforma o resultado **plano** de uma consulta SQL com `JOIN` em um **grafo de objetos hierárquico**.

A ideia é simples: você executa sua query normalmente (via JDBC, por exemplo), recebe uma lista de DTOs planos e o mapper reconstrói a estrutura de objetos.

Sem ORM.  
Sem dependências externas.

---

## Problema

Quando fazemos um `JOIN` entre várias tabelas, o resultado costuma vir assim:

```
idUsuario │ nome   │ roleId │ roleNome │ permissaoId │ permissaoNome
──────────┼────────┼────────┼──────────┼─────────────┼───────────────
    1     │ Alice  │   10   │ ADMIN    │     100     │ READ
    1     │ Alice  │   10   │ ADMIN    │     101     │ WRITE
    1     │ Alice  │   20   │ USER     │     102     │ READ
    2     │ Bob    │   20   │ USER     │     102     │ READ
```

Converter esse resultado em objetos hierárquicos manualmente é **repetitivo, verboso e propenso a bugs**.

---

## A Solução

1. Anota os campos do DTO com `@ParentField` e `@ChildField`
2. Chama `FlatGraphMapper.map(rows, MeuDTO.class)`
3. Recebe a lista de objetos raiz com o grafo montado e **sem duplicações**

```
Usuario(Alice)
  └─ Role(ADMIN)
  │    ├─ Permissao(READ)
  │    └─ Permissao(WRITE)
  └─ Role(USER)
       └─ Permissao(READ)
Usuario(Bob)
  └─ Role(USER)
       └─ Permissao(READ)
```

## Instalação

### Maven

```xml
<dependency>
    <groupId>io.github.flatgraph</groupId>
    <artifactId>flat-graph-mapper</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.flatgraph:flat-graph-mapper:1.0.0'
```

```

**Requisitos:** Java 17+ · Maven 3.8+

---

## Guia de Uso

### 1. Crie seu modelo de domínio

Os objetos de domínio precisam de:
- Um **construtor sem argumentos** (pode ser `private`)
- Um campo `List<Filho>` para cada coleção filha

```java
public class Usuario {
    private Long id;
    private String matricula;
    private String nome;
    private String email;
    private String ativo;
    private List<Role> roles = new ArrayList<>();
    // getters...
}

public class Role {
    private Long id;
    private String nome;
    private List<Permissao> permissoes = new ArrayList<>();
    // getters...
}

public class Permissao {
    private Long id;
    private String nome;
    // getters...
}
```

> ⚠️ O campo de ID nos objetos de domínio deve se chamar `id`.
> Ele é usado como chave de deduplicação pelo engine.

---

### 2. Anote seu DTO

Crie um DTO plano que represente uma linha do resultado SQL e anote cada campo:

```java
public class UsuarioDTO {

    // ── Raiz: Usuario ───────────────────────────────────────────
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

    // ── Filho nível 1: Role (pai = Usuario) ─────────────────────
    @ChildField(target = Role.class, field = "id", parent = Usuario.class)
    private Long roleId;

    @ChildField(target = Role.class, field = "nome", parent = Usuario.class)
    private String roleNome;

    // ── Filho nível 2: Permissao (pai = Role) ───────────────────
    @ChildField(target = Permissao.class, field = "id", parent = Role.class)
    private Long permissaoId;

    @ChildField(target = Permissao.class, field = "nome", parent = Role.class)
    private String permissaoNome;
}
```

**Regras importantes:**
- Todos os `@ParentField` de um DTO devem apontar para a **mesma** classe raiz
- O campo anotado com `field = "id"` é obrigatório em cada nível — ele identifica a instância
- Declare os campos no DTO **de cima para baixo** na hierarquia (pai antes do filho)

---

## Hierarquias Profundas

Não há limite de profundidade. Encadeie `@ChildField` usando o `parent` de cada nível:

```
Empresa  →  Departamento  →  Equipe  →  Funcionario
```

```java
public class EmpresaDTO {

    @ParentField(target = Empresa.class, field = "id")
    private Long empresaId;

    @ParentField(target = Empresa.class, field = "nome")
    private String empresaNome;

    // Nível 1
    @ChildField(target = Departamento.class, field = "id",   parent = Empresa.class)
    private Long departamentoId;

    @ChildField(target = Departamento.class, field = "nome", parent = Empresa.class)
    private String departamentoNome;

    // Nível 2
    @ChildField(target = Equipe.class, field = "id",   parent = Departamento.class)
    private Long equipeId;

    @ChildField(target = Equipe.class, field = "nome", parent = Departamento.class)
    private String equipeNome;

    // Nível 3
    @ChildField(target = Funcionario.class, field = "id",   parent = Equipe.class)
    private Long funcionarioId;

    @ChildField(target = Funcionario.class, field = "nome", parent = Equipe.class)
    private String funcionarioNome;
}
```

---

## Integração com JDBC

Exemplo completo com `JdbcTemplate` (a biblioteca não depende do Spring):

```java
private static final String SQL = """
        SELECT
            u.id          AS id_usuario,
            u.matricula   AS usuario_matricula,
            u.nome        AS usuario_nome,
            u.email       AS usuario_email,
            u.ativo       AS usuario_ativo,
            r.id          AS role_id,
            r.nome        AS role_nome,
            p.id          AS permissao_id,
            p.nome        AS permissao_nome
        FROM usuario u
        JOIN usuario_role ur   ON ur.usuario_id = u.id
        JOIN role r             ON r.id = ur.role_id
        JOIN role_permissao rp  ON rp.role_id = r.id
        JOIN permissao p        ON p.id = rp.permissao_id
        ORDER BY u.id, r.id, p.id
        """;

public List<Usuario> findAll() {
    List<UsuarioDTO> rows = jdbcTemplate.query(SQL, (rs, rowNum) -> {
        UsuarioDTO dto = new UsuarioDTO();
        dto.setIdUsuario(rs.getLong("id_usuario"));
        dto.setUsuarioMatricula(rs.getString("usuario_matricula"));
        dto.setUsuarioNome(rs.getString("usuario_nome"));
        dto.setUsuarioEmail(rs.getString("usuario_email"));
        dto.setUsuarioAtivo(rs.getString("usuario_ativo"));
        dto.setRoleId(rs.getLong("role_id"));
        dto.setRoleNome(rs.getString("role_nome"));
        dto.setPermissaoId(rs.getLong("permissao_id"));
        dto.setPermissaoNome(rs.getString("permissao_nome"));
        return dto;
    });

    return FlatGraphMapper.map(rows, UsuarioDTO.class);
}
```

## Contribuindo

```bash
# Clone
git clone https://github.com/seu-usuario/flat-graph-mapper.git
```


---

## Licença

MIT © 2024 — veja o arquivo [LICENSE](LICENSE) para detalhes.

> Transform flat SQL JOIN result-sets into typed, hierarchical Java object graphs — with zero frameworks, zero boilerplate.

---