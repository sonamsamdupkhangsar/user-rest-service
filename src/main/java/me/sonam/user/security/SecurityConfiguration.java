package me.sonam.user.security;

import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // @formatter:off
        http
                .authorizeExchange()
               // .anyExchange().authenticated()
                .pathMatchers("/actuator", "/actuator/*")
                .permitAll()
                .and()
                .oauth2ResourceServer()
                .jwt();

        //Okta.configureResourceServer401ResponseBody(http);

        return http.build();
        // @formatter:on
    }
}