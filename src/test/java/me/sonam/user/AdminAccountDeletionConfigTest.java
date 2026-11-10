package me.sonam.user;

import cloud.sonam.s3.config.S3ClientConfigurationProperties;
import me.sonam.user.config.AdminAccountDeletionConfig;
import me.sonam.user.handler.AdminAccountDeletionOperations;
import me.sonam.user.handler.AdminAccountDeletionService;
import me.sonam.user.handler.RegularAccountDeletionService;
import me.sonam.user.handler.ServiceAdminAccountDeletionOperations;
import me.sonam.user.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccountDeletionConfigTest {

    @Test
    void adminDeletionIsNotRegisteredByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(AdminAccountDeletionConfig.class, ServiceAdminAccountDeletionOperations.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AdminAccountDeletionService.class);
                    assertThat(context).doesNotHaveBean(AdminAccountDeletionOperations.class);
                });
    }

    @Test
    void explicitEnablementRegistersTheConcreteAdapterAndCoordinator() {
        S3ClientConfigurationProperties s3ClientConfigurationProperties = mock(S3ClientConfigurationProperties.class);
        when(s3ClientConfigurationProperties.getRootPath())
                .thenReturn("authzmanager/");
        when(s3ClientConfigurationProperties.getPhotoPath())
                .thenReturn("photos/");
        when(s3ClientConfigurationProperties.getBucket())
                .thenReturn("test-bucket");

        new ApplicationContextRunner()
                .withUserConfiguration(AdminAccountDeletionConfig.class, ServiceAdminAccountDeletionOperations.class)
                .withPropertyValues(
                        "account-deletion.admin-enabled=true",
                        "organization-rest-service.context=http://organization/organizations",
                        "role-rest-service.context=http://role/roles",
                        "auth-server.root=http://authorization")
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(RegularAccountDeletionService.class, () -> mock(RegularAccountDeletionService.class))
                .withBean("serviceWebClientBuilder", WebClient.Builder.class, WebClient::builder)
                .withBean(S3AsyncClient.class, () -> mock(S3AsyncClient.class))
                .withBean(S3ClientConfigurationProperties.class, () -> s3ClientConfigurationProperties)
                .run(context -> {
                    assertThat(context).hasSingleBean(AdminAccountDeletionService.class);
                    assertThat(context).hasSingleBean(AdminAccountDeletionOperations.class);
                    assertThat(context.getBean(AdminAccountDeletionOperations.class))
                            .isInstanceOf(ServiceAdminAccountDeletionOperations.class);
                });
    }
}
