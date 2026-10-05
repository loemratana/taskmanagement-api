package com.ratana.task_management_api.utility;

import com.ratana.task_management_api.dto.request.UserFilterRequest;
import com.ratana.task_management_api.model.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public  static Specification<User> fromFilter(UserFilterRequest filter) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                String like = "%" + filter.getKeyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("fullName")), like)
                ));
            }
            if (filter.getDepartmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), filter.getPositionId()));
            }
            if (filter.getPositionId() != null) {
                predicates.add(cb.equal(root.get("position").get("id"), filter.getPositionId()));
            }
            if (filter.getManagerId() != null) {
                predicates.add(cb.equal(root.get("manager").get("id"), filter.getManagerId()));
            }
            if (filter.getUserStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getUserStatus()));
            }
            if (filter.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), filter.getActive()));
            }
            if (filter.getRoleId() != null) {
                predicates.add(cb.equal(root.get("role").get("id"), filter.getRoleId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

    }
}
