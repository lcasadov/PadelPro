package com.padelpro.auth.domain.port.out;

import com.padelpro.auth.domain.model.SystemConfig;

import java.util.Optional;

public interface SystemConfigRepositoryPort {
    Optional<SystemConfig> findById(Long id);

    SystemConfig save(SystemConfig config);
}
