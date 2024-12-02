package me.sonam.user.handler;


import jakarta.annotation.PostConstruct;
import me.sonam.user.handler.carrier.User;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import me.sonam.user.webclient.AccountWebClient;
import me.sonam.user.webclient.AuthenticationWebClient;
import me.sonam.user.webclient.OrganizationWebClient;
import me.sonam.user.webclient.RoleWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This will add a user entry and call authentication service to create
 * authentication entry in that remote service
 */
public class UserSignupService implements UserService {
    private static final Logger LOG = LoggerFactory.getLogger(UserSignupService.class);

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private UserRepository userRepository;

 //   private WebClient.Builder webClientBuilder;

    @Value("${account-rest-service.context}")

    private String accountEp;

    @Value("${authentication-rest-service.context}")
    private String authenticationEp;

   // @Autowired
   // private ReactiveRequestContextHolder reactiveRequestContextHolder;
    private final AccountWebClient accountWebClient;
    private final AuthenticationWebClient authenticationWebClient;
    private final OrganizationWebClient organizationWebClient;
    private final RoleWebClient roleWebClient;

    public UserSignupService(AccountWebClient accountWebClient,
                             AuthenticationWebClient authenticationWebClient, OrganizationWebClient organizationWebClient,
                             RoleWebClient roleWebClient) {
        this.accountWebClient = accountWebClient;
        this.authenticationWebClient = authenticationWebClient;
        this.organizationWebClient = organizationWebClient;
        this.roleWebClient = roleWebClient;
    }

    @PostConstruct
    public void setWebClient() {
        //webClientBuilder = webClientBuilder.filter(reactiveRequestContextHolder.headerFilter());
        List<String> serviceList = discoveryClient.getServices();
        LOG.info("printing services of size: {}", serviceList.size());

        serviceList.forEach(s -> LOG.info("Found service: {}", s));
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

        return userMono.flatMap(userTransfer -> {
            LOG.info("checking if userTransfer fields are empty: {}", userTransfer);
           if (userTransfer.getFirstName().trim().isEmpty()) {
               LOG.error("first name is emtpy");
               return Mono.error(new UserException("first name cannot be empty"));
           }
           if (userTransfer.getLastName().trim().isEmpty()) {
               LOG.error("last name is emtpy");
               return Mono.error(new UserException("last name cannot be empty"));
           }
           if (userTransfer.getEmail().trim().isEmpty()) {
               LOG.error("email is emtpy");
               return Mono.error(new UserException("email cannot be empty"));
           }
           if (userTransfer.getAuthenticationId().trim().isEmpty()) {
               LOG.error("authenticationId is emtpy");
               return Mono.error(new UserException("username cannot be empty"));
           }
           if (userTransfer.getPassword().trim().isEmpty()) {
               LOG.error("password is emtpy");
               return Mono.error(new UserException("password needs to be entered"));
           }
           return Mono.just(userTransfer);
        }).//thenReturn("User sign up success, checkemail");  //
                flatMap(userTransfer ->
                        userRepository.existsByAuthenticationIdAndActiveTrue(userTransfer.getAuthenticationId())
                                .filter(aBoolean -> !aBoolean)
                        .switchIfEmpty(Mono.error(new SignupException("User is already active with that username (authenticationId)")))
                        .flatMap(aBoolean -> userRepository.existsByAuthenticationIdAndUserAuthAccountCreatedTrue(userTransfer.getAuthenticationId()))
                        .filter(aBoolean -> {
                            LOG.info("aBoolean for findByAuthenticationIdAndUserAuthAccountCreatedTrue is {}", aBoolean);

                            return !aBoolean;
                        }).switchIfEmpty(Mono.error(new SignupException("User account has already been created for that username, check to activate it by email")))
                                .flatMap(aBoolean -> userRepository.existsByEmailAndActiveTrue(userTransfer.getEmail()))
                                .filter(aBoolean -> !aBoolean)
                                .switchIfEmpty(Mono.error(new SignupException("User account is active for that email")))
                                .flatMap(aBoolean -> userRepository.existsByEmailAndUserAuthAccountCreatedTrue(userTransfer.getEmail()))
                                .filter(aBoolean -> {
                                    LOG.info("emailExistsAndUserAuthAccountCreated is {}", aBoolean);
                                    return !aBoolean;
                                }).switchIfEmpty(Mono.error(new SignupException("User account has already been created for that email, check to activate it by email")))
                                .flatMap(aBoolean -> accountWebClient.deleteAccountByEmail(userTransfer.getEmail()))
                                .flatMap(s -> authenticationWebClient.deleteByAuthenticationId(userTransfer.getAuthenticationId()))
                                .flatMap(string -> userRepository.deleteByAuthenticationIdAndUserAuthAccountCreatedFalse(userTransfer.getAuthenticationId()))
                                //just delete rows with email and account created is in false - meaning not fully created
                                .flatMap(rows -> userRepository.deleteByEmailAndUserAuthAccountCreatedFalse(userTransfer.getEmail()))
                                .flatMap(integer -> Mono.just(new MyUser(userTransfer.getFirstName(), userTransfer.getLastName(),
                            userTransfer.getEmail(), userTransfer.getAuthenticationId())))
                        .flatMap(myUser -> userRepository.save(myUser))
                        .flatMap(myUser ->
                            authenticationWebClient.create(userTransfer.getAuthenticationId(), userTransfer.getPassword(), myUser.getId())
                        .then(accountWebClient.createAccount(userTransfer.getAuthenticationId(), myUser.getId(), userTransfer.getEmail())))
                        .thenReturn("user signup succcessful")
        );
    }

