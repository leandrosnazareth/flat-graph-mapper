package com.leandrosnazareth.flatgraph.domain;

import com.leandrosnazareth.flatgraph.FlatGraphMapper;
import com.leandrosnazareth.flatgraph.annotation.ParentField;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test helper: reads RandomData.json (array of ["id","nome"]) from test resources,
 * maps DTOs to domain objects and asserts mapping.
 */
public class PermissaoTest {

    // Simple DTO used only for this test — both fields are ParentField pointing to Permissao
    public static class PermissaoDTO {
        @ParentField(target = Permissao.class, field = "id")
        private String id;

        @ParentField(target = Permissao.class, field = "nome")
        private String nome;

        public PermissaoDTO() {}

        public PermissaoDTO(String id, String nome) {
            this.id = id;
            this.nome = nome;
        }
    }

    @Test
    public void mapRandomDataJson_toPermissao() throws IOException {
        List<PermissaoDTO> rows = readRandomData("/RandomData.json");
        // map to domain
        List<Permissao> mapped = FlatGraphMapper.map(rows, PermissaoDTO.class);

        // basic assertions: number of items and first id
        assertEquals(rows.size(), mapped.size(), "mapped size should match input rows");
        if (!mapped.isEmpty()) {
            // IDs in JSON are strings, mapper should convert to Long
            Long expectedFirstId = Long.parseLong(rows.get(0).id.trim());
            assertEquals(expectedFirstId, mapped.get(0).getId());
        }
    }

    private List<PermissaoDTO> readRandomData(String resourcePath) throws IOException {
        // Try classpath resource first
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is != null) {
            try (InputStream in = is) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return parseNestedStringArrays(content);
            }
        }

        // Fallback: try filesystem. Allow absolute path or project-relative path.
        // 1) system property `randomDataPath` overrides
        String altPath = System.getProperty("randomDataPath");
        File f = null;
        if (altPath != null && !altPath.isBlank()) {
            f = new File(altPath);
        }
        // 2) try user.dir + resourcePath (resourcePath usually starts with '/')
        if (f == null || !f.exists()) {
            String userDir = System.getProperty("user.dir");
            if (resourcePath.startsWith("/")) {
                f = new File(userDir + resourcePath);
            } else {
                f = new File(userDir, resourcePath);
            }
        }
        // 3) finally try resourcePath as absolute
        if (f == null || !f.exists()) {
            f = new File(resourcePath);
        }

        if (f.exists()) {
            try (InputStream fis = new FileInputStream(f)) {
                String content = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
                return parseNestedStringArrays(content);
            }
        }

        throw new IOException("Resource not found: " + resourcePath + " (classpath and filesystem attempts failed)");
    }

    private List<PermissaoDTO> parseNestedStringArrays(String json) {
        List<PermissaoDTO> out = new ArrayList<>();
        Pattern p = Pattern.compile("\\[\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\]");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String id = m.group(1);
            String nome = m.group(2);
            out.add(new PermissaoDTO(id, nome));
        }
        return out;
    }
}
