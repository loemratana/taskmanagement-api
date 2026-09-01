package com.ratana.task_management_api.scheduler;


import com.ratana.task_management_api.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    // Run every hour, adjust as needed
    @Scheduled(fixedDelay = 3600000)  /// 1 hour after each execution
    public void deleteExpiredTokens() {
        log.info("Starting scheduled cleanup of expired tokens");
        try{
            refreshTokenService.deleteExpiredTokens();
        }
        catch(Exception e){
            log.error("Error while deleting expired tokens", e);
        }

    }
}
