# FlatGraphMapper

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Build](https://img.shields.io/badge/build-Maven-blue?logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

> Transforme o resultado plano de um SQL JOIN em um grafo de objetos hierárquico — sem JPA, sem Spring, sem Hibernate.

---

## Sumário

- [O Problema](#o-problema)
- [A Solução](#a-solução)
- [Como Funciona](#como-funciona)
- [Instalação](#instalação)
- [Guia de Uso](#guia-de-uso)
  - [1. Crie seu modelo de domínio](#1-crie-seu-modelo-de-domínio)
  - [2. Anote seu DTO](#2-anote-seu-dto)
  - [3. Execute o mapeamento](#3-execute-o-mapeamento)
- [Referência das Annotations](#referência-das-annotations)
- [Hierarquias Profundas](#hierarquias-profundas)
- [Integração com JDBC](#integração-com-jdbc)
- [Arquitetura Interna](#arquitetura-interna)
- [Performance](#performance)
- [Limitações Conhecidas](#limitações-conhecidas)
- [Contribuindo](#contribuindo)

---

## O Problema

Ao executar um `JOIN` entre múltiplas tabelas, o resultado JDBC é sempre **plano** — cada linha repete os dados do pai para cada filho:

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

Com o **FlatGraphMapper**, você apenas:

1. Anota os campos do DTO com `@ParentField` e `@ChildField`
2. Chama `FlatGraphMapper.map(rows, MeuDTO.class)`
3. Recebe a lista de objetos raiz com todo o grafo montado e **sem duplicações**

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

---

## Como Funciona

```
List<UsuarioDTO>
       │
       ▼
 MetadataExtractor  ──── cache ────►  ClassMetadata
 (scan de annotations,                (FieldMappings pré-processados,
  executado 1x por DTO class)          Constructor handles abertos)
       │
       ▼
 GraphBuildEngine  (por chamada)
 ┌──────────────────────────────────────────────┐
 │  para cada linha do DTO:                     │
 │  1. extrai rootId  → pula se null            │
 │  2. get/cria root  → seta campos (1x)        │
 │  3. para cada childClass (em ordem):         │
 │     a. extrai childId → pula se null         │
 │     b. get/cria child → seta campos (1x)     │
 │     c. linka ao List do pai                  │
 └──────────────────────────────────────────────┘
       │
       ▼
 List<Usuario>  ← grafo completo, sem duplicações
```

A chave do algoritmo são dois **mapas de identidade**:

| Mapa | Chave | Valor |
|---|---|---|
| `rootMap` | `rootId` | instância do objeto raiz |
| `childMap` | `(targetClass, childId)` | instância do objeto filho |

Se um ID já existe no mapa, a instância é **reutilizada** e os campos **não são sobrescritos** — a primeira linha vence.

---

## Instalação

### Maven

```xml
<dependency>
    <groupId>io.github.flatgraph</groupId>
    <artifactId>flat-graph-mapper</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.flatgraph:flat-graph-mapper:1.0.0'
```

### Build local

```bash
git clone https://github.com/seu-usuario/flat-graph-mapper.git
cd flat-graph-mapper
mvn clean install
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

### 3. Execute o mapeamento

```java
// rows vem do seu JDBC / repositório
List<UsuarioDTO> rows = repository.findAllFlat();

List<Usuario> usuarios = FlatGraphMapper.map(rows, UsuarioDTO.class);

// Resultado:
// usuarios.size()                                           → 2  (Alice e Bob)
// usuarios.get(0).getNome()                                → "Alice"
// usuarios.get(0).getRoles().size()                        → 2  (ADMIN, USER)
// usuarios.get(0).getRoles().get(0).getNome()              → "ADMIN"
// usuarios.get(0).getRoles().get(0).getPermissoes().size() → 2  (READ, WRITE)
```

---

## Referência das Annotations

### `@ParentField`

Mapeia um campo do DTO para um campo da **classe raiz** do grafo.

| Atributo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `target` | `Class<?>` | ✅ | Classe raiz do domínio |
| `field` | `String` | ✅ | Nome exato do campo em `target` |

```java
@ParentField(target = Usuario.class, field = "nome")
private String usuarioNome;
```

---

### `@ChildField`

Mapeia um campo do DTO para um campo de uma **classe filha** do grafo.

| Atributo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `target` | `Class<?>` | ✅ | Classe filha do domínio |
| `field` | `String` | ✅ | Nome exato do campo em `target` |
| `parent` | `Class<?>` | ✅ | Classe pai que contém um `List<target>` |

```java
@ChildField(target = Role.class, field = "nome", parent = Usuario.class)
private String roleNome;
```

> A classe `parent` deve possuir um campo do tipo `List<target>`.
> O engine localiza esse campo automaticamente por introspecção do tipo genérico.

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

---

## Arquitetura Interna

```
io.github.flatgraph
│
├── FlatGraphMapper              ← API pública (1 método estático)
│
├── annotation/
│   ├── @ParentField             ← marca campo do DTO como raiz
│   └── @ChildField              ← marca campo do DTO como filho
│
├── metadata/
│   ├── FieldMapping             ← record imutável: dtoField + domainField + metadados
│   ├── ClassMetadata            ← record imutável: todos os FieldMappings de um DTO
│   └── MetadataExtractor        ← scan de annotations + cache thread-safe
│
└── engine/
    ├── GraphBuildEngine         ← algoritmo de montagem do grafo
    └── GraphMappingException    ← exceção não-verificada
```

### Decisões arquiteturais

| Decisão | Motivo |
|---|---|
| `ConcurrentHashMap` no `MetadataExtractor` | Leituras lock-free após warm-up; seguro para múltiplas threads |
| `Field#setAccessible(true)` na criação do cache | Elimina verificação de segurança JVM no loop quente |
| `LinkedHashMap` nos mapas de identidade | Preserva a ordem de inserção → resultado na mesma ordem do SQL |
| `record FieldMapping` | Imutabilidade garantida pela linguagem; `equals`/`hashCode` automáticos |
| `ChildKey(targetClass, id)` como chave | Evita colisão entre tipos filhos diferentes com o mesmo ID numérico |
| Escrita de campos apenas na 1ª ocorrência | Evita sobrescrita desnecessária e expõe inconsistências de dados |

---

## Performance

| Operação | Complexidade | Frequência |
|---|---|---|
| Scan de annotations + `setAccessible` | O(campos do DTO) | **1× por DTO class** (cached) |
| Lookup de instância nos mapas de identidade | O(1) | Por linha |
| Instanciação via reflexão | O(1) | Apenas para novas instâncias |
| Busca de `List<?>` no pai | O(campos da classe) | **1× por par (pai, filho)** (cached) |

Para grandes volumes o custo dominante é a iteração da lista de entrada — tudo mais é amortizado pelo cache.

---

## Limitações Conhecidas

| Limitação | Contorno |
|---|---|
| Todos os `@ParentField` devem apontar para a mesma classe raiz | Use um DTO por raiz |
| A chave de deduplicação deve se chamar `id` no domínio | Use um campo wrapper com esse nome |
| Coleções no domínio devem ser `List<T>` (não `Set` ou `Map`) | Versão futura adicionará suporte |
| IDs primitivos (`int`, `long`) não são suportados | Use os wrappers `Integer`, `Long` |

---

## Contribuindo

```bash
# Clone
git clone https://github.com/seu-usuario/flat-graph-mapper.git
cd flat-graph-mapper

# Compile e teste
mvn clean verify

# Apenas testes
mvn test
```

Pull requests são bem-vindos! Antes de abrir um PR:

- [ ] Todos os testes existentes passam (`mvn test`)
- [ ] Novos comportamentos têm testes correspondentes
- [ ] O código não introduz dependências externas

---

## Licença

MIT © 2024 — veja o arquivo [LICENSE](LICENSE) para detalhes.

> Transform flat SQL JOIN result-sets into typed, hierarchical Java object graphs — with zero frameworks, zero boilerplate.

---

## Table of Contents

1. [Concept](#concept)
2. [How It Works](#how-it-works)
3. [Quick Start](#quick-start)
4. [Annotations Reference](#annotations-reference)
5. [Real Example](#real-example)
6. [Architectural Decisions](#architectural-decisions)
7. [Advantages](#advantages)
8. [Building & Testing](#building--testing)

---

## Concept

When you execute a SQL JOIN across multiple tables, the JDBC result set is **flat** —
every row repeats parent data for each child record:

```
idUsuario | nome   | roleId | roleNome | permissaoId | permissaoNome
----------+--------+--------+----------+-------------+---------------
    1     | Alice  |   10   | ADMIN    |     100     | READ
    1     | Alice  |   10   | ADMIN    |     101     | WRITE
    1     | Alice  |   20   | USER     |     102     | READ
    2     | Bob    |   20   | USER     |     102     | READ
```

FlatGraphMapper turns this into:

```
Usuario(Alice)
  └─ Role(ADMIN)
       ├─ Permissao(READ)
       └─ Permissao(WRITE)
  └─ Role(USER)
       └─ Permissao(READ)
Usuario(Bob)
  └─ Role(USER)
       └─ Permissao(READ)
```

No JPA. No Spring. No Hibernate. Pure Java 17.

---

## How It Works

```
List<UsuarioDTO>  ──►  MetadataExtractor  ──►  ClassMetadata (cached)
                                                       │
                                                       ▼
                                              GraphBuildEngine
                                              ┌──────────────────────────┐
                                              │ for each row:            │
                                              │  1. extract root ID      │
                                              │  2. get/create root obj  │
                                              │  3. set parent fields    │
                                              │  4. get/create children  │
                                              │  5. link to collections  │
                                              └──────────────────────────┘
                                                       │
                                                       ▼
                                              List<Usuario>  (graph)
```

### Key components

| Class | Responsibility |
|---|---|
| `@ParentField` | Marks a DTO field as belonging to the root domain class |
| `@ChildField` | Marks a DTO field as belonging to a child domain class |
| `MetadataExtractor` | Scans annotations once, caches `ClassMetadata` per DTO class |
| `FieldMapping` | Immutable record holding pre-opened `Field` handles |
| `ClassMetadata` | Aggregates all `FieldMapping` lists for a DTO class |
| `GraphBuildEngine` | Stateless engine; builds the graph using identity maps |
| `FlatGraphMapper` | Public API — single static `map()` method |

---

## Quick Start

### 1. Annotate your DTO

```java
public class UsuarioDTO {

    @ParentField(target = Usuario.class, field = "id")
    private Long idUsuario;

    @ParentField(target = Usuario.class, field = "nome")
    private String usuarioNome;

    @ChildField(target = Role.class, field = "id", parent = Usuario.class)
    private Long roleId;

    @ChildField(target = Role.class, field = "nome", parent = Usuario.class)
    private String roleNome;

    @ChildField(target = Permissao.class, field = "id", parent = Role.class)
    private Long permissaoId;

    @ChildField(target = Permissao.class, field = "nome", parent = Role.class)
    private String permissaoNome;
}
```

### 2. Ensure your domain classes have `List<Child>` fields

```java
public class Usuario {
    private Long id;
    private String nome;
    private List<Role> roles = new ArrayList<>();   // ← required
}

public class Role {
    private Long id;
    private String nome;
    private List<Permissao> permissoes = new ArrayList<>();  // ← required
}
```

### 3. Map

```java
List<UsuarioDTO> rows = myRepository.findAllFlat();  // your JDBC/query result
List<Usuario> users   = FlatGraphMapper.map(rows, UsuarioDTO.class);
```

---

## Annotations Reference

### `@ParentField`

```java
@ParentField(
    target = Usuario.class,   // root domain class
    field  = "nome"           // field name in target class
)
private String usuarioNome;
```

- All `@ParentField` annotations on the same DTO **must** point to the same `target` class.
- The field annotated with `field = "id"` is used as the **de-duplication key** for the root.

### `@ChildField`

```java
@ChildField(
    target = Role.class,      // child domain class
    field  = "nome",          // field name in target class
    parent = Usuario.class    // which domain class holds List<Role>
)
private String roleNome;
```

- `parent` must have a `List<target>` field somewhere in its class hierarchy.
- The field annotated with `field = "id"` is used as the **de-duplication key** for that child.
- You can chain as many levels as needed (e.g. `Role → Permissao → SubPermissao`).

---

## Real Example

```java
// Simulated flat rows (would come from JDBC)
List<UsuarioDTO> rows = List.of(
    UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@corp.com", "S",
                  10L, "ADMIN", 100L, "READ"),
    UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@corp.com", "S",
                  10L, "ADMIN", 101L, "WRITE"),
    UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@corp.com", "S",
                  20L, "USER",  102L, "READ"),
    UsuarioDTO.of(2L, "MAT-002", "Bob",   "bob@corp.com",  "S",
                  20L, "USER",  102L, "READ")
);

List<Usuario> users = FlatGraphMapper.map(rows, UsuarioDTO.class);

// users.size() == 2
// users.get(0).getNome()                            == "Alice"
// users.get(0).getRoles().size()                    == 2
// users.get(0).getRoles().get(0).getNome()          == "ADMIN"
// users.get(0).getRoles().get(0).getPermissoes()    == [READ, WRITE]
```

---

## Architectural Decisions

### Why records for `FieldMapping` and `ClassMetadata`?
Records are immutable by design. Metadata is computed once and never changes — records make this contract explicit and give `equals`/`hashCode` for free.

### Why `ConcurrentHashMap` + `computeIfAbsent` instead of `synchronized`?
`computeIfAbsent` on `ConcurrentHashMap` is lock-free for reads and uses fine-grained segment locking for writes. In a multi-threaded environment (e.g. a web server with a shared mapper), this avoids a global lock on every mapping call.

### Why call `Field#setAccessible(true)` at metadata-extraction time?
The JVM permission check inside `Field.get()` is skipped when the field is already accessible. Setting it once at startup amortises the cost across all rows in the hot mapping loop.

### Why `LinkedHashMap` for identity maps?
Insertion order is preserved so the returned `List<R>` matches the order of first appearance in the input, which is typically the natural SQL sort order.

### Why separate `MetadataExtractor` from `GraphBuildEngine`?
Single Responsibility Principle. The extractor knows about annotations and reflection; the engine knows about graph construction. They can evolve independently.

---

## Advantages

| Feature | Benefit |
|---|---|
| Zero dependencies | No framework lock-in; works anywhere Java runs |
| Annotation-driven | No XML, no code generation, no build plugins |
| One-time reflection cost | Metadata cache means O(1) per row after warmup |
| Arbitrary depth | Chain `@ChildField` annotations to any depth |
| Thread-safe | Concurrent reads from cached engines; no shared mutable state |
| Null-safe | Rows with null root IDs are silently skipped |
| Clean API | One method: `FlatGraphMapper.map(rows, DtoClass.class)` |

---

## Building & Testing

```bash
# Build
mvn clean package

# Run tests
mvn test

# Run tests with verbose output
mvn test -Dsurefire.useFile=false
```

**Requirements:** Java 17+, Maven 3.8+
