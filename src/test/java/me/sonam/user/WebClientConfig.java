package me.sonam.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

/*
@Profile("test")
@Configuration
*/
public class WebClientConfig {
   /* private static final Logger LOG = LoggerFactory.getLogger(WebClientConfig.class);
    @Bean
    public WebClient.Builder webClientBuilder() {
        LOG.info("returning test load balanced webclient");
        return WebClient.builder();
    }*/
}
