package com.fitmanager.api.config;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitmanager.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeOpenApiDocumentationWithTitleAndVersion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.1")))
                .andExpect(jsonPath("$.info.title").value("FitManager API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    void shouldDocumentTheFiveStudentEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/students'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/students'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/students'].get").exists())
                .andExpect(jsonPath("$.paths['/api/students/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/students/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/students/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/students/{id}'].delete").doesNotExist())
                // Pageable expandido em parâmetros (@ParameterObject), e não como um objeto "pageable".
                .andExpect(jsonPath("$.paths['/api/students'].get.parameters[*].name",
                        hasItems("search", "status", "page", "size", "sort")));
    }
}
