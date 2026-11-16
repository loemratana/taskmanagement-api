package com.ratana.task_management_api.announcement.repository;

import com.ratana.task_management_api.announcement.enums.AnnouncementAudience;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.entity.Announcement;
import com.ratana.task_management_api.announcement.repository.projection.MonthlyCountView;
import com.ratana.task_management_api.announcement.repository.projection.TypeCountView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID>, JpaSpecificationExecutor<Announcement> {

    /** Detail / edit: creator and every audience target in one go. */
    @EntityGraph(attributePaths = {
            "createdBy",
            "targetDepartments",
            "targetTeamManagers",
            "targetUsers"
    })
    @Query("SELECT a FROM Announcement a WHERE a.id = :id")
    Optional<Announcement> findWithDetailsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {
            "createdBy",
            "targetDepartments",
            "targetTeamManagers",
            "targetUsers"
    })
    @Query("SELECT a FROM Announcement a WHERE a.id IN :ids")
    List<Announcement> findWithDetailsByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Admin list. To-one joins only: fetching collections with paging makes Hibernate
     * paginate in memory. Audience sets load in batches (@BatchSize on the entity).
     */
    @Override
    @EntityGraph(attributePaths = {"createdBy"})
    Page<Announcement> findAll(Specification<Announcement> spec, Pageable pageable);

    // ---- Employee feed ----

    String FEED_WHERE = """
            WHERE a.status = :published
              AND (a.endAt IS NULL OR a.endAt > :now)
              AND (
                    a.audienceType = :everyone
                 OR (a.audienceType = :department AND EXISTS (
                        SELECT 1 FROM Announcement a2 JOIN a2.targetDepartments d
                        WHERE a2 = a AND d.id = :departmentId))
                 OR (a.audienceType = :team AND EXISTS (
                        SELECT 1 FROM Announcement a3 JOIN a3.targetTeamManagers m
                        WHERE a3 = a AND m.id = :managerId))
                 OR (a.audienceType = :selected AND EXISTS (
                        SELECT 1 FROM Announcement a4 JOIN a4.targetUsers u
                        WHERE a4 = a AND u.id = :userId))
              )
            """;

    @EntityGraph(attributePaths = {"createdBy"})
    @Query(
            value = "SELECT a FROM Announcement a " + FEED_WHERE + " ORDER BY a.pinned DESC, a.publishedAt DESC",
            countQuery = "SELECT COUNT(a) FROM Announcement a " + FEED_WHERE
    )
    Page<Announcement> findFeed(@Param("userId") UUID userId,
                                @Param("departmentId") UUID departmentId,
                                @Param("managerId") UUID managerId,
                                @Param("now") Instant now,
                                @Param("published") AnnouncementStatus published,
                                @Param("everyone") AnnouncementAudience everyone,
                                @Param("department") AnnouncementAudience department,
                                @Param("team") AnnouncementAudience team,
                                @Param("selected") AnnouncementAudience selected,
                                Pageable pageable);

    default Page<Announcement> findFeed(UUID userId, UUID departmentId, UUID managerId, Instant now, Pageable pageable) {
        return findFeed(userId, departmentId, managerId, now,
                AnnouncementStatus.PUBLISHED,
                AnnouncementAudience.EVERYONE,
                AnnouncementAudience.DEPARTMENT,
                AnnouncementAudience.TEAM,
                AnnouncementAudience.SELECTED_EMPLOYEES,
                pageable);
    }

    @Query("SELECT COUNT(a) > 0 FROM Announcement a " + FEED_WHERE + " AND a.id = :id")
    boolean isVisibleTo(@Param("id") UUID id,
                        @Param("userId") UUID userId,
                        @Param("departmentId") UUID departmentId,
                        @Param("managerId") UUID managerId,
                        @Param("now") Instant now,
                        @Param("published") AnnouncementStatus published,
                        @Param("everyone") AnnouncementAudience everyone,
                        @Param("department") AnnouncementAudience department,
                        @Param("team") AnnouncementAudience team,
                        @Param("selected") AnnouncementAudience selected);

    default boolean isVisibleTo(UUID id, UUID userId, UUID departmentId, UUID managerId, Instant now) {
        return isVisibleTo(id, userId, departmentId, managerId, now,
                AnnouncementStatus.PUBLISHED,
                AnnouncementAudience.EVERYONE,
                AnnouncementAudience.DEPARTMENT,
                AnnouncementAudience.TEAM,
                AnnouncementAudience.SELECTED_EMPLOYEES);
    }

    // ---- Scheduler ----

    @Query("SELECT a.id FROM Announcement a WHERE a.status = :status AND a.publishAt <= :now")
    List<UUID> findIdsDueForPublish(@Param("status") AnnouncementStatus status, @Param("now") Instant now);

    /** Bulk update bypasses @Version and @UpdateTimestamp, so bump them by hand. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Announcement a
               SET a.status = :expired, a.version = a.version + 1, a.updatedAt = :now
             WHERE a.status = :published AND a.endAt IS NOT NULL AND a.endAt <= :now
            """)
    int expirePublished(@Param("now") Instant now,
                        @Param("published") AnnouncementStatus published,
                        @Param("expired") AnnouncementStatus expired);

    /**
     * Bulk delete. The database cascades to the audience join tables and reads
     * (ON DELETE CASCADE in V12), so Hibernate never loads the read rows.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Announcement a WHERE a.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);

    // ---- Stats / analytics ----

    long countByStatus(AnnouncementStatus status);

    long countByPinnedTrueAndStatus(AnnouncementStatus status);

    @Query("SELECT COALESCE(SUM(a.recipientCount), 0) FROM Announcement a WHERE a.status IN :statuses")
    long sumRecipients(@Param("statuses") Collection<AnnouncementStatus> statuses);

    @Query("SELECT a.type AS type, COUNT(a) AS count FROM Announcement a GROUP BY a.type")
    List<TypeCountView> countByType();

    @Query(value = """
            SELECT to_char(date_trunc('month', published_at AT TIME ZONE 'Asia/Phnom_Penh'), 'YYYY-MM') AS month,
                   COUNT(*) AS count
              FROM announcements
             WHERE published_at >= :from
             GROUP BY 1
             ORDER BY 1
            """, nativeQuery = true)
    List<MonthlyCountView> countPublishedPerMonth(@Param("from") Instant from);

    @EntityGraph(attributePaths = {"createdBy"})
    List<Announcement> findTop5ByStatusOrderByPublishedAtDesc(AnnouncementStatus status);

    @EntityGraph(attributePaths = {"createdBy"})
    List<Announcement> findTop5ByStatusOrderByPublishAtAsc(AnnouncementStatus status);
}