    @Override
    public Mono<String> updateUser(String authenticationId, Mono<UserTransfer> userMono) {
        LOG.info("update user fields for authenticationId: {}", authenticationId);

       return userMono.flatMap(userTransfer -> {
            LOG.info("userTransfer: {}", userTransfer);
                     return userRepository.findByAuthenticationId(userTransfer.getAuthenticationId())
                             .flatMap(myUser ->
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

                                        return userRepository.updateByAuthenticationId(userTransfer.getFirstName(), userTransfer.getLastName(),
                                                userTransfer.getEmail(),  userTransfer.isSearchable(),
                                                 userTransfer.getProfilePhoto(), userTransfer.getAuthenticationId());


                                    })
                                    .thenReturn("user firstname, lastname and email updated"));
    });
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
    public Mono<String> deleteMyAccount() {
        LOG.info("delete my account");

        return ReactiveSecurityContextHolder.getContext().flatMap(securityContext -> {
            LOG.info("principal: {}", securityContext.getAuthentication().getPrincipal());
            org.springframework.security.core.Authentication authentication = securityContext.getAuthentication();

            LOG.info("authentication: {}", authentication);
            LOG.info("authentication.principal: {}", authentication.getPrincipal());
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String userIdString = jwt.getClaim("userId");
            LOG.info("delete user data for userId: {}", userIdString);

            UUID userId = UUID.fromString(userIdString);

            return userRepository.findById(userId)
                    .switchIfEmpty(Mono.error(new UserException("no user found with userId: " + userId)))
                    .flatMap(myUser -> {
                        LOG.info("delete user from repository {}", myUser.getId());
                        return userRepository.deleteById(userId).thenReturn(Mono.just("user deleted by id"));
                    })

                    .flatMap(unused -> {
                        //LOG.info("delete account {}", unused);
                        return accountWebClient.deleteMyAccount();
                    })
                    .then(authenticationWebClient.deleteMyAccount())
                    .then(organizationWebClient.deleteMyAccount())
                    .then(roleWebClient.deleteMyAccount())
                    .thenReturn("delete my account success for user id: " + userId);
        });
    }

    @Override
    public Mono<Map<String, Object>> getUserByAuthenticationId(String authenticationId) {
        LOG.info("get user information for authenticationId: {}", authenticationId);

        return userRepository.findByAuthenticationId(authenticationId)
                .switchIfEmpty(Mono.error(new SignupException("user not found with authenticationId: "+
                        authenticationId)))
                .switchIfEmpty(Mono.error(new UserException("user searchable is turned off")))
                .map(myUser -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", myUser.getId().toString());
                    map.put("firstName", myUser.getFirstName());
                    map.put("lastName", myUser.getLastName());
                    map.put("email", myUser.getEmail());
                    map.put("profilePhoto", myUser.getProfilePhoto());
                    map.put("authenticationId", myUser.getAuthenticationId());
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                    if (myUser.getBirthDate() != null) {
                        map.put("dateOfBirth", dateFormat.format(myUser.getBirthDate()));
                    }
                    return map;
                });
    }

    @Override
    public Mono<Map<String, Object>> getUserByAuthenticationIdForProfileSearch(String authenticationId) {
        LOG.info("profile search user information for authenticationId: {}", authenticationId);

        return userRepository.findByAuthenticationId(authenticationId)
                .switchIfEmpty(Mono.error(new SignupException("user not found with authenticationId: "+
                        authenticationId)))
                .filter(myUser -> myUser.getSearchable() != null && myUser.getSearchable())
                .switchIfEmpty(Mono.error(new UserException("user searchable is turned off")))
                .map(myUser -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", myUser.getId().toString());
                    map.put("firstName", myUser.getFirstName());
                    map.put("lastName", myUser.getLastName());
                    map.put("email", myUser.getEmail());
                    map.put("profilePhoto", myUser.getProfilePhoto());
                    map.put("authenticationId", myUser.getAuthenticationId());
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                    if (myUser.getBirthDate() != null) {
                        map.put("dateOfBirth", dateFormat.format(myUser.getBirthDate()));
                    }
                    return map;
                });
    }

    @Override
    public Mono<Map<String, Object>> getUserForOidcUserInfo(UUID userId) {
        LOG.info("get user information for userId: {}", userId);

        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new SignupException("user not found with userId: "+
                        userId))).map(myUser -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", myUser.getId().toString());
                    map.put("firstName", myUser.getFirstName());
                    map.put("lastName", myUser.getLastName());
                    map.put("email", myUser.getEmail());
                    map.put("profilePhoto", myUser.getProfilePhoto());
                    map.put("authenticationId", myUser.getAuthenticationId());
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                    if (myUser.getBirthDate() != null) {
                        map.put("dateOfBirth", dateFormat.format(myUser.getBirthDate()));
                    }
                    return map;
                });
    }


    @Override
    public Mono<User> getUserById(UUID id) {
        LOG.info("get user by id: {}", id);

        return userRepository.findById(id).switchIfEmpty(Mono.error(new UserException("no user with id: "+ id)))
                .map(myUser -> {
                    LOG.info("found myUser: {}", myUser);
                    User user = new User(myUser.getId(), myUser.getFirstName(),
                            myUser.getLastName(), myUser.getEmail(), myUser.getAuthenticationId(), myUser.getActive(),
                            myUser.getUserAuthAccountCreated(), myUser.getSearchable());

                    LOG.info("user to return: {}", user);
                    return user;
                });

    }

    @Override
    public Mono<List<User>> getBatchOfUserById(List<UUID> uuids) {
        LOG.info("get user by batch of ids");

        return userRepository.findByIdIn(uuids).map(myUser -> new User(myUser.getId(), myUser.getFirstName(), myUser.getLastName(),
                myUser.getEmail(), myUser.getAuthenticationId(), myUser.getActive(),
                myUser.getUserAuthAccountCreated(), myUser.getSearchable())).collectList();
    }



}
