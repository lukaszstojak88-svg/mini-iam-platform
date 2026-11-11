package pl.stojak.miniiam.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** auth-service has no database of its own (spec §8.3) - the context starts without a DataSource. */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.keycloak-admin.client-secret=unused-in-this-test",
        "spring.security.oauth2.client.registration.mini-iam-service.client-secret=unused-in-this-test"
})
@AutoConfigureMockMvc
class AuthServiceApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsUpAndReturnsCorrelationId() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Correlation-Id", "it-test-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "it-test-1"));
    }

    @Test
    void generatesCorrelationIdWhenMissing() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
