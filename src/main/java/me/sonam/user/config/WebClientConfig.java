package me.sonam.user.config;

import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import me.sonam.user.handler.UserSignupService;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.webclient.AccountWebClient;
import me.sonam.user.webclient.AuthenticationWebClient;
import me.sonam.user.webclient.OrganizationWebClient;
import me.sonam.user.webclient.RoleWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("!localdevtest")
@Configuration
public class WebClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientConfig.class);
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

    // Used for normal downstream service calls through Spring load balancing.
    @LoadBalanced
    @Bean("serviceWebClientBuilder")
    public WebClient.Builder serviceWebClientBuilder(ReactiveRequestContextHolder reactiveRequestContextHolder) {
        LOG.info("returning load balanced service webclient builder");
        return WebClient.builder().filter(reactiveRequestContextHolder.headerFilter());
    }

    // Used only by ReactiveRequestContextHolder when requesting access tokens from authorization server.
    @LoadBalanced
    @Bean("tokenWebClientBuilder")
    @Profile("!local-https")
    public WebClient.Builder loadBalancedTokenWebClientBuilder() {
        LOG.info("returning load balanced token webclient builder");
        return WebClient.builder();
    }

    @Bean("tokenWebClientBuilder")
    @Profile("local-https")
    public WebClient.Builder localHttpsTokenWebClientBuilder() {
        LOG.info("returning non-load-balanced token webclient builder for local-https profile");
        return WebClient.builder();
    }

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
