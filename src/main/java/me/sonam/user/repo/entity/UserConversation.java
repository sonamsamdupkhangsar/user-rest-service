package me.sonam.user.repo.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

public class UserConversation implements Persistable<UUID> {
    @Id
    private UUID userId;
    private UUID conversationId;
    private boolean newRow;

    public UserConversation(UUID userId, UUID conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
        newRow = true;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setNewRow(Boolean value) {
        this.newRow = value;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }
}
