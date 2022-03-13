package me.sonam.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * a basic handler for liveness and readiness endpoints
 */
@Controller
public class LivenessReadinessHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LivenessReadinessHandler.class);

    public Mono<ServerResponse> liveness(ServerRequest serverRequest) {
        LOG.info("liveness check");
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

    public Mono<ServerResponse> readiness(ServerRequest serverRequest) {
        LOG.info("readiness check");
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).build();
    }

}
