package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.entity.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handler
 */
@Component
public class AccountHandler implements AccountBehaviors {
    private static final Logger LOG = LoggerFactory.getLogger(AccountHandler.class);

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public Mono<ServerResponse> isAccountActive(ServerRequest serverRequest) {
        LOG.info("checking account active status for userId");

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).
                body(accountRepository.existsByUserIdAndActiveTrue(
                        UUID.fromString(serverRequest.pathVariable("userId"))), Boolean.class)
                .onErrorResume(e -> ServerResponse.badRequest().body(BodyInserters
        .fromValue(e.getMessage())));
    }

    @Override
    public Mono<ServerResponse> activateAccount(ServerRequest serverRequest) {
        UUID userId = UUID.fromString(serverRequest.pathVariable("userId"));
        LOG.info("activate account for userId: {}", userId);

        return accountRepository.findByUserId(userId)
                .switchIfEmpty(Mono.just(new Account(userId, true, LocalDateTime.now())))
                .doOnNext(account -> {
                    if (!account.getActive()){
                        account.setActive(true);
                        account.setAccessDateTime(LocalDateTime.now());
                        account.setNewAccount(false);
                        LOG.info("set account to active if not");
                    }
                    else {
                        LOG.info("account has been set active");
                    }
                })
                .doOnNext(account -> {
                    LOG.info("saving account: {}", account.toString());
                    accountRepository.save(account);
                })
                .flatMap(account -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(account))
             .onErrorResume(throwable -> Mono.just("Error: " + throwable.getMessage())
             .flatMap(s -> ServerResponse.badRequest().contentType(MediaType.APPLICATION_JSON)
             .bodyValue(s)));
    }


}
