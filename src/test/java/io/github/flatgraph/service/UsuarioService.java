package io.github.flatgraph.service;

import io.github.flatgraph.FlatGraphMapper;
import io.github.flatgraph.domain.Usuario;
import io.github.flatgraph.dto.UsuarioDTO;

import java.util.List;
import java.util.Optional;

/**
 * Exemplo de Service que usa o {@link FlatGraphMapper} para transformar
 * o resultado plano de um JOIN SQL em um grafo de objetos hierárquico.
 *
 * <p>Em um projeto real, {@code findAllFlat()} seria substituído por uma
 * chamada JDBC/JdbcTemplate/NamedParameterJdbcTemplate que retorna
 * {@code List<UsuarioDTO>} mapeado linha a linha pelo ResultSet.
 *
 * <p>Esta classe é intencionalmente livre de frameworks (sem Spring, sem JPA)
 * para demonstrar que a biblioteca funciona em qualquer contexto Java.
 */
public class UsuarioService {

    /** Repositório/gateway que devolve linhas planas do banco. */
    private final UsuarioRepository repository;

    public UsuarioService(UsuarioRepository repository) {
        this.repository = repository;
    }

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Retorna todos os usuários com seus roles e permissões populados.
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Busca as linhas planas (JOIN SQL) via repositório.</li>
     *   <li>Passa para {@link FlatGraphMapper#map} que monta o grafo.</li>
     *   <li>Devolve a lista final sem duplicações.</li>
     * </ol>
     */
    public List<Usuario> findAll() {
        List<UsuarioDTO> rows = repository.findAllFlat();
        return FlatGraphMapper.map(rows, UsuarioDTO.class);
    }

    /**
     * Retorna um único usuário pelo ID, com grafo completo.
     */
    public Optional<Usuario> findById(Long id) {
        List<UsuarioDTO> rows = repository.findByIdFlat(id);
        List<Usuario> result = FlatGraphMapper.map(rows, UsuarioDTO.class);
        return result.stream().findFirst();
    }

    /**
     * Retorna apenas usuários ativos.
     */
    public List<Usuario> findAtivos() {
        List<UsuarioDTO> rows = repository.findAllFlat();
        List<Usuario> all = FlatGraphMapper.map(rows, UsuarioDTO.class);
        return all.stream()
                .filter(u -> "S".equals(u.getAtivo()))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Interface do repositório (substituir por implementação JDBC real)
    // -------------------------------------------------------------------------

    /**
     * Contrato do repositório que fornece as linhas planas.
     * Em produção, implemente com JDBC, JdbcTemplate, etc.
     */
    public interface UsuarioRepository {
        List<UsuarioDTO> findAllFlat();
        List<UsuarioDTO> findByIdFlat(Long id);
    }

    // -------------------------------------------------------------------------
    // Implementação in-memory para fins de demonstração / testes
    // -------------------------------------------------------------------------

    /**
     * Repositório em memória com dados fixos — útil para testes e demos.
     *
     * <p>Simula o resultado de uma query SQL como:
     * <pre>{@code
     * SELECT u.id, u.matricula, u.nome, u.email, u.ativo,
     *        r.id, r.nome,
     *        p.id, p.nome
     * FROM usuario u
     * JOIN usuario_role ur ON ur.usuario_id = u.id
     * JOIN role r           ON r.id = ur.role_id
     * JOIN role_permissao rp ON rp.role_id = r.id
     * JOIN permissao p       ON p.id = rp.permissao_id
     * }</pre>
     */
    public static class InMemoryUsuarioRepository implements UsuarioRepository {

        private static final List<UsuarioDTO> ALL_ROWS = List.of(
            // Alice – ADMIN – READ
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@corp.com", "S",
                          10L, "ADMIN", 100L, "READ"),
            // Alice – ADMIN – WRITE
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@corp.com", "S",
                          10L, "ADMIN", 101L, "WRITE"),
            // Alice – USER – READ
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@corp.com", "S",
                          20L, "USER", 102L, "READ"),
            // Bob – USER – READ
            UsuarioDTO.of(2L, "MAT-002", "Bob", "bob@corp.com", "N",
                          20L, "USER", 102L, "READ"),
            // Carol – SUPERUSER – DELETE
            UsuarioDTO.of(3L, "MAT-003", "Carol", "carol@corp.com", "S",
                          30L, "SUPERUSER", 103L, "DELETE")
        );

        @Override
        public List<UsuarioDTO> findAllFlat() {
            return ALL_ROWS;
        }

        @Override
        public List<UsuarioDTO> findByIdFlat(Long id) {
            return ALL_ROWS.stream()
                    .filter(dto -> id.equals(extractId(dto)))
                    .toList();
        }

        /** Extrai o idUsuario do DTO via campo público de fábrica (for demo only). */
        private Long extractId(UsuarioDTO dto) {
            // Na prática o DTO teria getter; aqui usamos a identidade dos dados fixos.
            return ALL_ROWS.stream()
                    .filter(r -> r == dto)
                    .map(r -> {
                        // Re-cria apenas para inspecionar — em produção use getter ou record
                        for (var f : r.getClass().getDeclaredFields()) {
                            if (f.getName().equals("idUsuario")) {
                                f.setAccessible(true);
                                try { return (Long) f.get(r); } catch (Exception ignored) {}
                            }
                        }
                        return null;
                    })
                    .findFirst()
                    .orElse(null);
        }
    }
}
