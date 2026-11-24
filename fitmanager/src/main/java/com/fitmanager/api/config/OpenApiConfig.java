package com.fitmanager.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fitManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FitManager API")
                        .version("v1")
                        .description("API REST para gerenciamento de academias: alunos, exercícios, fichas de treino e evolução."));
    }
}
