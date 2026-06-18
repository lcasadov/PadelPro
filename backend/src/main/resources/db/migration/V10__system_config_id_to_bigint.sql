-- Migration: Promote system_config.id from INTEGER to BIGINT (#159)
--
-- V6 created system_config.id as INTEGER, but the JPA entity
-- com.padelpro.auth.domain.model.SystemConfig maps id as java.lang.Long.
-- With ddl-auto=validate on PostgreSQL, Hibernate expects BIGINT and the
-- application context fails to start ("wrong column type ... found: int4,
-- but expecting: bigint").
--
-- V6 is already applied in existing environments, so editing it would break the
-- Flyway checksum. Instead we alter the column type here. system_config is a
-- singleton (id = 1) with no incoming foreign keys to its id, so the type
-- promotion is safe and lossless. The CHECK (id = 1) constraint is preserved by
-- ALTER COLUMN TYPE.

ALTER TABLE system_config
    ALTER COLUMN id TYPE BIGINT;
