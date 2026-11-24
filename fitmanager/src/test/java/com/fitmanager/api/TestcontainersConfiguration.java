package com.fitmanager.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL descartável para os testes: container próprio, porta aleatória e credenciais geradas
 * pelo Testcontainers. Não usa o .env nem o container de desenvolvimento (fitmanager-db).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Mesma imagem do docker-compose.yml, para testar contra a versão real do banco.
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
