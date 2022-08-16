package me.sonam.user.handler;


import me.sonam.user.email.Email;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    @Value("${account-rest-service}")
    private String accountEp;

    @Value("${authentication-rest-service}")
    private String authenticationEp;

    @Value("${jwt-rest-service}")
    private String jwtEp;

    @Value("${email-rest-service}")
    private String emailEp;

    @Value("${apiKey}")
    private String apiKey;

    @Value("${emailFrom}")
    private String emailFrom;

    @Value("${emailBody}")
    private String emailBody;

    @PostConstruct
    public void setWebClient() {
        webClient = WebClient.builder().build();
    }

    @Override
    public Mono<String> signupUser(Mono<UserTransfer> userMono) {
        LOG.info("signup user");

        return userMono.flatMap(userTransfer ->
                        userRepository.existsByAuthenticationIdAndActiveTrue(userTransfer.getAuthenticationId())
                                .filter(aBoolean -> !aBoolean)
                        .switchIfEmpty(Mono.error(new SignupException("User is already active with authenticationId")))
                        //delete any previous attempts that is not activated
                        .flatMap(aBoolean -> userRepository.deleteByAuthenticationIdAndActiveFalse(userTransfer.getAuthenticationId()))
                        .flatMap(integer -> Mono.just(new MyUser(userTransfer.getFirstName(), userTransfer.getLastName(),
                            userTransfer.getEmail(), userTransfer.getAuthenticationId())))
                        .flatMap(myUser -> userRepository.save(myUser))
                        .flatMap(myUser -> {
                            LOG.info("create Authentication record with webrequest on endpoint: {}", authenticationEp);
                            WebClient.ResponseSpec responseSpec = webClient.post().uri(authenticationEp).bodyValue(userTransfer).retrieve();

                            return responseSpec.bodyToMono(String.class).map(authenticationId -> {
                                LOG.info("got back authenticationId from service call: {}", authenticationId);
                                return authenticationId;
                            }).onErrorResume(throwable -> {
                                LOG.error("authentication rest call failed", throwable);
                                return Mono.error(new SignupException("Authentication api call failed with error: " + throwable.getMessage()));
                            });
                        })
                        .flatMap(s -> {
                            LOG.info("create Account record with webrequest on endpoint: {}", accountEp);

                            StringBuilder stringBuilder = new StringBuilder(accountEp).append("/")
                                    .append(userTransfer.getAuthenticationId())
                                    .append("/").append(userTransfer.getEmail());

                            WebClient.ResponseSpec spec = webClient.post().uri(stringBuilder.toString()).retrieve();

                            return spec.bodyToMono(String.class).map(string -> {
                                LOG.info("account has been created with response: {}", string);
                                return string;
                            }).onErrorResume(throwable -> {
                                LOG.error("account rest call failed", throwable);
                                LOG.info("rollback userRepository by deleting authenticationId");
                                return userRepository.deleteByAuthenticationId(userTransfer.getAuthenticationId()).then(
                                    Mono.error(new SignupException("Email activation failed: " + throwable.getMessage())));
                            });
                        }).thenReturn("user signup succcessful")
        );
    }

    @Override
    public Mono<String> updateUser(String authenticationId, Mono<UserTransfer> userMono) {
        LOG.info("update user fields");

        return userMono.flatMap(userTransfer ->
                     userRepository.findByAuthenticationId(authenticationId).flatMap(myUser ->
                            userRepository.existsByEmailAndIdNot(userTransfer.getEmail(), myUser.getId())
                                    .filter(aBoolean -> {
                                        LOG.info("boolean: {}", aBoolean);
                                        if (aBoolean == true) {
                                            return false;
                                        } else {
                                            return true;
                                        }
                                    })
                                    .switchIfEmpty(Mono.error(new SignupException("email: email already used")))
                                    .flatMap(aBoolean -> {
                                        LOG.info("update name and email");
                                        return userRepository.updateFirstNameAndLastNameAndEmailByAuthenticationId(
                                                userTransfer.getFirstName(), userTransfer.getLastName(), userTransfer.getEmail()
                                                , userTransfer.getAuthenticationId()
                                        );

                                    })
                                    .thenReturn("user firstname, lastname and email updated")));
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

    @Override
    public Mono<String> activateUser(String authenticationId) {
        LOG.info("activate user");

        return userRepository.updateUserActiveTrue(authenticationId)
                .thenReturn("activated: "+authenticationId);
    }


}
