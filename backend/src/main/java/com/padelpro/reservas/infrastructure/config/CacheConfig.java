package com.padelpro.reservas.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Enables Spring caching and configures the Caffeine cache manager (data-model §7.3).
 *
 * <p>The {@code available-slots} cache keeps availability results for 30 s keyed by date; the
 * short TTL bounds inconsistency while the cache absorbs the high read frequency. Eviction on write
 * is provided by {@code DisponibilidadService.invalidate(LocalDate)} (the reusable hook for Wave 3).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Logical name of the availability cache. */
    public static final String AVAILABLE_SLOTS = "available-slots";

    /** Availability cache TTL (D6). */
    public static final Duration AVAILABLE_SLOTS_TTL = Duration.ofSeconds(30);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(AVAILABLE_SLOTS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(AVAILABLE_SLOTS_TTL)
                .maximumSize(1_000));
        return manager;
    }
}
