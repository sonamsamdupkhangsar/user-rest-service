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

    @LoadBalanced
    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning load balanced webclient part");
        return WebClient.builder().filter(reactiveRequestContextHolder().headerFilter());
    }
    @LoadBalanced
    @Bean("noFilter")
    public WebClient.Builder webClientBuilderNoFilter() {
        LOG.info("returning for noFilter load balanced webclient part");
        return WebClient.builder();
    }

    @Bean
    public ReactiveRequestContextHolder reactiveRequestContextHolder() {
        return new ReactiveRequestContextHolder(webClientBuilderNoFilter());
    }

    @Bean
    public UserSignupService userSignupService() {
        return new UserSignupService(accountWebClient(),
                authenticationWebClient(), organizationWebClient(),
                roleWebClient());
    }

    @Bean
    public AccountWebClient accountWebClient() {
        return new AccountWebClient(webClientBuilder(), deleteMyAccountEndpoint, userRepository);
    }

    @Bean
    public AuthenticationWebClient authenticationWebClient() {
        return new AuthenticationWebClient(webClientBuilder(), deleteMyAuthenticationEndpoint, userRepository);
    }

    @Bean
    public OrganizationWebClient organizationWebClient() {
        return new OrganizationWebClient(webClientBuilder(), deleteMyOrganizationEndpoint);
    }

    @Bean
    public RoleWebClient roleWebClient() {
        return new RoleWebClient(webClientBuilder(), deleteMyRoleEndpoint);
    }
}