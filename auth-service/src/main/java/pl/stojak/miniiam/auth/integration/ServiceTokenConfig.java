package pl.stojak.miniiam.auth.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;
import pl.stojak.miniiam.auth.integration.keycloak.KeycloakAdminProperties;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Client Credentials tokens of the technical client {@code mini-iam-admin} (registration {@value #REGISTRATION_ID}),
 * shared by the Keycloak Admin API client and the Feign clients of other services. Tokens are cached by
 * {@link OAuth2AuthorizedClientService} and refreshed when expired.
 */
@Configuration
public class ServiceTokenConfig {

    public static final String REGISTRATION_ID = "keycloak-admin";

    /** Tokens are not tied to the end user: all calls are made on behalf of the service itself. */
    public static final Authentication SERVICE_PRINCIPAL =
            UsernamePasswordAuthenticationToken.authenticated("auth-service", null, List.of());

    @Bean
    OAuth2AuthorizedClientManager serviceAuthorizedClientManager(ClientRegistrationRepository clientRegistrations,
                                                                 OAuth2AuthorizedClientService authorizedClientService,
                                                                 KeycloakAdminProperties properties) {
        RestClient tokenRestClient = RestClient.builder()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
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

    @Bean
    ServiceAccessTokenProvider serviceAccessTokenProvider(OAuth2AuthorizedClientManager serviceAuthorizedClientManager,
                                                          OAuth2AuthorizedClientService authorizedClientService) {
        return new ServiceAccessTokenProvider(serviceAuthorizedClientManager, authorizedClientService);
    }

    /** HTTP/1.1 JDK client with explicit timeouts (spec §7). */
    public static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
