package me.sonam.user;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import me.sonam.user.handler.UserHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
@OpenAPIDefinition(info = @Info(title = "UserService", version = "1.0", description = "User service for signup user"))
public class Router {
    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    @Bean
    @RouterOperations(
            {
                    @RouterOperation(path = "/signup"
                    , produces = {
                        MediaType.APPLICATION_JSON_VALUE}, method= RequestMethod.GET,
                         operation = @Operation(operationId="singup", responses = {
                            @ApiResponse(responseCode = "200", description = "successful operation"),
                                 @ApiResponse(responseCode = "400", description = "invalid user id")}
                    ))
            }
    )
    public RouterFunction<ServerResponse> route(UserHandler handler) {
        LOG.info("building router function");
        return RouterFunctions.route(POST("/public/user/signup").and(accept(MediaType.APPLICATION_JSON)),
                handler::signupUser)
                .andRoute(PUT("/user").and(accept(MediaType.APPLICATION_JSON)),
                        handler::update)
                .andRoute(GET("/user/names/{firstName}/{lastName}").and(accept(MediaType.APPLICATION_JSON)),
                handler::findMatchingFirstNameAndLastName)
                .andRoute(GET("/user/{authId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::getUserByAuthId)
                .andRoute(PUT("/user/profilephoto").and(accept(MediaType.APPLICATION_JSON)),
                        handler::updateProfilePhoto)
                .andRoute(PUT("/user/activate/{authenticationId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::activateUser);
    }
}
