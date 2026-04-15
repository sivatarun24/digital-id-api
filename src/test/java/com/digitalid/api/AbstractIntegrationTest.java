package com.digitalid.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for all integration tests (*IT.java) in this project.
 *
 * Convention:
 *   - Unit tests:       *Test.java  — run by Surefire, use H2 + Mockito, profile "test"
 *   - Integration tests: *IT.java   — run by Failsafe, use real MySQL via Testcontainers, profile "test-integration"
 *
 * The MySQL container is shared across all subclasses (static field) so it starts once
 * per JVM, not once per test class. This keeps CI fast.
 *
 * SpringBootTest default webEnvironment is MOCK (no actual HTTP port), which is correct
 * for service-layer tests. Controller-layer integration tests may override to RANDOM_PORT.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test-integration")
public abstract class AbstractIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("digital_id_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(true);  // reuse container across test classes in the same JVM

    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
