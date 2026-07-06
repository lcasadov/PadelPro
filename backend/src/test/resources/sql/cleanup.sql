-- cleanup.sql — executed @BeforeEach in integration tests to isolate state
-- Order respects FK constraints: child tables before parent tables

-- notificaciones capability (#197): notification_log has a nullable FK to users (ON DELETE SET NULL)
DELETE FROM notification_log;

-- reservas capability (US-007 / #14): child rows before reservations, reservations before users
DELETE FROM idempotency_keys;
DELETE FROM payments;
DELETE FROM participants;
DELETE FROM reservations;

DELETE FROM audit_log;
DELETE FROM refresh_tokens;
DELETE FROM users;

-- Reset sequences so IDs start at 1 in each test
ALTER SEQUENCE IF EXISTS users_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS audit_log_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS refresh_tokens_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS idempotency_keys_id_seq RESTART WITH 1;

-- Restore the singleton system_config (id=1) to its Flyway-seeded defaults so a test that mutates
-- pricing / pista state / deadline does not leak into the next test (US-024 tests rely on this).
UPDATE system_config
   SET club_name                   = 'Mi Club de Pádel',
       club_description            = 'Club profesional de pádel',
       pista_state                 = 'ACTIVA',
       payment_gateway             = 'CASH',
       max_participants_per_pista  = 4,
       price_per_hour              = 15.00,
       cancellation_deadline_hours = 2,
       updated_by_user_id          = NULL
 WHERE id = 1;
