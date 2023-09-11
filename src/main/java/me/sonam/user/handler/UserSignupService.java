package me.sonam.user.handler;


import jakarta.annotation.PostConstruct;
import me.sonam.security.headerfilter.ReactiveRequestContextHolder;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * This will add a user entry and call authentication service to create
 * authentication entry in that remote service
 */
public class UserSignupService implements UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserSignupService.class);

    @Autowired
    private UserRepository userRepository;

    private WebClient.Builder webClientBuilder;

    @Value("${account-rest-service.root}${account-rest-service.accounts}")
    private String accountEp;

    @Value("${authentication-rest-service.root}${authentication-rest-service.authentications}")
    private String authenticationEp;

    @Autowired
    private ReactiveRequestContextHolder reactiveRequestContextHolder;

    public UserSignupService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @PostConstruct
    public void setWebClient() {
        webClientBuilder = webClientBuilder.filter(reactiveRequestContextHolder.headerFilter());
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
                                .flatMap(aBoolean -> userRepository.existsByEmailAndActiveTrue(userTransfer.getEmail()))
                                .filter(aBoolean -> !aBoolean)
                                .switchIfEmpty(Mono.error(new SignupException("User account is active for that email")))
                                .flatMap(aBoolean -> userRepository.existsByEmailAndUserAuthAccountCreatedTrue(userTransfer.getEmail()))
                                .filter(aBoolean -> {
                                    LOG.info("emailExistsAndUserAuthAccountCreated is {}", aBoolean);
                                    return !aBoolean;
                                }).switchIfEmpty(Mono.error(new SignupException("User account has already been created for that email, check to activate it by email")))
                                .flatMap(aBoolean -> callDeleteAccountCheck(userTransfer.getEmail()))
                                .flatMap(string -> userRepository.deleteByAuthenticationIdAndUserAuthAccountCreatedFalse(userTransfer.getAuthenticationId()))
                                //just delete rows with email and acccount created is in false - meaning not fully created
                                .flatMap(rows -> userRepository.deleteByEmailAndUserAuthAccountCreatedFalse(userTransfer.getEmail()))
                                .flatMap(integer -> Mono.just(new MyUser(userTransfer.getFirstName(), userTransfer.getLastName(),
                            userTransfer.getEmail(), userTransfer.getAuthenticationId())))
                        .flatMap(myUser -> userRepository.save(myUser))
                        .flatMap(myUser -> {
                            LOG.info("create Authentication record with webrequest on endpoint: {}", authenticationEp);

                            Map<String, String> payloadMap = new HashMap<>();
                            payloadMap.put("authenticationId", userTransfer.getAuthenticationId());
                            payloadMap.put("password", userTransfer.getPassword());
                            payloadMap.put("userId", myUser.getId().toString());
                            WebClient.ResponseSpec responseSpec = webClientBuilder.build().post().uri(authenticationEp).bodyValue(payloadMap).retrieve();

                            return responseSpec.bodyToMono(Map.class).map(map -> {
                                LOG.info("got back authenticationId from service call: {}", map.get("message"));
                                return map.get("message");
                            }).onErrorResume(throwable -> {
                                LOG.error("authentication rest call failed: {}", throwable.getMessage());

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

                            WebClient.ResponseSpec spec = webClientBuilder.build().post().uri(stringBuilder.toString()).retrieve();

                            return spec.bodyToMono(Map.class).map(map -> {
                                LOG.info("account has been created with response: {}", map.get("message"));

                                return userRepository.updatedUserAuthAccountCreatedTrue(
                                        userTransfer.getAuthenticationId())
                                       .subscribe(integer -> LOG.info("update UserAuthAccountCreatedTrue"));

                            }).onErrorResume(throwable -> {
                                LOG.error("account rest call failed: {}", throwable.getMessage());
                                if (throwable instanceof WebClientResponseException) {
                                    WebClientResponseException webClientResponseException = (WebClientResponseException) throwable;
                                    LOG.error("error body contains: {}", webClientResponseException.getResponseBodyAsString());

                                    return userRepository.deleteByAuthenticationId(userTransfer.getAuthenticationId())
                                                    .then(
                                            Mono.error(new SignupException("Account api call failed with error: " +
                                                    webClientResponseException.getResponseBodyAsString())));
                                }
                                else {
                                    return Mono.error(new SignupException("Account api call failed with error: " +throwable.getMessage()));
                                }

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

   /* @Override
    public Mono<MyUser> getUserByAuthenticationId(String authenticationId) {
        LOG.info("find user with id: {}", authenticationId);

        return userRepository.findByAuthenticationId(authenticationId);
    }*/

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

    @Override
    public Mono<Map<String, Object>> getUserByAuthenticationId(String authenticationId) {
        LOG.info("get user information for authenticationId: {}", authenticationId);

        return userRepository.findByAuthenticationId(authenticationId).map(myUser -> {
            Map<String, Object> map = new HashMap<>();
            map.put("firstName", myUser.getFirstName());
            map.put("lastName", myUser.getLastName());
            map.put("email", myUser.getEmail());
            map.put("profilePhoto", myUser.getProfilePhoto());
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
            if (myUser.getBirthDate() != null) {
                map.put("dateOfBirth", dateFormat.format(myUser.getBirthDate()));
            }
            return map;
        });
    }


    private Mono<? extends String> callDeleteAccountCheck(String email) {
        LOG.info("call delete account check");
        final StringBuilder stringBuilder = new StringBuilder(accountEp).append("/email/").append(email);
        LOG.info("accountEp: {}", stringBuilder.toString());

        WebClient.ResponseSpec responseSpec = webClientBuilder.build().delete().uri(stringBuilder.toString()).retrieve();

        return responseSpec.bodyToMono(Map.class).map(map -> {
            LOG.info("got back response from account deletion service call: {}", map.get("message"));
            return map.get("message");
        }).onErrorResume(throwable -> {
            LOG.error("account deletion rest call failed: {}", throwable.getMessage());
            //return Mono.just(true);
            //return Mono.just("account deletion not needed");
            return Mono.just(true);
        }).then(Mono.just("done calling account delete check"));//.then(Mono.error(new SignupException("a user with this email already exists")));

    }
}
