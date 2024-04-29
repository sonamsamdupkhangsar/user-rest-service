package me.sonam.user.handler;

import java.util.UUID;

public record User (UUID id, String firstName, String lastName, String email, String authenticationId, Boolean active, Boolean userAuthAccountCreated){
    public User() {
        this(null, null, null, null, null, null, null);
    }
}
