-- cleanup.sql — executed @BeforeEach in integration tests to isolate state
-- Order respects FK constraints: child tables before parent tables

DELETE FROM audit_log;
DELETE FROM refresh_tokens;
DELETE FROM users;

-- Reset sequences so IDs start at 1 in each test
ALTER SEQUENCE IF EXISTS users_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS audit_log_id_seq RESTART WITH 1;
ALTER SEQUENCE IF EXISTS refresh_tokens_id_seq RESTART WITH 1;
