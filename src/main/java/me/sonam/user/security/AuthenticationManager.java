package me.sonam.user.security;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.Authentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class AuthenticationManager implements ReactiveAuthenticationManager {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationManager.class);

    @Autowired
    private ReactiveJwtDecoder jwtDecoder;

    public AuthenticationManager() {
        LOG.info("instantiating authenticationManager");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Authentication> authenticate(Authentication authentication) {
        String authToken = authentication.getCredentials().toString();

        return jwtDecoder.decode(authToken).map(jwt -> {
            LOG.info("returning UsernamePasswordAuthenticationToken");
            return new UsernamePasswordAuthenticationToken(
                    jwt.getSubject(),
                    null,
                    null
            );
        });
    }


        /*return webClient.get().uri(jwtRestService)
                .headers(httpHeaders -> httpHeaders.setBearerAuth(authToken))
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(clientResponse ->
                    clientResponse.bodyToMono(Map.class)
                            .map(map -> {
                                LOG.info("returning UsernamePasswordAuthenticationToken");
                                        return new UsernamePasswordAuthenticationToken(
                                                map.get("authId"),
                                                null,
                                                null
                                        );
                            })
                );*/

}