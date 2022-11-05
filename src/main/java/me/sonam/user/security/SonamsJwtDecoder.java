package me.sonam.user.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

public class SonamsJwtDecoder implements ReactiveJwtDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(SonamsJwtDecoder.class);

    @Value("${jwt-rest-service}")
    private String jwtRestService;

    private WebClient webClient;

    @Override
    public Mono<Jwt> decode(String token) throws JwtException {
        return webClient.get().uri(jwtRestService)
                .headers(httpHeaders -> httpHeaders.setBearerAuth(token))
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(clientResponse ->
                        clientResponse.bodyToMono(Map.class)
                                .map(map -> {
                                    return new Jwt("token", null, null,
                                            Map.of("alg", "none"), Map.of("sub", map.get("subject")));

                                    /*LOG.info("returning UsernamePasswordAuthenticationToken");
                                    return new UsernamePasswordAuthenticationToken(
                                            map.get("authId"),
                                            null,
                                            null
                                    );*/
                                })
                );
    }
}
