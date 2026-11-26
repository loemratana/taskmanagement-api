package com.ratana.task_management_api.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every request slower than app.observability.slow-request-ms (default 200 ms) with its
 * status, duration and SQL statement count, e.g.
 * {@code Slow request GET /api/v1/tasks -> 200 in 812 ms, 41 SQL statements}.
 * Runs first in the chain, so the time includes authentication.
 */
@Slf4j
@Configuration
public class SlowRequestLoggingFilter {

    @Bean
    HibernatePropertiesCustomizer sqlStatementCounterCustomizer() {
        return properties -> properties.put("hibernate.session_factory.statement_inspector",
                new SqlStatementCounter());
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    OncePerRequestFilter slowRequestFilter(@Value("${app.observability.slow-request-ms:200}") long thresholdMs) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                long start = System.nanoTime();
                SqlStatementCounter.reset();
                try {
                    chain.doFilter(request, response);
                } finally {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    if (ms >= thresholdMs) {
                        log.warn("Slow request {} {} -> {} in {} ms, {} SQL statements",
                                request.getMethod(), request.getRequestURI(), response.getStatus(),
                                ms, SqlStatementCounter.current());
                    }
                    SqlStatementCounter.clear();
                }
            }
        };
    }
}
