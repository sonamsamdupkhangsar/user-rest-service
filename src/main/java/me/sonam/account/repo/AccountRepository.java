package me.sonam.account.repo;


import me.sonam.account.repo.entity.Account;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AccountRepository extends ReactiveCrudRepository<Account, UUID> {
    Mono<Boolean> existsByUserIdAndActiveTrue(UUID var1);
    Mono<Account> findByUserId(UUID userId);

    Mono<Integer> countByUserId(UUID userId);

    @Modifying
    @Query("Update Account a set a.active=true and a.access_date_time= :localDateTime where a.user_id= :userId")
    Mono<Integer> activeAccount(@Param("userId") UUID userId, @Param("localDateTime") LocalDateTime localDateTime);

}
