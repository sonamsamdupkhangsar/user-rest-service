package me.sonam.user.repo;


import me.sonam.user.repo.entity.MyUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepository extends ReactiveCrudRepository<MyUser, UUID> {
    Mono<MyUser> findByEmail(String email);
    Flux<MyUser> findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(String firstName, String lastName);
    Mono<MyUser> findByAuthenticationId(String authenticationId);
    @Query("update My_User mu set mu.first_Name= :firstName, mu.last_Name= :lastName where mu.authentication_Id= :authenticationId")
    Mono<MyUser> updateFirstNameAndLastNameByAuthenticationId(@Param("firstName")String firstName,
                                                              @Param("lastName") String lastName,
                                                              @Param("authenticationId") String authenticationId);
    @Query("update My_User mu set mu.profile_photo= :profilePhoto where mu.authentication_id= : authenticationId")
    Mono<MyUser> updateProfilePhoto(@Param("profilePhoto") String profielPhoto, @Param("authenticationId")
                                    String authenticationId);
}
