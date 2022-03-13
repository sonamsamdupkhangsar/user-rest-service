package me.sonam.account;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories
class R2DBCConfiguration extends AbstractR2dbcConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(R2DBCConfiguration.class);

    @Bean
    public H2ConnectionFactory connectionFactory() {
        LOG.info("creating H2ConnectionFactory..");
        H2ConnectionFactory h2ConnectionFactory =  new H2ConnectionFactory(
                H2ConnectionConfiguration.builder()
                        .url("mem:testdb;DB_CLOSE_DELAY=-1;")
                        .username("sa")
                        .build()
        );

        LOG.info("created H2ConnectionFactory.");
        return h2ConnectionFactory;
    }

   @Bean
    public ConnectionFactory monnectionFactory() {
        return ConnectionFactories.get("r2dbc:h2:mem:///testdb?options=DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");

    }
}