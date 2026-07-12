package com.padelpro.reservas.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage unit tests for the {@link IdempotencyKey} entity (change backend-branch-coverage,
 * D1/D2). Covers the {@code onCreate} timestamp guard and the identity contract. Same package so the
 * JPA-protected {@code onCreate} is reachable.
 */
@DisplayName("Unit — IdempotencyKey (domain)")
class IdempotencyKeyTest {

    private static final UUID RES = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    private static IdempotencyKey key() {
        return new IdempotencyKey("idem-123", 7L, RES);
    }

    private static void setId(IdempotencyKey k, Long id) {
        try {
            var f = IdempotencyKey.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(k, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("constructor exposes the key fields")
    void constructor_fields() {
        IdempotencyKey k = key();
        assertThat(k.getIdemKey()).isEqualTo("idem-123");
        assertThat(k.getUserId()).isEqualTo(7L);
        assertThat(k.getReservationId()).isEqualTo(RES);
    }

    @Test
    @DisplayName("onCreate stamps createdAt only when unset (both branches)")
    void onCreate_stamps_when_unset() {
        IdempotencyKey k = key();
        k.onCreate();
        var createdAt = k.getCreatedAt();
        assertThat(createdAt).isNotNull();

        k.onCreate(); // already set → false branch, no overwrite
        assertThat(k.getCreatedAt()).isEqualTo(createdAt);
    }

    @Nested
    @DisplayName("equals / hashCode identity contract")
    class Identity {

        @Test
        @DisplayName("reflexive")
        void reflexive() {
            IdempotencyKey k = key();
            setId(k, 1L);
            assertThat(k.equals(k)).isTrue();
        }

        @Test
        @DisplayName("transient (null id) never equal; hashCode falls back to identity")
        void transient_not_equal() {
            IdempotencyKey a = key();
            IdempotencyKey b = key();
            setId(b, 1L);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a));
        }

        @Test
        @DisplayName("not equal to null nor to a different type")
        void not_equal_null_or_other_type() {
            IdempotencyKey k = key();
            setId(k, 1L);
            assertThat(k.equals(null)).isFalse();
            assertThat(k.equals("x")).isFalse();
        }

        @Test
        @DisplayName("same id equal + same hashCode; different id not equal")
        void by_id() {
            IdempotencyKey a = key();
            IdempotencyKey b = key();
            IdempotencyKey c = key();
            setId(a, 1L);
            setId(b, 1L);
            setId(c, 2L);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }
}
