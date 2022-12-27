package me.sonam.user.security;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

//@EnableWebFluxSecurity
//@EnableReactiveMethodSecurity
public class SecurityConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private SecurityContextRepository securityContextRepository;

    @Value("${permitPaths}")
    private String[] permitPaths;

    @Bean
    public SecurityWebFilterChain securitygWebFilterChain(ServerHttpSecurity http) {
        LOG.info("permitPaths.length: {}, permitPaths: {}", permitPaths.length, permitPaths);
        return http
                .exceptionHandling()
                .authenticationEntryPoint((swe, e) ->
                        Mono.fromRunnable(() -> swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED))
                ).accessDeniedHandler((swe, e) ->
                        Mono.fromRunnable(() -> swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN))
                ).and()
                .csrf().disable()
                .formLogin().disable()
                .httpBasic().disable()
                .authenticationManager(authenticationManager)
                .securityContextRepository(securityContextRepository)
                .authorizeExchange()
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                //.pathMatchers("/login").permitAll()
                .pathMatchers(permitPaths).permitAll()
                //.pathMatchers("/login", "/actuator/health").permitAll()
                .anyExchange().authenticated()
                .and().build();
    }

    /*@Bean
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
    }*/
}