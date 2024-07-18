package me.sonam.user;

import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import me.sonam.user.handler.UserSignupService;
import me.sonam.user.webclient.AccountWebClient;
import me.sonam.user.webclient.AuthenticationWebClient;
import me.sonam.user.webclient.OrganizationWebClient;
import me.sonam.user.webclient.RoleWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("localdevtest")
@Configuration
public class WebClientDevConfig {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientDevConfig.class);

    @Value("${account-rest-service.context}")
    private String deleteMyAccountEndpoint;

    @Value("${authentication-rest-service.context}")
    private String deleteMyAuthenticationEndpoint;

    @Value("${organization-rest-service.context}")
    private String deleteMyOrganizationEndpoint;

    @Value("${role-rest-service.context}")
    private String deleteMyRoleEndpoint;

    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning non-loadbalanced webclient");
        return WebClient.builder();
    }

    @Bean
    public ReactiveRequestContextHolder reactiveRequestContextHolder() {
        return new ReactiveRequestContextHolder(webClientBuilder());
    }
/*    @Bean
    public UserSignupService userSignupService() {
        return new UserSignupService(webClientBuilder());
    }*/


    @Bean
    public UserSignupService userSignupService() {
        return new UserSignupService(webClientBuilder(), accountWebClient(),
                authenticationWebClient(), organizationWebClient(),
                roleWebClient());
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

}
