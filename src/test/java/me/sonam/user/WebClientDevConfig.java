package me.sonam.user;

import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import me.sonam.user.handler.UserSignupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("localdevtest")
@Configuration
public class WebClientDevConfig {
    private static final Logger LOG = LoggerFactory.getLogger(WebClientDevConfig.class);
    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning non-loadbalanced webclient");
        return WebClient.builder();
    }

    @Bean
    public ReactiveRequestContextHolder reactiveRequestContextHolder() {
        return new ReactiveRequestContextHolder(webClientBuilder());
    }
    @Bean
    public UserSignupService userSignupService() {
        return new UserSignupService(webClientBuilder());
    }
}
