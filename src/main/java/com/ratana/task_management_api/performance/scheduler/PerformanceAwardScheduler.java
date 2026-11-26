package com.ratana.task_management_api.performance.scheduler;

import com.ratana.task_management_api.performance.service.PerformanceAwardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Announces top performers at 09:00 business time: last month's on the 1st of every month, last
 * year's on 2 January (the 1st is a public holiday). Set a cron to "-" to turn that announcement off.
 * Publishing is idempotent, and a missed run can be done by hand: POST /api/v1/performance/awards/publish.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PerformanceAwardScheduler {

    private final PerformanceAwardService awardService;

    @SchedulerLock(name = "performance-award-monthly", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    @Scheduled(cron = "${app.performance.awards.monthly-cron:0 0 9 1 * *}",
            zone = "${app.performance.zone:Asia/Phnom_Penh}")
    public void announceLastMonth() {
        log.info("Monthly top-performer announcement starting");
        awardService.publishPreviousMonth();
    }

    @SchedulerLock(name = "performance-award-yearly", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    @Scheduled(cron = "${app.performance.awards.yearly-cron:0 0 9 2 1 *}",
            zone = "${app.performance.zone:Asia/Phnom_Penh}")
    public void announceLastYear() {
        log.info("Yearly top-performer announcement starting");
        awardService.publishPreviousYear();
    }
}
