package me.sonam.user;

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
        LOG.debug("liveness check");
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(new ReadinessStatus("alive"));
    }

    public Mono<ServerResponse> readiness(ServerRequest serverRequest) {
        LOG.debug("readiness check");

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(new ReadinessStatus("ready"));
    }

    class ReadinessStatus {
        private String readyStatus;
        public ReadinessStatus(String readyStatus) {
            this.readyStatus = readyStatus;
        }
        public ReadinessStatus() {

        }
        public String getReadyStatus() {
            return readyStatus;
        }
    }

    class LivenessStatus {
        private String liveStatus;
        public LivenessStatus(String liveStatus) {
            this.liveStatus = liveStatus;
        }
        public LivenessStatus() {

        }
        public String getLiveStatus() {
            return liveStatus;
        }
    }

}
