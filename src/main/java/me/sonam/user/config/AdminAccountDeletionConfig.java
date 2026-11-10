package me.sonam.user.config;

import me.sonam.user.handler.AdminAccountDeletionOperations;
import me.sonam.user.handler.AdminAccountDeletionService;
import me.sonam.user.handler.RegularAccountDeletionService;
import me.sonam.user.repo.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the coordinator only when explicitly enabled; delegation routes require the internal service identity. */
@Configuration
@ConditionalOnProperty(name = "account-deletion.admin-enabled", havingValue = "true")
public class AdminAccountDeletionConfig {

    @Bean
    public AdminAccountDeletionService adminAccountDeletionService(
            UserRepository userRepository,
            AdminAccountDeletionOperations adminAccountDeletionOperations,
            RegularAccountDeletionService regularAccountDeletionService) {
        return new AdminAccountDeletionService(
                userRepository,
                adminAccountDeletionOperations,
                regularAccountDeletionService);
    }
}
