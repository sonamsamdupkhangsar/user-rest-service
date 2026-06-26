package me.sonam.user.config;

import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import me.sonam.user.handler.UserSignupService;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.webclient.AccountWebClient;
import me.sonam.user.webclient.AuthenticationWebClient;
import me.sonam.user.webclient.OrganizationWebClient;
import me.sonam.user.webclient.RoleWebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile({"eureka", "non-eureka"})
@Configuration
public class WebClientConfig {
    @Value("${account-rest-service.context}")
    private String deleteMyAccountEndpoint;

    @Value("${authentication-rest-service.context}")
    private String deleteMyAuthenticationEndpoint;

    @Value("${organization-rest-service.context}")
    private String deleteMyOrganizationEndpoint;

    @Value("${role-rest-service.context}")
    private String deleteMyRoleEndpoint;

    @Autowired
    private UserRepository userRepository;

    @Value("${tokenExpireSeconds:1}")
    private int tokenExpireSeconds;

    @Bean
    public ReactiveRequestContextHolder reactiveRequestContextHolder(
            @Qualifier("tokenWebClientBuilder") WebClient.Builder tokenWebClientBuilder) {
        return new ReactiveRequestContextHolder(tokenWebClientBuilder, tokenExpireSeconds);
    }

    @Bean
    public UserSignupService userSignupService(AccountWebClient accountWebClient,
                                               AuthenticationWebClient authenticationWebClient,
                                               OrganizationWebClient organizationWebClient,
                                               RoleWebClient roleWebClient) {
        return new UserSignupService(accountWebClient, authenticationWebClient, organizationWebClient, roleWebClient);
    }

    @Bean
    public AccountWebClient accountWebClient(
            @Qualifier("serviceWebClientBuilder") WebClient.Builder serviceWebClientBuilder) {
        return new AccountWebClient(serviceWebClientBuilder, deleteMyAccountEndpoint, userRepository);
    }

    @Bean
    public AuthenticationWebClient authenticationWebClient(
            @Qualifier("serviceWebClientBuilder") WebClient.Builder serviceWebClientBuilder) {
        return new AuthenticationWebClient(serviceWebClientBuilder, deleteMyAuthenticationEndpoint, userRepository);
    }

    @Bean
    public OrganizationWebClient organizationWebClient(
            @Qualifier("serviceWebClientBuilder") WebClient.Builder serviceWebClientBuilder) {
        return new OrganizationWebClient(serviceWebClientBuilder, deleteMyOrganizationEndpoint);
    }

    @Bean
    public RoleWebClient roleWebClient(
            @Qualifier("serviceWebClientBuilder") WebClient.Builder serviceWebClientBuilder) {
        return new RoleWebClient(serviceWebClientBuilder, deleteMyRoleEndpoint);
    }
}
