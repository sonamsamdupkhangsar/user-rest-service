package me.sonam.user.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Explicit delegation is restricted to the configured internal client, never an end-user token. */
@Component
public class InternalServiceAuthorization {
    private static final Logger LOG = LoggerFactory.getLogger(InternalServiceAuthorization.class);

    private final String clientId;
    private final String issuer;

    public InternalServiceAuthorization(@Value("${BASE64_CLIENT_ID_SECRET:}") String credentials,
            @Value("${INTERNAL_SERVICE_ISSUER:${ISSUER_URI:${ISSUER_ADDRESS:}}}") String issuer) {
        this.issuer = issuer == null ? "" : issuer.replaceAll("/+$", "");
        String parsed = null;

        try {
            String decoded = new String(
                    Base64.getDecoder().decode(credentials),
                    StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon > 0) {
                parsed = decoded.substring(0, colon);
            }
        } catch (IllegalArgumentException exception) {
            LOG.error("Unable to decode BASE64_CLIENT_ID_SECRET; internal service delegation will be denied", exception);
            // Missing or malformed configuration must deny delegation.
        }

        clientId = parsed;
    }

    public Mono<Void> requireService() {
        return ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                .flatMap(context -> {
                    if (context.getAuthentication() == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
                    }


                    if (context.getAuthentication().getPrincipal() instanceof Jwt jwt
                            && clientId != null && clientId.equals(jwt.getSubject())
                            && !issuer.isBlank() && jwt.getIssuer() != null
                            && issuer.equals(jwt.getIssuer().toString().replaceAll("/+$", ""))
                            && jwt.getClaim("userId") == null) {
                        return Mono.<Void>empty();
                    }

                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal service access required"));
                });
    }
}
