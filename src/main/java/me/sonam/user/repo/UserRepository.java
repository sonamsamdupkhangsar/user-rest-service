package me.sonam.user.repo;


import me.sonam.user.repo.entity.MyUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends ReactiveCrudRepository<MyUser, UUID> {
    Flux<MyUser> findByIdIn(List<UUID> ids);
    Mono<MyUser> findByEmail(String email);
    Mono<Boolean> existsByEmailAndIdNot(String email, UUID id);
    Mono<Boolean> existsByEmail(String email);
    Mono<Boolean> existsByAuthenticationId(String authenticationId);
    Mono<Boolean> existsByAuthenticationIdAndActiveTrue(String authenticationId);
    Mono<Integer> deleteByAuthenticationIdAndEmailAndActiveFalse(String authenticationId, String email);
    Mono<Integer> deleteByEmailAndActiveFalse(String email);
    Mono<Boolean> existsByEmailAndActiveTrue(String email);
    Mono<Integer> deleteByAuthenticationIdAndActiveFalse(String authenticationId);
    Mono<Boolean> existsByAuthenticationIdOrEmail(String authenticationId, String email);
    Mono<Void> deleteByAuthenticationId(String authenticationId);
    Flux<MyUser> findByFirstNameContainingIgnoreCaseAndLastNameContainingIgnoreCase(String firstName, String lastName);
    Mono<MyUser> findByAuthenticationId(String authenticationId);
    @Query("update My_User mu set mu.first_Name= :firstName, mu.last_Name= :lastName where mu.authentication_Id= :authenticationId")
    Mono<Integer> updateFirstNameAndLastNameByAuthenticationId(@Param("firstName")String firstName,
                                                              @Param("lastName") String lastName,
                                                              @Param("authenticationId") String authenticationId);

    @Query("update My_User set first_Name= :firstName, last_Name= :lastName, email= :email, searchable = :searchable where authentication_Id= :authenticationId")
    Mono<Integer> updateFirstNameAndLastNameAndEmailAndSearchableByAuthenticationId(
            @Param("firstName")String firstName,
            @Param("lastName") String lastName,
            @Param("email") String email,
            @Param("searchable")boolean searchable,
            @Param("authenticationId") String authenticationId);

    @Query("update My_User set first_Name= :firstName, last_Name= :lastName, email= :email, searchable = :searchable " +
            " where authentication_Id= :authenticationId")
    Mono<Integer> updateByAuthenticationId(
            @Param("firstName")String firstName,
            @Param("lastName") String lastName,
            @Param("email") String email,
            @Param("searchable")boolean searchable,
            @Param("authenticationId") String authenticationId);

    @Query("update My_User set profile_photo= CAST(:profilePhoto AS json) where authentication_Id= :authenticationId")
    Mono<Integer> updateProfilePhotoByAuthenticationId(@Param("profilePhoto") String profilePhoto, @Param("authenticationId") String authenticationId);


    @Query("update My_User mu set mu.profile_photo_file_key= :profilePhotoFileKey, mu.thumbnail_file_key= :thumbnailFileKey " +
            "where mu.authentication_id= :authenticationId")
    Mono<Integer> updateProfilePhoto( @Param("profilePhotoFileKey") String profilePhotoFileKey,
                                      @Param("thumbnailFileKey") String thumbnailFileKey
                                , @Param("authenticationId")
                                    String authenticationId);

    @Query("update My_user set active=true where authentication_Id= :authenticationId")
    Mono<Integer> updateUserActiveTrue(@Param("authenticationId") String authenticationId);
    Mono<Boolean> existsByAuthenticationIdAndUserAuthAccountCreatedTrue(String authenticationId);
    Mono<Integer> deleteByAuthenticationIdAndUserAuthAccountCreatedFalse(String authenticationId);
    Mono<Integer> deleteByEmailAndUserAuthAccountCreatedFalse(String email);
    Mono<Boolean> existsByEmailAndUserAuthAccountCreatedTrue(String email);

    @Query("update My_user set user_auth_account_created=true where authentication_Id= :authenticationId")
    Mono<Integer> updatedUserAuthAccountCreatedTrue(String authenticationId);
}
