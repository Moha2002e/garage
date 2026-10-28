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
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer existing = jdbc.queryForObject(
                "select count(*) from clients where email like '%@seed.garage.test'", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Demo data already present ({} clients), insertion skipped", existing);
        } else {
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
        realignTotals(jdbc);
    }

    /** Idempotent: aligns demo totals on the sum of their lines (rounded per line, HALF_UP), only for demo clients. */
    private void realignTotals(JdbcTemplate jdbc) {
        int lines = jdbc.update("""
                update invoice_lines il set total = round(il.quantity * il.unit_price, 2)
                where il.total <> round(il.quantity * il.unit_price, 2)
                  and il.invoice_id in (select i.id from invoices i join clients c on c.id = i.client_id
                                        where c.email like '%@seed.garage.test')""");
        int invoices = jdbc.update("""
                update invoices i set total = s.t
                from (select invoice_id, sum(total) t from invoice_lines group by invoice_id) s
                where s.invoice_id = i.id and i.total <> s.t
                  and i.client_id in (select id from clients where email like '%@seed.garage.test')""");
        int quotes = jdbc.update("""
                update quotes q set total = s.t
                from (select quote_id, sum(round(quantity * unit_price, 2)) t
                      from quote_lines where active group by quote_id) s
                where s.quote_id = q.id and q.total <> s.t
                  and q.appointment_id in (select a.id from appointments a join clients c on c.id = a.client_id
                                           where c.email like '%@seed.garage.test')""");
        log.info("Demo totals realigned: {} quotes, {} invoices, {} invoice lines", quotes, invoices, lines);
    }
}
