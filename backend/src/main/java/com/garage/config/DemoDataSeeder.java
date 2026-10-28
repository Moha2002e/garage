package com.garage.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/**
 * Inserts a large set of demo data (seed/seed.sql) once, when SEED_ENABLED=true.
 * Idempotent: does nothing if demo clients (@seed.garage.test) already exist.
 * Remove the variable (and this class + seed.sql) once the data is in.
 */
@Component
@ConditionalOnProperty(name = "SEED_ENABLED", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DataSource dataSource;

    public DemoDataSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer existing = new JdbcTemplate(dataSource)
                .queryForObject("select count(*) from clients where email like '%@seed.garage.test'", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Demo data already present ({} clients), seeding skipped", existing);
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ScriptUtils.executeSqlScript(connection,
                        new EncodedResource(new ClassPathResource("seed/seed.sql"), "UTF-8"));
                connection.commit();
                log.info("Demo data inserted");
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
