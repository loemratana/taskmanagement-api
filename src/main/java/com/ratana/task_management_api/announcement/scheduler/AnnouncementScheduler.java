package com.ratana.task_management_api.announcement.scheduler;

import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.repository.AnnouncementRepository;
import com.ratana.task_management_api.announcement.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Every minute: publishes scheduled announcements that are due, then expires ended ones.
 * Each publish runs in its own transaction, so one failure doesn't block the rest;
 * @Version on Announcement stops a concurrent manual publish from sending twice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementScheduler {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementService announcementService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.announcement.check-interval-ms:60000}")
    public void run() {
        List<UUID> due = announcementRepository.findIdsDueForPublish(AnnouncementStatus.SCHEDULED, clock.instant());
        int published = 0;
        for (UUID id : due) {
            try {
                if (announcementService.publishIfDue(id)) {
                    published++;
                }
            } catch (Exception e) {
                log.error("Scheduled publish failed for announcement {}", id, e);
            }
        }

        int expired = 0;
        try {
            expired = announcementService.expireDue();
        } catch (Exception e) {
            log.error("Announcement expiry failed", e);
        }

        if (published > 0 || expired > 0) {
            log.info("Announcement scheduler: {} published, {} expired", published, expired);
        }
    }
}
