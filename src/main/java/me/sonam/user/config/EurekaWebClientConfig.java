package me.sonam.user.config;

import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("eureka")
@Configuration
public class EurekaWebClientConfig {
    private static final Logger LOG = LoggerFactory.getLogger(EurekaWebClientConfig.class);

    @LoadBalanced
    @Bean("serviceWebClientBuilder")
    public WebClient.Builder serviceWebClientBuilder(ReactiveRequestContextHolder reactiveRequestContextHolder) {
        LOG.info("creating load-balanced service WebClient for Eureka service discovery");
        return WebClient.builder().filter(reactiveRequestContextHolder.headerFilter());
    }

    @LoadBalanced
    @Bean("tokenWebClientBuilder")
    @Profile("!local-https")
    public WebClient.Builder tokenWebClientBuilder() {
        LOG.info("creating load-balanced token WebClient for Eureka service discovery");
        return WebClient.builder();
    }

    @Bean("tokenWebClientBuilder")
    @Profile("local-https")
    public WebClient.Builder directTokenWebClientBuilder() {
        LOG.info("creating direct token WebClient for local HTTPS");
        return WebClient.builder();
    }
}
