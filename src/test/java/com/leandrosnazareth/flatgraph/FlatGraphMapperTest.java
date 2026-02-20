package com.leandrosnazareth.flatgraph;

import com.leandrosnazareth.flatgraph.domain.Permissao;
import com.leandrosnazareth.flatgraph.domain.Role;
import com.leandrosnazareth.flatgraph.domain.Usuario;
import com.leandrosnazareth.flatgraph.dto.UsuarioDTO;
import com.leandrosnazareth.flatgraph.engine.GraphMappingException;
import com.leandrosnazareth.flatgraph.engine.NullIdStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FlatGraphMapper}.
 *
 * <p>The test data simulates a typical SQL JOIN result:
 * <pre>
 * idUsuario | nome    | roleId | roleNome | permissaoId | permissaoNome
 * ----------+---------+--------+----------+-------------+---------------
 *     1     | Alice   |   10   | ADMIN    |     100     | READ
 *     1     | Alice   |   10   | ADMIN    |     101     | WRITE
 *     1     | Alice   |   20   | USER     |     102     | READ
 *     2     | Bob     |   20   | USER     |     102     | READ
 * </pre>
 */
@DisplayName("FlatGraphMapper")
class FlatGraphMapperTest {

    // -------------------------------------------------------------------------
    // fixtures
    // -------------------------------------------------------------------------

