package pl.stojak.miniiam.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.keycloak-admin.client-secret=unused-in-this-test",
        "spring.security.oauth2.client.registration.mini-iam-service.client-secret=unused-in-this-test"
})
@AutoConfigureMockMvc
@Testcontainers
class UserServiceApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("init-schema.sql");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseRunsOnCleanDatabaseInOwnSchema() {
        Integer changelogTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'users' AND table_name = 'databasechangelog'",
                Integer.class);

        assertThat(changelogTables).isEqualTo(1);

        // Spec §12.3 (12): the business table is built by Liquibase only (ddl-auto=validate), in the own schema.
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'users' AND table_name = 'users'",
                String.class);
        assertThat(columns).containsExactlyInAnyOrder("id", "keycloak_id", "username", "email", "first_name", "last_name", "enabled", "created_at", "updated_at", "version");

        Integer publicTables = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
        assertThat(publicTables).isZero();
    }

    @Test
    void healthEndpointIsUpAndReturnsCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "it-test-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "it-test-1"));
    }
}
