package me.sonam.user.config;

import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import me.sonam.user.handler.UserSignupService;
import me.sonam.user.webclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Value("${account-rest-service.accountDelete}")
    private String deleteMyAccountEndpoint;

    @Value("${authentication-rest-service.accountDelete}")
    private String deleteMyAuthenticationEndpoint;

    @Value("${organization-rest-service.organizationDelete}")
    private String deleteMyOrganizationEndpoint;

    @Value("${role-rest-service.roleDelete}")
    private String deleteMyRoleEndpoint;

    @Value("${tokenMediator-rest-service.tokenDelete}")
    private String deleteMyTokenMediatorEndpoint;

    @LoadBalanced
    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning load balanced webclient part");
        return WebClient.builder();
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
        return new UserSignupService(webClientBuilder(), accountWebClient(),
                authenticationWebClient(), organizationWebClient(),
                roleWebClient(), tokenMediatorWebClient());
    }

    @Bean
    public AccountWebClient accountWebClient() {
        return new AccountWebClient(webClientBuilder(), deleteMyAccountEndpoint);
    }

    @Bean
    public AuthenticationWebClient authenticationWebClient() {
        return new AuthenticationWebClient(webClientBuilder(), deleteMyAuthenticationEndpoint);
    }

    @Bean
    public OrganizationWebClient organizationWebClient() {
        return new OrganizationWebClient(webClientBuilder(), deleteMyOrganizationEndpoint);
    }

    @Bean
    public RoleWebClient roleWebClient() {
        return new RoleWebClient(webClientBuilder(), deleteMyRoleEndpoint);
    }

    @Bean
    public TokenMediatorWebClient tokenMediatorWebClient() {
        return new TokenMediatorWebClient(webClientBuilder(), deleteMyTokenMediatorEndpoint);
    }


}