-- Migration: notification_log (capability notificaciones, change notificaciones-eventos-email / #197)
-- Source of truth: openspec/changes/notificaciones-eventos-email/specs/notificaciones/spec.md (Req
-- "Registro de notificaciones en notification_log") + design.md D2.
--
-- Audit record of every transactional notification delivery (email of reservation CONFIRMED /
-- CANCELLED and payment PAID). Each attempt is registered PENDING then updated to SENT or FAILED.
-- RN-RGPD-04: message/recipient never carry passwords, tokens, OTP or card data — enforced by the
-- application layer, not the schema.
--
-- type/status are VARCHAR + CHECK (not native PG enums) so the JPA EnumType.STRING mapping works on
-- both PostgreSQL and H2 without the NAMED_ENUM cast used by the reservation/payment enums.

CREATE TABLE notification_log (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    user_id              BIGINT,
    type                 VARCHAR(20)   NOT NULL,
    recipient            VARCHAR(255)  NOT NULL,
    subject              VARCHAR(255)  NOT NULL,
    message              TEXT          NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    error_message        TEXT,
    related_entity_type  VARCHAR(50),
    related_entity_id    VARCHAR(64),
    attempts             INTEGER       NOT NULL DEFAULT 0,
    sent_at              TIMESTAMPTZ,
    last_attempt_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT notification_log_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_notif_type   CHECK (type IN ('EMAIL')),
    CONSTRAINT chk_notif_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notif_attempts CHECK (attempts >= 0)
);

-- Index for the retry job: it scans FAILED entries below the attempt cap (Req 3).
CREATE INDEX idx_notif_status ON notification_log (status);

-- Index for looking up the notifications emitted for a given reservation/payment (ADMIN inspection).
CREATE INDEX idx_notif_related_entity ON notification_log (related_entity_type, related_entity_id);
