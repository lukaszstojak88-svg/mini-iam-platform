package pl.stojak.miniiam.user.integration.keycloak;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.List;

/**
 * Wires the Keycloak Admin REST API client: a {@link RestClient} with timeouts that attaches a cached
 * Client Credentials token of the technical client {@code mini-iam-admin} (registration {@value #REGISTRATION_ID}).
 */
@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakAdminClientConfig {

    static final String REGISTRATION_ID = "keycloak-admin";

    /** Tokens are not tied to the end user: all admin calls are made on behalf of the service itself. */
    private static final Authentication SERVICE_PRINCIPAL =
            UsernamePasswordAuthenticationToken.authenticated("user-service", null, List.of());

    @Bean
    KeycloakAdminClient keycloakAdminClient(KeycloakAdminProperties properties,
                                            ClientRegistrationRepository clientRegistrations,
                                            OAuth2AuthorizedClientService authorizedClientService) {
        JdkClientHttpRequestFactory requestFactory = requestFactory(properties);

        OAuth2ClientHttpRequestInterceptor tokenInterceptor = new OAuth2ClientHttpRequestInterceptor(
                authorizedClientManager(clientRegistrations, authorizedClientService, requestFactory));
        tokenInterceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        tokenInterceptor.setPrincipalResolver(request -> SERVICE_PRINCIPAL);
        // Drop a cached token that Keycloak rejects (401/403), so the next call obtains a fresh one.
        tokenInterceptor.setAuthorizationFailureHandler(
                OAuth2ClientHttpRequestInterceptor.authorizationFailureHandler(authorizedClientService));

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl() + "/admin/realms/" + properties.realm())
                .requestFactory(requestFactory)
                .requestInterceptor(tokenInterceptor)
                .build();
        return new KeycloakAdminClient(restClient);
    }

    private static AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrations,
            OAuth2AuthorizedClientService authorizedClientService,
            JdkClientHttpRequestFactory requestFactory) {
        RestClient tokenRestClient = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.clear();
                    converters.add(new FormHttpMessageConverter());
                    converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        RestClientClientCredentialsTokenResponseClient tokenClient = new RestClientClientCredentialsTokenResponseClient();
        tokenClient.setRestClient(tokenRestClient);

        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(clientCredentials -> clientCredentials.accessTokenResponseClient(tokenClient))
                .build();
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrations, authorizedClientService);
        manager.setAuthorizedClientProvider(provider);
        return manager;
    }

    private static JdkClientHttpRequestFactory requestFactory(KeycloakAdminProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }
}
