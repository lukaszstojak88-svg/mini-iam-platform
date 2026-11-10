package pl.stojak.miniiam.user.integration.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Connection settings for the Keycloak Admin REST API. */
@ConfigurationProperties("app.keycloak.admin")
public record KeycloakAdminProperties(String baseUrl, String realm, Duration connectTimeout, Duration readTimeout) {
}
