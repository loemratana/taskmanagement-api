package com.ratana.task_management_api.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Cluster-wide locks for {@code @Scheduled} jobs, so running several API instances does not send
 * reminders, overdue alerts, awards or retries more than once. Each job marked
 * {@code @SchedulerLock} runs on whichever instance takes its row in the {@code shedlock} table
 * first; the others skip that run.
 *
 * <p>Lock times use the database clock ({@code usingDbTime}), so instances whose clocks drift
 * apart still agree on when a lock expires. The default lock-at-most is a safety net for a
 * crashed instance: its lock frees itself after that long.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
