package me.sonam.user.handler;


import cloud.sonam.s3.config.S3ClientConfigurationProperties;
import cloud.sonam.s3.file.S3Service;
import cloud.sonam.s3.file.util.ImageUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import me.sonam.user.handler.carrier.User;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import me.sonam.user.util.ProfilePhotoUrl;
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
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Autowired
    private S3Service s3Service;
    @Autowired
    private S3ClientConfigurationProperties s3Properties;

    @Value("${profilePhotoFolder:profile/}")
    private String profilePhotoFolder;
    @Value("${profilePhotoMaxBytes:5242880}")
    private long profilePhotoMaxBytes;

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
     *
     * @param userMono
     * @return
     */
    @Override
    public Mono<String> signupUser(Mono<UserTransfer> userMono) {
        LOG.info("signup user");

        return userMono.flatMap(this::validateOnSignup).//thenReturn("User sign up success, checkemail");  //
                flatMap(userTransfer ->

                userRepository.existsByAuthenticationIdIgnoreCaseAndActiveTrue(userTransfer.getAuthenticationId())
                        .filter(aBoolean -> !aBoolean)
                        .switchIfEmpty(Mono.error(new SignupException("User is already active with that username (authenticationId)")))
                        .flatMap(aBoolean -> userRepository.existsByAuthenticationIdIgnoreCaseAndUserAuthAccountCreatedTrue(userTransfer.getAuthenticationId()))
                        .filter(aBoolean -> {
                            LOG.info("aBoolean for findByAuthenticationIdAndUserAuthAccountCreatedTrue is {}", aBoolean);

                            return !aBoolean;
                        }).switchIfEmpty(Mono.error(new SignupException("User account has already been created for that username, check to activate it by email")))
                        .flatMap(aBoolean -> userRepository.existsByEmailIgnoreCaseAndActiveTrue(userTransfer.getEmail()))
                        .filter(aBoolean -> !aBoolean)
                        .switchIfEmpty(Mono.error(new SignupException("User account is active for that email")))
                        .flatMap(aBoolean -> userRepository.existsByEmailIgnoreCaseAndUserAuthAccountCreatedTrue(userTransfer.getEmail()))
                        .filter(aBoolean -> {
                            LOG.info("aBoolean {}", aBoolean);
                            return !aBoolean;
                        }).switchIfEmpty(Mono.error(new SignupException("User account has already been created for that email, check to activate it by email")))
                        .flatMap(aBoolean -> accountWebClient.deleteAccountByEmail(userTransfer.getEmail()))
                        .flatMap(s -> authenticationWebClient.deleteByAuthenticationId(userTransfer.getAuthenticationId()))
                        .flatMap(string -> userRepository.deleteByAuthenticationIdIgnoreCaseAndUserAuthAccountCreatedFalse(userTransfer.getAuthenticationId()))
                        //just delete rows with email and account created is in false - meaning not fully created
                        .flatMap(rows -> userRepository.deleteByEmailIgnoreCaseAndUserAuthAccountCreatedFalse(userTransfer.getEmail()))
                        .flatMap(integer -> Mono.just(new MyUser(userTransfer.getFirstName(), userTransfer.getLastName(),
                                userTransfer.getEmail(), userTransfer.getAuthenticationId(), userTransfer.isActive())))
                        .flatMap(myUser -> userRepository.save(myUser))
                        .flatMap(myUser ->
                                authenticationWebClient.create(userTransfer.getAuthenticationId(), userTransfer.getPassword(), myUser.getId(), myUser.getActive())
                                        .then(accountWebClient.createAccount(myUser.getFirstName() + " " + myUser.getLastName(),
                                                userTransfer.getAuthenticationId(), myUser.getId(),
                                                userTransfer.getEmail(), myUser.getActive(),
                                                userTransfer.getPassword() != null && !userTransfer.getPassword().isEmpty(),
                                                userTransfer.getActivationHost()))));
    }

    private Mono<UserTransfer> validateOnSignup(UserTransfer userTransfer) {
        LOG.debug("userTransfer: {}", userTransfer);

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

        /*if (userTransfer.getPassword().trim().isEmpty()) {
            LOG.error("password is empty");
            return Mono.error(new UserException("password needs to be entered"));
        }*/

        return Mono.just(userTransfer);
    }

    /**
     * this will only update the user profilePhoto property.
     * @param authenticationId
     * @param profilePhotoUpdateMono
     * @return
     */
    @Override
    public Mono<String> updateProfilePhoto(String authenticationId, Mono<ProfilePhotoUpdate> profilePhotoUpdateMono) {
        LOG.info("update profile photo requested");

        return profilePhotoUpdateMono.flatMap(profilePhotoUpdate -> {
            LOG.debug("profile photo metadata supplied: {}", profilePhotoUpdate.getProfilePhoto() != null);
            return userRepository.findByAuthenticationIdIgnoreCase(authenticationId)
                 .switchIfEmpty(Mono.error(new SignupException("email: email already used")))

                    .flatMap(myUser -> {
                                LOG.info("updating profile photo metadata");
                                if (profilePhotoUpdate.getProfilePhoto() == null || profilePhotoUpdate.getProfilePhoto().isEmpty()) {
                                    LOG.error("profilePhoto value is empty");
                                    return Mono.error(new UserException("profilePhoto value is empty"));
                                }
                                return userRepository.updateProfilePhotoByAuthenticationId(
                                        profilePhotoUpdate.getProfilePhoto(), authenticationId);


                            })
                            .thenReturn("profilePhoto property updated");
        });
    }

    @Override
    public Mono<Map<String, String>> uploadProfilePhoto(String authenticationId, FilePart file) {
        MediaType mediaType = file.headers().getContentType();
        if (mediaType == null || !Set.of(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF)
                .contains(mediaType)) {
            return Mono.error(new IllegalArgumentException("Profile photo must be a JPEG, PNG, or GIF image"));
        }
        ImageUtil.getFileFormat(mediaType, file.filename());

        return userRepository.findByAuthenticationIdIgnoreCase(authenticationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User was not found")))
                .flatMap(user -> DataBufferUtils.join(file.content())
                        .flatMap(buffer -> {
                            int length = buffer.readableByteCount();
                            if (length == 0 || length > profilePhotoMaxBytes) {
                                DataBufferUtils.release(buffer);
                                return Mono.error(new IllegalArgumentException(
                                        "Profile photo must be between 1 byte and 5 MB"));
                            }
                            byte[] content = new byte[length];
                            buffer.read(content);
                            DataBufferUtils.release(buffer);
                            validateImage(content);
                            return replaceProfilePhoto(user, file.filename(), mediaType,
                                    ByteBuffer.wrap(content));
                        }));
    }

    private void validateImage(byte[] content) {
        try {
            BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalArgumentException("The selected file does not contain a supported image");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The selected image could not be read", exception);
        }
    }

    private Mono<Map<String, String>> replaceProfilePhoto(MyUser user, String filename,
                                                           MediaType mediaType, ByteBuffer bytes) {
        String folder = s3Properties.getRootPath() + s3Properties.getPhotoPath()
                + profilePhotoFolder + user.getId();
        String prefix = folder + "/";
        LocalDateTime uploaded = LocalDateTime.now();
        Dimension thumbnail = new Dimension(s3Properties.getThumbnailSize().getWidth(),
                s3Properties.getThumbnailSize().getHeight());

        return s3Service.deleteFolder(folder)
                .then(s3Service.uploadFile(Flux.just(bytes), prefix, filename, mediaType,
                        bytes.remaining(), ObjectCannedACL.PRIVATE, uploaded))
                .flatMap(photoKey -> s3Service.createPresignedUrl(Mono.just(photoKey))
                        .flatMap(photoUrl -> s3Service.createPhotoThumbnail(uploaded, photoUrl, prefix,
                                        ObjectCannedACL.PUBLIC_READ, filename, mediaType, thumbnail)
                                .map(thumbnailKey -> {
                                    Map<String, String> metadata = new HashMap<>();
                                    metadata.put("profilePhotoKey", photoKey);
                                    metadata.put("profilePhotoUrl", s3Properties.getSubdomain() + "/" + photoKey);
                                    metadata.put("profilePhotoAcl", ObjectCannedACL.PRIVATE.toString());
                                    metadata.put("thumbnailKey", thumbnailKey);
                                    metadata.put("thumbnailUrl", s3Properties.getSubdomain() + "/" + thumbnailKey);
                                    metadata.put("thumbnailAcl", ObjectCannedACL.PUBLIC_READ.toString());
                                    return metadata;
                                })))
                .flatMap(metadata -> userRepository.updateProfilePhotoByAuthenticationId(
                                toJson(metadata), user.getAuthenticationId())
                        .thenReturn(metadata));
    }

    private String toJson(Map<String, String> metadata) {
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize profile photo metadata", exception);
        }
    }

    /**
     * this will update the user firstname, lastname and searcable fields only.  It will not update the email or profilePhoto. For profilePhoto property there
     * is another endpoint to handle this.
     * @param authenticationId
     * @param userMono
     * @return
     */
    @Override
    public Mono<String> updateUser(String authenticationId, Mono<UserUpdate> userMono) {
        LOG.info("update user fields requested");

       return userMono.flatMap(userUpdate -> {
            LOG.info("userTransfer: {}", userUpdate);
                     return userRepository.findByAuthenticationIdIgnoreCase(userUpdate.getAuthenticationId())
                             .flatMap(myUser ->
                                     userRepository.updateFirstNameAndLastNameAndSearchableByAuthenticationId(userUpdate.getFirstName(), userUpdate.getLastName(),
                                             userUpdate.isSearchable(), userUpdate.getAuthenticationId())
                             )
                             .thenReturn("user firstname, lastname and email updated");
        });
    }

   /* @Override
    public Mono<MyUser> getUserByAuthenticationId(String authenticationId) {
        LOG.info("find user by authentication identifier");

        return userRepository.findByAuthenticationId(authenticationId);
    }*/

    @Override
    public Flux<MyUser> findMatchingName(String firstName, String lastName) {
        LOG.info("find matching user name requested");
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

        return userRepository.findByAuthenticationIdIgnoreCase(authenticationId)
                .filter(myUser -> !myUser.getActive())
                .switchIfEmpty(Mono.error(new UserException("user is active, cannot delete")))
                .flatMap(myUser ->   userRepository.deleteByAuthenticationIdIgnoreCaseAndActiveFalse(authenticationId))
                .thenReturn("deleted: " + authenticationId);
    }

    @Override
    public Mono<String> deleteUserData(UUID organizationId) {
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
                        return accountWebClient.deleteUserData(userId);
                    })
                    .then(authenticationWebClient.deleteUserData(userId))
                    .then(organizationWebClient.deleteUserData(organizationId, userId))
                    .then(roleWebClient.deleteUserData(organizationId, userId))
                    .thenReturn("delete my account success for user id: " + userId);
        });
    }

    @Override
    public Mono<Map<String, Object>> getUserByAuthenticationId(String authenticationId) {
        LOG.info("get user information by authentication identifier");

        return userRepository.findByAuthenticationIdIgnoreCase(authenticationId)
                .switchIfEmpty(Mono.error(new SignupException("user not found with authenticationId: "+
                        authenticationId)))
                .switchIfEmpty(Mono.error(new UserException("user searchable is turned off")))
                .map(myUser -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", myUser.getId().toString());
                    map.put("firstName", myUser.getFirstName());
                    map.put("lastName", myUser.getLastName());
                    map.put("email", myUser.getEmail());
                    if (myUser.getProfilePhoto() != null && !myUser.getProfilePhoto().isEmpty()) {
                        final String thumbnailUrl = ProfilePhotoUrl.getProfileUrl(myUser.getProfilePhoto());
                        LOG.debug("set profile photo thumbnail URL");
                        map.put("profilePhoto", thumbnailUrl);
                    }
                    else {
                        LOG.debug("profile photo metadata is absent");
                        map.put("profilePhoto", "");
                    }

                    map.put("authenticationId", myUser.getAuthenticationId());
                    DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                    if (myUser.getBirthDate() != null) {
                        map.put("dateOfBirth", dateFormat.format(myUser.getBirthDate()));
                    }
                    return map;
                });
    }

    @Override
    public Mono<Map<String, Object>> getUserByAuthenticationIdForProfileSearch(String authenticationId, boolean ignoreSearchable) {
        LOG.info("profile search by authentication identifier");

        return userRepository.findByAuthenticationIdIgnoreCase(authenticationId)
                .switchIfEmpty(Mono.error(new SignupException("user not found with authenticationId: "+
                        authenticationId)))
                .filter(myUser -> {
                    LOG.debug("ignoreSearchable {}", ignoreSearchable);
                    if (!ignoreSearchable) { //honor user's request to searchable setting
                        if (myUser.getSearchable() != null && myUser.getSearchable()) {
                            return myUser.getSearchable();
                        }
                        else {
                            return false;
                        }
                    }
                    else {//if query is to find user regardless of searchable field
                        return true;
                    }
                })
              //  .filter(myUser -> myUser.getSearchable() != null && myUser.getSearchable())
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
                            myUser.getUserAuthAccountCreated(), myUser.getSearchable(), myUser.getProfilePhoto());

                    LOG.info("user to return: {}", user);
                    return user;
                });

    }

    @Override
    public Mono<List<User>> getBatchOfUserById(List<UUID> uuids) {
        LOG.info("get user by batch of ids");

        return userRepository.findByIdIn(uuids).map(myUser -> new User(myUser.getId(), myUser.getFirstName(), myUser.getLastName(),
                myUser.getEmail(), myUser.getAuthenticationId(), myUser.getActive(),
                myUser.getUserAuthAccountCreated(), myUser.getSearchable(), myUser.getProfilePhoto())).collectList();
    }



}
