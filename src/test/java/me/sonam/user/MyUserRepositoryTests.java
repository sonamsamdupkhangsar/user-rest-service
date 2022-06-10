package me.sonam.user;


import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataR2dbcTest
public class MyUserRepositoryTests {
    private static final Logger LOG = LoggerFactory.getLogger(MyUserRestServiceTest.class);

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void saveAuthenticate() {
        MyUser myUser = new MyUser("Dommy", "cat", "dommy@cat.email");

        Mono<MyUser> userMono = userRepository.save(myUser);

        userMono.as(StepVerifier::create)
            .assertNext(actual -> {
                assertThat(actual.getFirstName()).isEqualTo("Dommy");
                assertThat(actual.getEmail()).isEqualTo("dommy@cat.email");
                LOG.info("save and checked mono from saved instance");
            })
            .verifyComplete();

        LOG.info("assert findByAuthenticationId and password works");
        userMono = userRepository.findByEmail("dommy@cat.email");

        userMono.as(StepVerifier::create)
                .assertNext(actual -> {
                    assertThat(actual.getFirstName()).isEqualTo("Dommy");
                    assertThat(actual.getEmail()).isEqualTo("dommy@cat.email");
                    LOG.info("asserted findByEmail api");
                })
                .verifyComplete();
    }
}
