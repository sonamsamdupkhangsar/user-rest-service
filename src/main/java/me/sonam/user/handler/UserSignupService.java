package me.sonam.user.handler;

import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.UUID;

/**
 * This will add a user entry and call authentication service to create
 * authentication entry in that remote service
 */
@Service
public class UserSignupService implements UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserSignupService.class);

    @Autowired
    private UserRepository userRepository;

    private WebClient webClient;

    @Value("${authentication-rest-service}")
    private String authenticationEp;

    @Value("${jwt-rest-service}")
    private String jwtEp;

    @Value("${apiKey}")
    private String apiKey;

    @PostConstruct
    public void setWebClient() {
        webClient = WebClient.builder().build();
    }

    @Override
    public Mono<String> signupUser(Mono<UserTransfer> userMono) {
        LOG.info("signup user, apiKey: {}", apiKey);

       return userMono
                .filter(userTransfer -> {
                    //first filter on apiKey
                    LOG.info(" userTransfer.apiKey: {}, apiKey: {}, match: {}", userTransfer.getApiKey(), apiKey, userTransfer.getApiKey().equals(apiKey));
                    return userTransfer.getApiKey().equals(apiKey);
                })
                .switchIfEmpty(Mono.error(new SignupException("apikey check fail")))
               .flatMap(userTransfer -> userRepository.findByEmail(userTransfer.getEmail()).switchIfEmpty(Mono.just(new MyUser())).zipWith(Mono.just(userTransfer)))
               .filter(objects -> {
                   LOG.info("objects.t1 {}, t2: {}", objects.getT1(), objects.getT2());
                   return objects.getT1().getId() == null;
               })
               .switchIfEmpty(Mono.error(new SignupException("user already exists with email")))
               .flatMap(objects -> {
                    LOG.info("save new user, t1: {}", objects.getT1());
                    MyUser myUser = new MyUser(objects.getT2().getFirstName(), objects.getT2().getLastName(),
                            objects.getT2().getEmail(), objects.getT2().getAuthenticationId());
                    return userRepository.save(myUser).zipWith(Mono.just(objects.getT2()));
                })
                .flatMap(objects -> {
                    MyUser myUser = objects.getT1();
                    UserTransfer userTransfer = objects.getT2();
                    LOG.info("create authentication with rest call to endpoint {}", authenticationEp);

                    WebClient.ResponseSpec responseSpec = webClient.post().uri(authenticationEp).bodyValue(userTransfer).retrieve();

                    return responseSpec.bodyToMono(String.class).map(authenticationId -> {
                        LOG.info("got back authenticationId from service call: {}", authenticationId);
                        return "user created with id: " + myUser.getEmail() + " with authId: " + authenticationId;
                    }).onErrorResume(throwable -> Mono.error(new SignupException("Authentication api call failed with error: " + throwable.getMessage())));
                });
    }

    @Override
    public Mono<String> updateUser(String authenticationId, Mono<UserTransfer> userMono) {
        LOG.info("update user fields");

        return userMono.flatMap(userTransfer -> {
            userRepository.updateFirstNameAndLastNameByAuthenticationId(userTransfer.getFirstName()
                    , userTransfer.getLastName(), authenticationId);
            return Mono.just("user firstName and lastname updated for authId: ");
        });
    }

    @Override
    public Mono<String> updateProfilePhoto(String authenticationId, Mono<String> profilePhotoUrlMono) {
        LOG.info("update profile photo url");
        return profilePhotoUrlMono.flatMap(profilePhotoUrl ->
                {
                    LOG.info("url: {}", profilePhotoUrl);
                    return userRepository.updateProfilePhoto(profilePhotoUrl, authenticationId).then(Mono.just("photo updated"));
                }
        );
    }

    @Override
    public Mono<MyUser> getUserByAuthenticationId(String authenticationId) {
        LOG.info("find user with id: {}", authenticationId);

        return userRepository.findByAuthenticationId(authenticationId);
    }

    @Override
    public Flux<MyUser> findMatchingName(String firstName, String lastName) {
        LOG.info("find user with firstName and lastName: '{}' '{}'", firstName, lastName);
        return userRepository.findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(firstName, lastName);
    }


}