    private List<UsuarioDTO> buildRows() {
        return List.of(
            // Alice – ADMIN – READ
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                          10L, "ADMIN", 100L, "READ"),
            // Alice – ADMIN – WRITE
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                          10L, "ADMIN", 101L, "WRITE"),
            // Alice – USER – READ
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                          20L, "USER", 102L, "READ"),
            // Bob – USER – READ
            UsuarioDTO.of(2L, "MAT-002", "Bob", "bob@test.com", "S",
                          20L, "USER", 102L, "READ")
        );
    }

    // -------------------------------------------------------------------------
    // tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("should return two root Usuario objects")
    void shouldReturnTwoUsers() {
        List<Usuario> result = FlatGraphMapper.map(buildRows(), UsuarioDTO.class);
        assertEquals(2, result.size(), "Expected 2 distinct usuarios");
    }

    @Test
    @DisplayName("should populate scalar fields on root")
    void shouldPopulateRootFields() {
        List<Usuario> result = FlatGraphMapper.map(buildRows(), UsuarioDTO.class);
        Usuario alice = result.get(0);

        assertEquals(1L,            alice.getId());
        assertEquals("MAT-001",     alice.getMatricula());
        assertEquals("Alice",       alice.getNome());
        assertEquals("alice@test.com", alice.getEmail());
        assertEquals("S",           alice.getAtivo());
    }

    @Test
    @DisplayName("Alice should have 2 roles (ADMIN and USER) — no duplicates")
    void aliceShouldHaveTwoRoles() {
        List<Usuario> result = FlatGraphMapper.map(buildRows(), UsuarioDTO.class);
        Usuario alice = result.get(0);

        assertEquals(2, alice.getRoles().size(),
            "Alice should have exactly 2 roles, but got: " + alice.getRoles());
    }

    @Test
    @DisplayName("Bob should have 1 role (USER)")
    void bobShouldHaveOneRole() {
        List<Usuario> result = FlatGraphMapper.map(buildRows(), UsuarioDTO.class);
        Usuario bob = result.get(1);

        assertEquals(1, bob.getRoles().size());
        assertEquals("USER", bob.getRoles().get(0).getNome());
    }

    @Test
    @DisplayName("ADMIN role of Alice should have 2 permissoes (READ and WRITE)")
    void adminRoleShouldHaveTwoPermissoes() {
        List<Usuario> result = FlatGraphMapper.map(buildRows(), UsuarioDTO.class);
        Usuario alice = result.get(0);

        Role adminRole = alice.getRoles().stream()
            .filter(r -> "ADMIN".equals(r.getNome()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ADMIN role not found"));

        assertEquals(2, adminRole.getPermissoes().size(),
            "ADMIN role should have READ and WRITE, got: " + adminRole.getPermissoes());

        List<String> permNames = adminRole.getPermissoes().stream()
            .map(Permissao::getNome).toList();
        assertTrue(permNames.contains("READ"));
        assertTrue(permNames.contains("WRITE"));
    }

    @Test
    @DisplayName("same Permissao (READ/102) is NOT duplicated across roles that share it")
    void sharedPermissaoIsNotDuplicated() {
        List<Usuario> result = FlatGraphMapper.map(buildRows(), UsuarioDTO.class);
        Usuario alice = result.get(0);

        Role userRole = alice.getRoles().stream()
            .filter(r -> "USER".equals(r.getNome()))
            .findFirst()
            .orElseThrow();

        assertEquals(1, userRole.getPermissoes().size(),
            "USER role of Alice should have exactly 1 permissao");
    }

    @Test
    @DisplayName("empty input returns empty list")
    void emptyInputReturnsEmptyList() {
        List<Usuario> result = FlatGraphMapper.map(List.of(), UsuarioDTO.class);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("single row produces a complete graph")
    void singleRowProducesCompleteGraph() {
        List<UsuarioDTO> single = List.of(
            UsuarioDTO.of(99L, "MAT-099", "Carol", "carol@test.com", "S",
                          30L, "SUPERUSER", 200L, "DELETE")
        );

        List<Usuario> result = FlatGraphMapper.map(single, UsuarioDTO.class);

        assertEquals(1, result.size());
        Usuario carol = result.get(0);
        assertEquals("Carol", carol.getNome());
        assertEquals(1, carol.getRoles().size());
        assertEquals("SUPERUSER", carol.getRoles().get(0).getNome());
        assertEquals(1, carol.getRoles().get(0).getPermissoes().size());
        assertEquals("DELETE", carol.getRoles().get(0).getPermissoes().get(0).getNome());
    }

    @Test
    @DisplayName("null root ID rows are skipped")
    void nullRootIdRowsAreSkipped() {
        List<UsuarioDTO> rows = List.of(
            UsuarioDTO.of(null, null, null, null, null, null, null, null, null),
            UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                          10L, "ADMIN", 100L, "READ")
        );

        List<Usuario> result = FlatGraphMapper.map(rows, UsuarioDTO.class);
        assertEquals(1, result.size());
    }

    // -------------------------------------------------------------------------
    // NullIdStrategy tests
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("NullIdStrategy")
    class NullIdStrategyTests {

        /** Row with Alice having a null roleId — simulates LEFT JOIN with no role. */
        private List<UsuarioDTO> rowsWithNullChildId() {
            return List.of(
                // Alice sem role (LEFT JOIN miss)
                UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                              null, null, null, null),
                // Bob com role normal
                UsuarioDTO.of(2L, "MAT-002", "Bob", "bob@test.com", "S",
                              10L, "ADMIN", 100L, "READ")
            );
        }

        @Test
        @DisplayName("SKIP — child with null ID is ignored, parent is still created")
        void skipStrategy_childIgnored_parentCreated() {
            List<Usuario> result = FlatGraphMapper.map(
                rowsWithNullChildId(), UsuarioDTO.class, NullIdStrategy.SKIP);

            assertEquals(2, result.size(), "Both root users must be present");

            Usuario alice = result.get(0);
            assertEquals("Alice", alice.getNome());
            assertTrue(alice.getRoles().isEmpty(),
                "Alice should have no roles since roleId was null");

            Usuario bob = result.get(1);
            assertEquals(1, bob.getRoles().size());
            assertEquals("ADMIN", bob.getRoles().get(0).getNome());
        }

        @Test
        @DisplayName("SKIP — is the default strategy (no explicit argument)")
        void skipStrategy_isDefault() {
            List<Usuario> explicit = FlatGraphMapper.map(
                rowsWithNullChildId(), UsuarioDTO.class, NullIdStrategy.SKIP);
            List<Usuario> implicit = FlatGraphMapper.map(
                rowsWithNullChildId(), UsuarioDTO.class);

            assertEquals(explicit.size(), implicit.size());
            assertEquals(explicit.get(0).getNome(), implicit.get(0).getNome());
            assertEquals(explicit.get(0).getRoles().size(), implicit.get(0).getRoles().size());
        }

        @Test
        @DisplayName("THROW — throws GraphMappingException on null child ID")
        void throwStrategy_throwsOnNullChildId() {
            GraphMappingException ex = assertThrows(GraphMappingException.class, () ->
                FlatGraphMapper.map(rowsWithNullChildId(), UsuarioDTO.class, NullIdStrategy.THROW)
            );

            assertTrue(ex.getMessage().contains("Null ID"),
                "Exception message should mention null ID, got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains(Role.class.getName()),
                "Exception message should name the child class");
        }

        @Test
        @DisplayName("THROW — does not throw when all child IDs are present")
        void throwStrategy_noThrowWhenAllIdsPresent() {
            List<UsuarioDTO> cleanRows = List.of(
                UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                              10L, "ADMIN", 100L, "READ")
            );

            assertDoesNotThrow(() ->
                FlatGraphMapper.map(cleanRows, UsuarioDTO.class, NullIdStrategy.THROW));
        }

        @Test
        @DisplayName("ALLOW_NULL_ID — creates child with null ID and populates non-ID fields")
        void allowNullId_childCreatedWithNullId() {
            List<UsuarioDTO> rows = List.of(
                // roleId null, but roleNome has a value
                UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                              null, "GHOST_ROLE", null, null)
            );

            List<Usuario> result = FlatGraphMapper.map(
                rows, UsuarioDTO.class, NullIdStrategy.ALLOW_NULL_ID);

            assertEquals(1, result.size());
            Usuario alice = result.get(0);
            assertEquals(1, alice.getRoles().size(),
                "Child should be created even with null ID");

            Role ghostRole = alice.getRoles().get(0);
            assertNull(ghostRole.getId(), "Role ID should be null");
            assertEquals("GHOST_ROLE", ghostRole.getNome(),
                "Non-ID fields should still be populated");
        }

        @Test
        @DisplayName("ALLOW_NULL_ID — multiple rows with null child ID share one instance")
        void allowNullId_multipleNullRowsShareOneInstance() {
            List<UsuarioDTO> rows = List.of(
                UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                              null, "GHOST_ROLE", null, null),
                UsuarioDTO.of(1L, "MAT-001", "Alice", "alice@test.com", "S",
                              null, "GHOST_ROLE", null, null)  // same null ID again
            );

            List<Usuario> result = FlatGraphMapper.map(
                rows, UsuarioDTO.class, NullIdStrategy.ALLOW_NULL_ID);

            assertEquals(1, result.size());
            assertEquals(1, result.get(0).getRoles().size(),
                "Duplicate null-ID child should be deduplicated into one instance");
        }
    }
}
