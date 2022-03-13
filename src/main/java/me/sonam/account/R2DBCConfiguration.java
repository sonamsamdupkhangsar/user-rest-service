package me.sonam.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@Configuration
@EnableR2dbcRepositories
public class R2DBCConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(R2DBCConfiguration.class);


}
