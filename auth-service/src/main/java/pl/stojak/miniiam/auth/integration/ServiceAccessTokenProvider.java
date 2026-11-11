package pl.stojak.miniiam.auth.integration;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Supplies the (cached) Client Credentials access token of the technical client {@code mini-iam-service}
 * (realm role SERVICE only) for service-to-service calls.
 * The token value is never logged.
 */
public class ServiceAccessTokenProvider {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public ServiceAccessTokenProvider(OAuth2AuthorizedClientManager authorizedClientManager,
                                      OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientManager = authorizedClientManager;
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * Drops the cached token, e.g. after the target service rejected it (401/403 - Keycloak keys rotated,
     * realm re-imported). The next call obtains a fresh token.
     */
    public void invalidate() {
        authorizedClientService.removeAuthorizedClient(ServiceTokenConfig.SERVICE_REGISTRATION_ID,
                ServiceTokenConfig.SERVICE_PRINCIPAL.getName());
    }

    /**
     * @throws OAuth2AuthorizationException when the token cannot be obtained (wrong secret, Keycloak unavailable)
     */
    public String accessToken() {
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(OAuth2AuthorizeRequest
                .withClientRegistrationId(ServiceTokenConfig.SERVICE_REGISTRATION_ID)
                .principal(ServiceTokenConfig.SERVICE_PRINCIPAL)
                .build());
        if (client == null) {
            throw new OAuth2AuthorizationException(new OAuth2Error("unauthorized_client",
                    "Client registration " + ServiceTokenConfig.SERVICE_REGISTRATION_ID + " cannot be authorized", null));
        }
        return client.getAccessToken().getTokenValue();
    }
}
