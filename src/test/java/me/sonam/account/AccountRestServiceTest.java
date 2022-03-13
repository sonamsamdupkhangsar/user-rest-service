package me.sonam.account;

import me.sonam.account.repo.AccountRepository;
import me.sonam.account.repo.entity.Account;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AccountRestServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(AccountRestServiceTest.class);

    @Autowired
    private WebTestClient client;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void isAccountActive() {
        final String uuid = UUID.randomUUID().toString();
        LOG.info("check for uuid: {}", uuid);
        client.get().uri("/active/"+uuid)
                .exchange().expectStatus().isOk();

    }


    @Test
    public void activateAccount() throws InterruptedException {
        UUID id = UUID.randomUUID();
        LOG.info("activate account for userId: {}", id);
        client.post().uri("/activate/" + id.toString())
                .exchange().expectStatus().isOk();
    }

    @Test
    public void testAccountDuplicatesWhenNewFlagTrue() {
        UUID userId = UUID.randomUUID();
        LOG.info("testing count of unique rows when saving account multiple times");

        Account account = new Account(userId, true, LocalDateTime.now());
        Mono<Account> accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag once with userId: {}", userId);
        accountMono.subscribe(account1 -> LOG.info("account: {}", account1));

        accountRepository.countByUserId(userId).as(StepVerifier::create)
                .assertNext(count ->  {LOG.info("count now is: {}", count); assertThat(count).isEqualTo(1);})
                .verifyComplete();

        account = new Account(userId, true, LocalDateTime.now());
        accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag twice with userId: {}", userId);
        accountMono.subscribe(account1 -> LOG.info("account: {}", account1));

        accountRepository.countByUserId(userId).as(StepVerifier::create)
                .assertNext(count -> { LOG.info("count now is: {}", count); assertThat(count).isEqualTo(2);})
            .verifyComplete();
    }

    @Test
    public void testAccountNewFlagTrueForUpdate() {
        UUID userId = UUID.randomUUID();
        LOG.info("testing count of unique rows when saving account multiple times");

        Account account = new Account(userId, true, LocalDateTime.now());
        Mono<Account> accountMono = accountRepository.save(account);
        LOG.info("saved account with newFlag once with userId: {}", userId);
        accountMono.subscribe(account1 -> LOG.info("account1: {}", account1));

       accountRepository.countByUserId(userId).as(StepVerifier::create)
                .assertNext(count ->  {LOG.info("count now is: {}", count); assertThat(count).isEqualTo(1);})
                .verifyComplete();

        account.setNewAccount(false);
        accountMono = accountRepository.save(account);
        accountMono.subscribe(account1 -> LOG.info("saved same account: {}", account1));

        accountRepository.countByUserId(account.getUserId()).as(StepVerifier::create)
                .assertNext(count -> {
                    LOG.info("newAccount=false, should only be 1 row for userId: {}, count: {}", account.getUserId(), count);
                    assertThat(count).isEqualTo(1);
                }).verifyComplete();
    }

}
