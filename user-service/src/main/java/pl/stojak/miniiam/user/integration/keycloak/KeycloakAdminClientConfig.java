package pl.stojak.miniiam.user.integration.keycloak;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import pl.stojak.miniiam.user.integration.ServiceTokenConfig;

/**
 * Wires the Keycloak Admin REST API client: a {@link RestClient} with timeouts that attaches the cached
 * Client Credentials token of the technical client mini-iam-admin (see {@link ServiceTokenConfig}).
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminClientConfig {

    @Bean
    KeycloakAdminClient keycloakAdminClient(KeycloakAdminProperties properties,
                                            OAuth2AuthorizedClientManager serviceAuthorizedClientManager,
                                            OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2ClientHttpRequestInterceptor tokenInterceptor =
                new OAuth2ClientHttpRequestInterceptor(serviceAuthorizedClientManager);
        tokenInterceptor.setClientRegistrationIdResolver(request -> ServiceTokenConfig.ADMIN_REGISTRATION_ID);
        tokenInterceptor.setPrincipalResolver(request -> ServiceTokenConfig.SERVICE_PRINCIPAL);
        // Drop a cached token that Keycloak rejects (401/403), so the next call obtains a fresh one.
        tokenInterceptor.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClientService));

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl() + "/admin/realms/" + properties.realm())
                .requestFactory(ServiceTokenConfig.requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .requestInterceptor(tokenInterceptor)
                .build();
        return new KeycloakAdminClient(restClient);
    }
}
