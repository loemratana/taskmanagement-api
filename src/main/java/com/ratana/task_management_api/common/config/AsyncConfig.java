package com.ratana.task_management_api.common.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /** Keep below the platform's kill timeout (Docker's default stop grace period is 10s). */
    @Value("${app.async.shutdown-await-seconds:8}")
    private int asyncShutdownAwaitSeconds;

    @Bean(name = "notificationExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notif-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Graceful shutdown: on deploy/stop, stop taking new work but let queued and running
        // notifications finish (bounded, so a hung provider can't block shutdown forever).
        // Anything still unsent after that stays PENDING/FAILED and RetryScheduler picks it up.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(asyncShutdownAwaitSeconds);
        executor.initialize();
        return executor;
    }

    @Override
    public @Nullable AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async error in method: {}", method.getName(), ex);
            if (params != null) {
                for (Object param : params) {
                    log.error("  - Param: {}", param);
                }
            }
        };
    }
}
