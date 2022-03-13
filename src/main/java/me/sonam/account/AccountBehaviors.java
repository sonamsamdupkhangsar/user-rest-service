package me.sonam.account;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

public interface AccountBehaviors {
    // returns boolean if account is active
    Mono<ServerResponse> isAccountActive(ServerRequest serverRequest);
    /**
     * if account with userId exists then it will activate account
     * else it will create account and activate it
     * @param serverRequest
     * @return
     */
    Mono<ServerResponse> activateAccount(ServerRequest serverRequest);
}
