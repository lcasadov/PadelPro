package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.application.dto.AuditLogFilter;
import com.padelpro.auth.domain.model.AuditLog;
import com.padelpro.auth.domain.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification factory for {@link AuditLog} queries.
 *
 * <p>All predicates are null-safe — a null filter field produces no restriction
 * on that dimension. Combined with AND logic.
 */
public final class AuditLogSpecification {

    private AuditLogSpecification() {
        // Utility class
    }

    /**
     * Build a {@link Specification} from the given filter.
     *
     * @param filter nullable filter criteria
     * @return a Specification that applies all non-null filter fields
     */
    public static Specification<AuditLog> buildSpec(AuditLogFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter == null) {
                return cb.conjunction();
            }

            if (filter.action() != null && !filter.action().isBlank()) {
                predicates.add(cb.equal(root.get("action"), filter.action()));
            }

            if (filter.userId() != null) {
                Join<AuditLog, User> userJoin = root.join("user", JoinType.INNER);
                predicates.add(cb.equal(userJoin.get("id"), filter.userId()));
            }

            if (filter.entityType() != null && !filter.entityType().isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), filter.entityType()));
            }

            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }

            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
