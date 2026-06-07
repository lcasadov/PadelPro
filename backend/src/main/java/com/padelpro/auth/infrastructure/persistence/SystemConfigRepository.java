package com.padelpro.auth.infrastructure.persistence;

import com.padelpro.auth.domain.model.SystemConfig;
import com.padelpro.auth.domain.port.out.SystemConfigRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long>, SystemConfigRepositoryPort {
    // Extends both Spring Data interface and our domain port
    // Automatically implements findById() and save()
}
