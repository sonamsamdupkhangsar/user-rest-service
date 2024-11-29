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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@DataR2dbcTest
public class MyUserRepositoryTests {
    private static final Logger LOG = LoggerFactory.getLogger(MyUserRepositoryTests.class);

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void saveAuthenticate() {
        MyUser myUser = new MyUser("Dommy", "cat", "dommy@cat.email", "dommy@cat.email");

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

        userRepository.deleteAll().subscribe();
    }

    @Test
    public void updateUser() {
        LOG.info("create a user");
        MyUser myUser = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser).subscribe();

        MyUser myUser2 = new MyUser("apple", "applelastname", "apple@cat.email", "apple@cat.email");

        userRepository.save(myUser2).subscribe();

        LOG.info("update that user now");
        Mono<Integer> mono = userRepository.updateByAuthenticationId(
                "John", "InTibet", "dommy@cat.email", false,
                "videos/2024-11-22/2024-11-22T08:15:40.314460.jpeg",
                "videos/2024-11-22/thumbnail/2024-11-22T08:15:40.314460.jpeg",
                "dommy@cat.email");

        userRepository.findAll().subscribe(myUser1 -> LOG.info("found user: {}", myUser1));

        LOG.info("update that user now");
        userRepository.updateByAuthenticationId(
                "John", "InTibet", "dog@cat.email", false,
                        "videos/2024-11-22/2024-11-22T08:15:40.314460.jpeg",
                        "videos/2024-11-22/thumbnail/2024-11-22T08:15:40.314460.jpeg",
                        "dommy@cat.email")
               .log().subscribe();

        userRepository.findByAuthenticationId("dommy@cat.email")
                .subscribe(myUser1 -> LOG.info("found user: {}, test condiditon: {}", myUser1, myUser1.getEmail().equals("dog@cat.email")));
      /*  mono.as(StepVerifier::create)
                .thenConsumeWhile(myUser1 -> {
                    LOG.info("check John as firstName: {}", myUser1.getFirstName());
                    return myUser1.getFirstName().equals("John");})
                .thenConsumeWhile(myUser1 -> myUser1.getLastName().equals("inTibet"))
                .verifyComplete();*/

       /* LOG.info("findByAuthId");

        userRepository.findByAuthenticationId("dommy@cat.email").as(StepVerifier::create)
                .expectNextCount(1)
                .thenConsumeWhile(myUser1 -> {
                    LOG.info("check John as firstName: {}", myUser1.getFirstName());
                    return myUser1.getFirstName().equals("John");})
                .thenConsumeWhile(myUser1 -> myUser1.getLastName().equals("inTibet"))
                .expectComplete()
                .verify();

        userRepository.findByAuthenticationId("dommy@cat.email").as(StepVerifier::create)
                .consumeNextWith(myUser1 -> LOG.info("found user: {}", myUser1)).verifyComplete();

*/


      /*  mono2.as(StepVerifier::create)
               .assertNext(long -> assertThat(myUser1.getEmail()).isEqualTo("dog@cat.email"))
                .expectComplete();*/

      /*  userRepository.findByAuthenticationId("dommy@cat.email").as(StepVerifier::create)
                .consumeNextWith(myUser1 -> LOG.info("found user: {}", myUser1)).verifyComplete();

*/
        userRepository.deleteAll().subscribe();
    }

    @Test
    public void findByFirstNameAndLastNameMatching() {
        LOG.info("test find by firstName and lastName matching");
        MyUser myUser = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser).subscribe();

        myUser = new MyUser("Dommy", "thecatman", "dommythecatman@cat.email", "dommythecatman@cat.email");

        userRepository.save(myUser).subscribe();

        myUser = new MyUser("Dommy", "mac", "dommymacn@cat.email", "dommymacn@cat.email");

        userRepository.save(myUser).subscribe();

        LOG.info("find those two users that has matching begining first and lastName");
        Flux<MyUser> myUserFlux = userRepository.findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase("dommy", "thecat");

        myUserFlux.as(StepVerifier::create)
                .expectNextCount(2)
                .expectComplete()
                .verify();

        userRepository.deleteAll().subscribe();
    }

    @Test
    public void findByUserId() {
        LOG.info("find by user id");

        MyUser myUser = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");
        userRepository.save(myUser).subscribe();

        Mono<MyUser> myUserMono = userRepository.findById(myUser.getId());

        myUserMono.as(StepVerifier::create).expectNext(myUser).expectComplete().verify();
        LOG.info("verify done");
    }
}
