package com.ratana.task_management_api.auth.scheduler;


import com.ratana.task_management_api.auth.service.PasswordResetService;
import com.ratana.task_management_api.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;

    // Run every hour, adjust as needed
    @SchedulerLock(name = "refresh-token-cleanup", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    @Scheduled(fixedDelay = 3600000)  /// 1 hour after each execution
    public void deleteExpiredTokens() {
        log.info("Starting scheduled cleanup of expired tokens");
        try{
            refreshTokenService.deleteExpiredTokens();
            int codes = passwordResetService.deleteExpiredCodes();
            if (codes > 0) {
                log.info("Deleted {} expired password reset code(s)", codes);
            }
        }
        catch(Exception e){
            log.error("Error while deleting expired tokens", e);
        }

    }
}
