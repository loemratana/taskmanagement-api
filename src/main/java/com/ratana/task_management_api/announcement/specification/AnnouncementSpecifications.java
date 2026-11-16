package com.ratana.task_management_api.announcement.specification;

import com.ratana.task_management_api.announcement.dto.request.AnnouncementFilterRequest;
import com.ratana.task_management_api.announcement.entity.Announcement;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class AnnouncementSpecifications {

    private AnnouncementSpecifications() {
    }

    public static Specification<Announcement> fromFilter(AnnouncementFilterRequest f) {
        return (root, query, cb) -> {
            if (f == null) {
                return cb.conjunction();
            }
            List<Predicate> p = new ArrayList<>();
            if (f.getStatus() != null) {
                p.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getType() != null) {
                p.add(cb.equal(root.get("type"), f.getType()));
            }
            if (f.getPriority() != null) {
                p.add(cb.equal(root.get("priority"), f.getPriority()));
            }
            if (f.getAudienceType() != null) {
                p.add(cb.equal(root.get("audienceType"), f.getAudienceType()));
            }
            if (f.getPinned() != null) {
                p.add(cb.equal(root.get("pinned"), f.getPinned()));
            }
            if (f.getCreatedById() != null) {
                p.add(cb.equal(root.get("createdBy").get("id"), f.getCreatedById()));
            }
            if (f.getFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("publishAt"), f.getFrom()));
            }
            if (f.getTo() != null) {
                p.add(cb.lessThan(root.get("publishAt"), f.getTo()));
            }
            if (StringUtils.hasText(f.getSearch())) {
                String like = "%" + f.getSearch().trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("summary")), like)
                ));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
