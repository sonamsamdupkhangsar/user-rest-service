package me.sonam.user;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import me.sonam.user.handler.UserTransfer;
import me.sonam.user.handler.carrier.User;
import me.sonam.user.repo.UserRepository;
import me.sonam.user.repo.entity.MyUser;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * This will test the response from the Rest endpoint, the business service
 * , and the data repository interfaces.
 * For the `/public/user/signup` endpoint see {@link UserEndpointMockWebServerTest}
 * will test that endpoint which will mock the response to the Authentication endpoint.
 */
@EnableAutoConfiguration
@ExtendWith(SpringExtension.class)
@SpringBootTest( webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserEndpointTest {
    private static final Logger LOG = LoggerFactory.getLogger(UserEndpointTest.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    @AfterEach
    public void deleteUserRepo() {
        userRepository.deleteAll().subscribe();
    }

    @Test
    public void actuatorEndpoint() {
        LOG.info("access actuator health endpoint");
        EntityExchangeResult<String> result = webTestClient.get().uri("/users")
                .exchange().expectStatus().is4xxClientError().expectBody(String.class).returnResult();

        LOG.info("result: {}", result.getResponseBody());

    }

    @Test
    public void updateUser() throws InterruptedException, IOException {
        LOG.info("make rest call to save user and create authentication record");

        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email",
                authenticationId);

        userRepository.save(myUser2).subscribe();

        UserTransfer userTransfer = new UserTransfer();

        userTransfer.setFirstName("Josey");
        userTransfer.setLastName("Cat");
        userTransfer.setEmail("josey.cat@@cat.emmail");

        userTransfer.setAuthenticationId(authenticationId);

        LOG.info("update user fields with jwt in auth bearer token");
        EntityExchangeResult<String> result = webTestClient.put().uri("/users")
                .bodyValue(userTransfer)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("result: {}", result.getResponseBody());


        userRepository.findByAuthenticationId("dave").as(StepVerifier::create)
                .expectNextMatches(myUser -> {
                    LOG.info("do expectNextMatches");
                            LOG.info("profilePhoto: '{}'", myUser.getProfilePhoto());


                    return myUser.getEmail().equals("josey.cat@@cat.emmail");



                })
                .expectComplete().verify();

    }

    @Test
    public void updateProfilePhoto() throws InterruptedException, IOException {
        LOG.info("make rest call to save user and create authentication record");

        final String authenticationId = "dave";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email",
                authenticationId);

        userRepository.save(myUser2).subscribe();

        UserTransfer userTransfer = new UserTransfer();

        userTransfer.setFirstName("Josey");
        userTransfer.setLastName("Cat");
        userTransfer.setEmail("josey@cat.email");

        JsonObject jsonObject = getJsonObject();
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<HashMap<String, String>> tr = new TypeReference<HashMap<String, String>>() {};

        LOG.info("jsonObject.toString: {}", jsonObject.toString());
        final Map<String, String> map = objectMapper.readValue(jsonObject.toString(), tr);

        userTransfer.setProfilePhoto(jsonObject.toString());
        userTransfer.setAuthenticationId(authenticationId);

        LOG.info("update user fields with jwt in auth bearer token");
        EntityExchangeResult<String> result = webTestClient.put().uri("/users/photo")
                .bodyValue(userTransfer)
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult();

        LOG.info("result: {}", result.getResponseBody());


        userRepository.findByAuthenticationId("dave").as(StepVerifier::create)
                .expectNextMatches(myUser -> {
                    LOG.info("do expectNextMatches");
                    LOG.info("profilePhoto: '{}'", myUser.getProfilePhoto());
                    try {
                        final String myJson = myUser.getProfilePhoto();
                        LOG.info("myJson: {}", myJson);
                        JsonElement jsonElement = JsonParser.parseString(myJson);
                        LOG.info("jsonElement: {}", jsonElement.getAsString());
                        LOG.info("json.instance of {}", jsonElement.getClass() );

                        JsonObject jsonObject2 = null;
                        if (jsonElement.isJsonPrimitive()) {
                            JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
                            // Get the primitive value (string, number, boolean)
                            LOG.info("2- is a json primitive: {}", jsonPrimitive);
                            LOG.info("2- string: {}", jsonPrimitive.getAsString());
                            JsonElement jsonElement2 = JsonParser.parseString(jsonPrimitive.getAsString());
                            LOG.info("2- jsonObject?: {}", jsonElement2.isJsonObject());

                            jsonObject2 = jsonElement2.getAsJsonObject();
                        }
                        LOG.info("2- jsonObject from db: {}", jsonObject2);
                        LOG.info("2- jsonbject equals jsonObject db: {}", jsonObject.equals(jsonObject2));

                        LOG.info("myUser.email: {}, myUser {}", myUser.getEmail(), myUser);

                        boolean userEquals = myUser.getEmail().equals(myUser2.getEmail()) &&
                                myUser.getFirstName().equals(myUser2.getFirstName()) &&
                                myUser.getLastName().equals(myUser2.getLastName());
                        LOG.info("userEquals: {}", userEquals);
                        return userEquals && jsonObject2 != null && jsonObject2.equals(jsonObject);
                    }
                    catch (Exception e) {
                        LOG.error("failed to create a objectmapper", e);
                    }
                    return false;

                })
                .expectComplete().verify();

    }

    @Test
    public void jsonTest(){
        //String json = "{\"name\": \"John\", \"age\": 30, \"city\": \"New York\"}";
        String json = "{\"profilePhotoUrl\":\"https://sonam.cloud/user-rest-service/videos/2024-11-22/2024-11-22T08:15:40.314460.jpeg\",\"profilePhotoAcl\":\"private\",\"profilePhotoFileKey\":\"videos/2024-11-22/2024-11-22T08:15:40.314460.jpeg\",\"thumbnailUrl\":\"https://sonam.cloud/user-rest-service/videos/2024-11-22/thumbnail/2024-11-22T08:15:40.314460.jpeg\",\"thumbnailAcl\":\"public\"}";

        JsonElement jsonElement = JsonParser.parseString(json);

        if (jsonElement.isJsonObject()) {
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            String name = jsonObject.get("profilePhotoUrl").getAsString();
            LOG.info("profilePhotoUrl: {}", name);
        } else {
            LOG.info("Not a JSON object");
        }
    }
    private static @NotNull JsonObject getJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("profilePhotoUrl", "https://sonam.cloud/user-rest-service/videos/2024-11-22/2024-11-22T08:15:40.314460.jpeg");
        jsonObject.addProperty("profilePhotoAcl", "private");
        jsonObject.addProperty("profilePhotoFileKey", "videos/2024-11-22/2024-11-22T08:15:40.314460.jpeg");
        jsonObject.addProperty("thumbnailUrl", "https://sonam.cloud/user-rest-service/videos/2024-11-22/thumbnail/2024-11-22T08:15:40.314460.jpeg");
        jsonObject.addProperty("thumbnailAcl", "public");
        return jsonObject;
    }

    @Test
    public void getUserByAuthId() {
        LOG.info("make rest call to save user and create authentication record");

        final String authenticationId = "dommy@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email", authenticationId);
        userRepository.save(myUser2).subscribe();

        // should get an error because the searchable is not true
        webTestClient.get().uri("/users/profile/authentication-id/" + authenticationId)
                .headers(addJwt(jwt)).exchange().expectStatus().isBadRequest()
                .returnResult(MyUser.class).getResponseBody();

        myUser2.setSearchable(true);
        myUser2.setNewAccount(false);
        userRepository.save(myUser2).subscribe();

        LOG.info("get user by auth id");

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/users/profile/authentication-id/"+authenticationId)
                .headers(addJwt(jwt)).exchange().expectStatus().isOk()
                .returnResult(MyUser.class).getResponseBody();


        StepVerifier.create(myUserFlux)
                .assertNext(myUser -> {
                    LOG.info("asserting found user by authId: {}", myUser);
                    assertThat(myUser.getLastName()).isEqualTo("thecat");
                    assertThat(myUser.getEmail()).isEqualTo("dommy@cat.email");
                })
                .verifyComplete();

        Flux<Map> result = webTestClient.get().uri("/users/profile/authentication-id/dummy-123")
                .headers(addJwt(jwt)).exchange().expectStatus().isBadRequest()
                .returnResult(Map.class).getResponseBody();

        StepVerifier.create(result)
                .assertNext(map -> {
                    LOG.info("user not found {}", map.get("error"));

                })
                .verifyComplete();
    }

    @Test
    public void findMatchingFirstNameAndLastName() {
        LOG.info("test find by firstName and lastName matching");
        MyUser myUser = new MyUser("Dommy", "thecat", "dommy@cat.email", "dommy@cat.email");

        userRepository.save(myUser).subscribe();

        myUser = new MyUser("Dommy", "thecatman", "dommythecatman@cat.email", "dommythecatman@cat.email");

        userRepository.save(myUser).subscribe();

        myUser = new MyUser("Dommy", "mac", "dommymacn@cat.email", "dommymacn@cat.email");

        userRepository.save(myUser).subscribe();

        final String authenticationId = "dommymacn@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/users/names/dommy/thecat")
                .headers(addJwt(jwt))
                .exchange().expectStatus().isOk()
                .returnResult(MyUser.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .expectNextCount(2)
                .verifyComplete();
    }

    /**
     * this will test the endpoint /users/ids/id get batch of usres by id
     */
    @Test
    public void getUsersByIds() {
        LOG.info("get batch of users by ids");

        final String authenticationId = "dommy@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser1 = new MyUser("Sonam", "thecat", "dommy@cat.email", "sonam");
        userRepository.save(myUser1).subscribe();

        MyUser myUser2 = new MyUser("Tenzing", "Passang", "tenzing@cat.email", "tenzing");
        userRepository.save(myUser2).subscribe();

        MyUser myUser3 = new MyUser("John", "Wix", "john@cat.email", "john");
        userRepository.save(myUser3).subscribe();

        LOG.info("get users by their user.id");

        assertThat(myUser1.getId()).isNotNull();
        assertThat(myUser2.getId()).isNotNull();
        assertThat(myUser3.getId()).isNotNull();

        List<UUID> idsList = List.of(myUser1.getId(), myUser2.getId(), myUser3.getId());
        String idsCsv = idsList.stream().map(uuid -> uuid + ",").collect(Collectors.joining());
        LOG.info("idsCsv without ,: {}", idsCsv);
        if (idsCsv.endsWith(",")) {
            int lastIndexOfExtraComma = idsCsv.lastIndexOf(",");
            LOG.info("lastIndexOfExtraComma: {}", lastIndexOfExtraComma);

            idsCsv = idsCsv.substring(0, lastIndexOfExtraComma);

        }
        LOG.info("idsCsv: {}", idsCsv);

        Mono<String> stringMono = webTestClient.get().uri("/users/ids/"+idsCsv)
                .headers(addJwt(jwt)).exchange().expectStatus().isOk()
                .returnResult(String.class).getResponseBody().single();

        Mono<Collection<User>> collectionMono = stringMono.flatMap(s -> {
            LOG.info("string is {}", s);
            ObjectMapper objectMapper = new ObjectMapper();
            Collection<User> userCollection = null;
            try {
                userCollection = objectMapper.readValue(s, new TypeReference<Collection<User>>() {});
            } catch (JsonProcessingException e) {
                LOG.error("error on objectMapper", e);
                throw new RuntimeException(e);
            }
            return Mono.just(userCollection);
        });

        StepVerifier.create(collectionMono)
                .assertNext(users -> {
                    assertThat(users.size()).isEqualTo(3);
                    List<UUID> returnedUserIds = users.stream().map(User::getId).toList();
                    assertThat(idsList.containsAll(returnedUserIds)).isTrue();
                    LOG.info("assert the idsList contains all the ids returned, idsList {} vs returnedUserIds: {},",
                            idsList, returnedUserIds);

                    users.forEach(user -> {
                        assertThat(user.getId()).isNotNull();
                        assertThat(user.getFirstName()).isNotNull();
                        assertThat(user.getLastName()).isNotNull();
                        assertThat(user.getActive()).isNotNull();
                        assertThat(user.getUserAuthAccountCreated()).isNotNull();
                    });
                })
                .verifyComplete();

        LOG.info("test completed");
    }


    /**
     * get user by user.id
     */
    @Test
    public void getUserById() {
        LOG.info("make rest call to save user and create authentication record");

        final String authenticationId = "dommy@cat.email";
        Jwt jwt = jwt(authenticationId);
        when(this.jwtDecoder.decode(anyString())).thenReturn(Mono.just(jwt));

        MyUser myUser2 = new MyUser("Dommy", "thecat", "dommy@cat.email", authenticationId);

        userRepository.save(myUser2).subscribe();

        LOG.info("get user by id");
        assertThat(myUser2.getId()).isNotNull();

        Flux<MyUser> myUserFlux = webTestClient.get().uri("/users/"+myUser2.getId())
                .headers(addJwt(jwt)).exchange().expectStatus().isOk()
                .returnResult(MyUser.class).getResponseBody();

        StepVerifier.create(myUserFlux)
                .assertNext(myUser -> {
                    LOG.info("asserting found user by id: {}", myUser);
                    assertThat(myUser.getLastName()).isEqualTo("thecat");
                    assertThat(myUser.getEmail()).isEqualTo("dommy@cat.email");
                })
                .verifyComplete();

        Flux<Map> result = webTestClient.get().uri("/users/dummy-123")
                .headers(addJwt(jwt)).exchange().expectStatus().isBadRequest()
                .returnResult(Map.class).getResponseBody();

        StepVerifier.create(result)
                .assertNext(map -> {
                    LOG.info("user not found {}", map.get("error"));

                })
                .verifyComplete();
    }

    private Jwt jwt(String subjectName) {
        return new Jwt("token", null, null,
                Map.of("alg", "none"), Map.of("sub", subjectName));
    }

    private Consumer<HttpHeaders> addJwt(Jwt jwt) {
        return headers -> headers.setBearerAuth(jwt.getTokenValue());
    }
}
