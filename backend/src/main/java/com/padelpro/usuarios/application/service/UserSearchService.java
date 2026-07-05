package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.usuarios.application.dto.UserSearchResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service backing the registered-partner search (D5).
 *
 * <p>Returns a bounded list of {@link UserSearchResult} (id + display name only) for a name/email
 * term. Two guardrails keep it from leaking the member directory (RN-RGPD): a minimum term length
 * (short terms short-circuit to an empty list without touching the DB) and a hard result cap.
 *
 * <p>RN-RGPD: the search term is never logged (it may contain a personal name/email), and the
 * projection deliberately excludes email, password hash, role and status.
 */
@Service
public class UserSearchService {

    /** Terms shorter than this never hit the database — avoids dumping the directory on `a`, `` … */
    static final int MIN_TERM_LENGTH = 2;

    /** Hard cap on returned rows regardless of how many users match. */
    static final int MAX_RESULTS = 10;

    private final UserRepositoryPort userRepositoryPort;

    public UserSearchService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    /**
     * Search ACTIVE users by name or email.
     *
     * @param rawTerm the raw query term from the request (may be null/blank)
     * @return up to {@link #MAX_RESULTS} matches (id + display name); empty list if the term is too
     *         short or nothing matches
     */
    @Transactional(readOnly = true)
    public List<UserSearchResult> search(String rawTerm) {
        if (rawTerm == null) {
            return List.of();
        }
        String term = rawTerm.trim();
        if (term.length() < MIN_TERM_LENGTH) {
            return List.of();
        }

        Pageable limit = PageRequest.of(0, MAX_RESULTS);
        return userRepositoryPort.searchByNameOrEmail(escapeLike(term), limit).stream()
                .map(UserSearchService::toResult)
                .toList();
    }

    private static UserSearchResult toResult(User user) {
        String nombre = (user.getFirstName() + " " + user.getLastName()).trim();
        return new UserSearchResult(user.getId(), nombre);
    }

    /**
     * Escape the LIKE wildcards ({@code %}, {@code _}) and the escape char itself ({@code \}) so a
     * caller cannot pass {@code %} to match every user and dump the directory. Relies on PostgreSQL's
     * default backslash escape character for LIKE.
     */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
