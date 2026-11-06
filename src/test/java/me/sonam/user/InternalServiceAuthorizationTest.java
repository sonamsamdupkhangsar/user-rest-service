package me.sonam.user;

import me.sonam.user.security.InternalServiceAuthorization;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class InternalServiceAuthorizationTest {
    private final InternalServiceAuthorization internalServiceAuthorization = new InternalServiceAuthorization(
            Base64.getEncoder()
                    .encodeToString("internal-client:test-secret".getBytes(StandardCharsets.UTF_8)), "https://platform.example/issuer");

    @Test
    void onlyConfiguredServiceTokenCanDelegate() {
        var service = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("internal-client")
                .issuer("https://platform.example/issuer")
                .build();
        internalServiceAuthorization.requireService()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(service)))
                .block();
        var user = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("internal-client")
                .issuer("https://platform.example/issuer")
                .claim("userId", "user-id")
                .build();
        assertThatThrownBy(() -> internalServiceAuthorization.requireService()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(user)))
                .block())
                .hasMessageContaining("Internal service access");
    }

    @Test
    void missingConfigurationOrWrongClientFailsClosed() {
        var other = new JwtAuthenticationToken(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("other-client")
                .build());
        assertThatThrownBy(() -> internalServiceAuthorization.requireService()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(other))
                .block())
                .hasMessageContaining("Internal service access");
        assertThatThrownBy(() -> new InternalServiceAuthorization("", "https://platform.example/issuer")
                .requireService()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(other))
                .block())
                .hasMessageContaining("Internal service access");
    }

    @Test
    void anotherTenantCannotImpersonateTheInternalClientId() {
        var token = new JwtAuthenticationToken(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("internal-client")
                .issuer("https://free.example/issuer")
                .build());
        assertThatThrownBy(() -> internalServiceAuthorization.requireService()
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(token))
                .block())
                .hasMessageContaining("Internal service access");
    }
}
