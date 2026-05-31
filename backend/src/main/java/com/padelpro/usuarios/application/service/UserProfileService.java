package com.padelpro.usuarios.application.service;

import com.padelpro.auth.domain.model.User;
import com.padelpro.auth.domain.port.out.UserRepositoryPort;
import com.padelpro.usuarios.application.dto.UpdateMyProfileCommand;
import com.padelpro.usuarios.application.dto.UserProfileResponse;
import com.padelpro.usuarios.domain.exception.EmailConflictException;
import com.padelpro.usuarios.domain.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for self-profile operations (GET/PATCH /api/usuarios/me).
 *
 * <p>Users may update firstName, lastName, email, and phone.
 * Role and status are read-only from the user's perspective.
 */
@Service
public class UserProfileService {

    private final UserRepositoryPort userRepositoryPort;

    public UserProfileService(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = userRepositoryPort;
    }

    /**
     * Return the profile of the authenticated user.
     *
     * @param userId the id extracted from the JWT
     * @throws UserNotFoundException if no user with this id exists
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = requireUser(userId);
        return toProfileResponse(user);
    }

    /**
     * Apply a partial update to the authenticated user's own profile.
     *
     * <p>Only non-null fields in the command are applied.
     * Role and status cannot be changed via this operation.
     *
     * @param userId the id extracted from the JWT
     * @param cmd    the partial update command
     * @throws UserNotFoundException  if no user with this id exists
     * @throws EmailConflictException if the requested email is already used by another user
     */
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UpdateMyProfileCommand cmd) {
        User user = requireUser(userId);

        if (cmd.email() != null && !cmd.email().equals(user.getEmail())) {
            if (userRepositoryPort.existsByEmailAndIdNot(cmd.email(), userId)) {
                throw new EmailConflictException();
            }
            user.setEmail(cmd.email());
        }
        if (cmd.firstName() != null) user.setFirstName(cmd.firstName());
        if (cmd.lastName()  != null) user.setLastName(cmd.lastName());
        if (cmd.phone()     != null) user.setPhone(cmd.phone());

        User saved = userRepositoryPort.save(user);
        return toProfileResponse(saved);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private User requireUser(Long userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    static UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getLogin(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus().name(),
                user.getRole().name(),
                user.getTelegramChatId() != null
        );
    }
}
