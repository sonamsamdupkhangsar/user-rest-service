package me.sonam.user.handler;


import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

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

    @Value("${emailFrom}")
    private String emailFrom;

    @Value("${emailBody}")
    private String emailBody;

    @PostConstruct
    public void setWebClient() {
        webClient = WebClient.builder().build();
    }

    /**
     * First, check if user already exists with authenticaitonId and is active
     * Second,if user is not active with authenticationId then check user, authentication and account was created successfully in a prior call
     * and throw exception if user/authentication/account was already created before
     * Third, verify there is no user with that email already.
     * Then create the user object and save it.
     * Make a rest call to Authentication to save a Authentication data
     * Then make another rest call to Account to save a Account data. On success response set the user UserAuthAccountCreated to true
     * @param userMono
     * @return
     */
    @Override
    public Mono<String> signupUser(Mono<UserTransfer> userMono) {
        LOG.info("signup user");

        return userMono.flatMap(userTransfer ->
                        userRepository.existsByAuthenticationIdAndActiveTrue(userTransfer.getAuthenticationId())
                                .filter(aBoolean -> !aBoolean)
                        .switchIfEmpty(Mono.error(new SignupException("User is already active with authenticationId")))
                        .flatMap(aBoolean -> userRepository.existsByAuthenticationIdAndUserAuthAccountCreatedTrue(userTransfer.getAuthenticationId()))
                        .filter(aBoolean -> {
                            LOG.info("aBoolean for findByAuthenticationIdAndUserAuthAccountCreatedTrue is {}", aBoolean);

                            return !aBoolean;
                        }).switchIfEmpty(Mono.error(new SignupException("User account has already been created for that id, check to activate it by email")))
                        .flatMap(aBoolean -> userRepository.existsByEmail(userTransfer.getEmail()))
                        .filter(aBoolean -> !aBoolean)
                        //.switchIfEmpty(Mono.error(new SignupException("a user with this email already exists")))
                          .switchIfEmpty(callDeleteAccountCheck(userTransfer.getEmail()))
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

                                LOG.info("rollback userRepository by deleting authenticationId");
                                return userRepository.deleteByAuthenticationId(userTransfer.getAuthenticationId()).then(
                                    Mono.error(new SignupException("Authentication api call failed with error: " + throwable.getMessage())));
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

                               return userRepository.updatedUserAuthAccountCreatedTrue(
                                        userTransfer.getAuthenticationId())
                                       .subscribe(integer -> LOG.info("update UserAuthAccountCreatedTrue"));

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
        LOG.info("update user fields for authenticationId: {}", authenticationId);

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
                                        LOG.info("update name and email for authId: {}", authenticationId);
                                        return userRepository.updateFirstNameAndLastNameAndEmailByAuthenticationId(
                                                userTransfer.getFirstName(), userTransfer.getLastName(), userTransfer.getEmail()
                                                , authenticationId
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

    @Override
    public Mono<String> deleteUser(String authenticationId) {
        LOG.info("delete user if it's active status is false");

        return userRepository.findByAuthenticationId(authenticationId)
                .filter(myUser -> !myUser.getActive())
                .switchIfEmpty(Mono.error(new UserException("user is active, cannot delete")))
                .flatMap(myUser ->   userRepository.deleteByAuthenticationIdAndActiveFalse(authenticationId))
                .thenReturn("deleted: " + authenticationId);
    }


    private Mono<? extends Boolean> callDeleteAccountCheck(String email) {
        final StringBuilder stringBuilder = new StringBuilder(accountEp).append("/email/").append(email);

        WebClient.ResponseSpec responseSpec = webClient.delete().uri(stringBuilder.toString()).retrieve();

        return responseSpec.bodyToMono(String.class).map(stringResponse -> {
            LOG.info("got back response from account deletion service call: {}", stringResponse);
            return stringResponse;
        }).onErrorResume(throwable -> {
            LOG.error("account deletion rest call failed", throwable);
            return null;
        }).then(Mono.error(new SignupException("a user with this email already exists")));

    }
}
