package com.padelpro.otp.infrastructure.persistence;

import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA adapter for {@link OtpCode}, implementing {@link OtpCodeRepositoryPort} so the
 * application layer depends on the domain port (hexagonal, mirrors {@code UserRepository}).
 *
 * <p>{@code save(OtpCode)} coincides with {@code JpaRepository.save} under type erasure, so Spring
 * Data supplies it; the derived queries below satisfy the remaining port methods.
 */
@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long>, OtpCodeRepositoryPort {

    @Override
    List<OtpCode> findByUserIdAndTypeAndUsedFalseOrderByCreatedAtDesc(Long userId, OtpType type);

    @Override
    List<OtpCode> findByUserIdAndUsedFalse(Long userId);

    @Override
    List<OtpCode> findByCodeHashAndTypeAndUsedFalse(String codeHash, OtpType type);
}
