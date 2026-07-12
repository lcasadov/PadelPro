package com.padelpro.otp.infrastructure.persistence;

import com.padelpro.otp.domain.model.OtpCode;
import com.padelpro.otp.domain.model.OtpType;
import com.padelpro.otp.domain.port.out.OtpCodeRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Invalidate all active OTP codes of a user in one UPDATE ({@code used = true}). Used by the RGPD
     * anonymization flow (RN-RGPD-07). Idempotent — updates 0 rows when none are active.
     */
    @Override
    @Transactional
    @Modifying
    @Query("UPDATE OtpCode o SET o.used = true WHERE o.userId = :userId AND o.used = false")
    void invalidateAllActiveByUserId(@Param("userId") Long userId);
}
