package me.sonam.user.repo;


import me.sonam.user.repo.entity.MyUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepository extends ReactiveCrudRepository<MyUser, UUID> {
    Mono<MyUser> findByEmail(String email);
}
