package me.sonam.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

/**
 * Setup the route for liveness and readiness endpoints
 */
@Configuration
public class LivenessReadinessRouter {
    private static final Logger LOG = LoggerFactory.getLogger(LivenessReadinessRouter.class);

    @Bean("livenessRouter")
    public RouterFunction<ServerResponse> route(LivenessReadinessHandler livenessReadinessHandler) {
        LOG.info("building email router function");
        return RouterFunctions.route(GET("/api/health/liveness").and(accept(MediaType.APPLICATION_JSON)),
                livenessReadinessHandler::liveness)
                .andRoute(GET("/api/health/readiness").and(accept(MediaType.APPLICATION_JSON)),
                        livenessReadinessHandler::readiness);
    }
}
