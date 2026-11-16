package com.ratana.task_management_api.announcement.repository;

import com.ratana.task_management_api.announcement.entity.AnnouncementRead;
import com.ratana.task_management_api.announcement.repository.projection.AnnouncementMetrics;
import com.ratana.task_management_api.announcement.repository.projection.DepartmentEngagementView;
import com.ratana.task_management_api.announcement.repository.projection.MonthlyCountView;
import com.ratana.task_management_api.announcement.repository.projection.MostViewedView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, UUID> {

    Optional<AnnouncementRead> findByAnnouncementIdAndUserId(UUID announcementId, UUID userId);

    /** Feed: read flags for one page of announcements. */
    List<AnnouncementRead> findByUserIdAndAnnouncementIdIn(UUID userId, Collection<UUID> announcementIds);

    /**
     * Upsert, safe under concurrent requests (relies on uq_announcement_read_user);
     * find-then-save would hit the unique constraint on double clicks.
     */
    @Modifying
    @Query(value = """
            INSERT INTO announcement_reads
                   (id, announcement_id, user_id, first_viewed_at, last_viewed_at, view_count, created_at, updated_at)
            VALUES (gen_random_uuid(), :announcementId, :userId, now(), now(), 1, now(), now())
            ON CONFLICT (announcement_id, user_id) DO UPDATE
               SET last_viewed_at = now(),
                   view_count     = announcement_reads.view_count + 1,
                   updated_at     = now()
            """, nativeQuery = true)
    void recordView(@Param("announcementId") UUID announcementId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO announcement_reads
                   (id, announcement_id, user_id, first_viewed_at, last_viewed_at, view_count, read_at, created_at, updated_at)
            VALUES (gen_random_uuid(), :announcementId, :userId, now(), now(), 1, now(), now(), now())
            ON CONFLICT (announcement_id, user_id) DO UPDATE
               SET read_at    = COALESCE(announcement_reads.read_at, now()),
                   updated_at = now()
            """, nativeQuery = true)
    void markRead(@Param("announcementId") UUID announcementId, @Param("userId") UUID userId);

    /** Metrics for a page of announcements in one query. COUNT(readAt) skips nulls. */
    @Query("""
            SELECT r.announcement.id AS announcementId,
                   COALESCE(SUM(r.viewCount), 0) AS views,
                   COUNT(r.readAt) AS readCount
              FROM AnnouncementRead r
             WHERE r.announcement.id IN :ids
             GROUP BY r.announcement.id
            """)
    List<AnnouncementMetrics> findMetrics(@Param("ids") Collection<UUID> ids);

    @Query("SELECT COALESCE(SUM(r.viewCount), 0) FROM AnnouncementRead r")
    long sumAllViews();

    /** Reads on announcements that were sent out (published or since expired/archived). */
    @Query("""
            SELECT COUNT(r) FROM AnnouncementRead r
             WHERE r.readAt IS NOT NULL AND r.announcement.publishedAt IS NOT NULL
            """)
    long countReadsOfSent();

    @Query("""
            SELECT r.announcement.id AS id, r.announcement.title AS title, SUM(r.viewCount) AS views
              FROM AnnouncementRead r
             GROUP BY r.announcement.id, r.announcement.title
             ORDER BY SUM(r.viewCount) DESC
            """)
    List<MostViewedView> findMostViewed(Pageable pageable);

    @EntityGraph(attributePaths = {"announcement"})
    List<AnnouncementRead> findTop10ByUserIdOrderByLastViewedAtDesc(UUID userId);

    /**
     * Read rate per publish month, as a whole-number percentage in "count".
     * Months with no recipients are skipped.
     */
    @Query(value = """
            SELECT x.month AS month,
                   ROUND(100.0 * SUM(x.reads) / SUM(x.recipients))::bigint AS count
              FROM (
                    SELECT to_char(date_trunc('month', a.published_at AT TIME ZONE 'Asia/Phnom_Penh'), 'YYYY-MM') AS month,
                           a.recipient_count AS recipients,
                           COUNT(r.read_at) AS reads
                      FROM announcements a
                      LEFT JOIN announcement_reads r ON r.announcement_id = a.id
                     WHERE a.published_at >= :from
                     GROUP BY a.id, 1, a.recipient_count
                   ) x
             GROUP BY x.month
            HAVING SUM(x.recipients) > 0
             ORDER BY x.month
            """, nativeQuery = true)
    List<MonthlyCountView> readRatePerMonth(@Param("from") Instant from);

    @Query(value = """
            SELECT d.id AS departmentId,
                   d.name AS department,
                   COUNT(DISTINCT u.id) AS members,
                   COUNT(DISTINCT r.user_id) AS readers
              FROM departments d
              JOIN users u ON u.department_id = d.id AND u.is_active = true
              LEFT JOIN announcement_reads r ON r.user_id = u.id AND r.read_at IS NOT NULL
             GROUP BY d.id, d.name
             ORDER BY d.name
            """, nativeQuery = true)
    List<DepartmentEngagementView> departmentEngagement();
}
