package com.padelpro.notificaciones.infrastructure.schedule;

import com.padelpro.notificaciones.application.service.EmailNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationRetryJob} (task 4.2): the scheduled entry point delegates to the
 * service's retry logic and never lets an exception escape (which would kill the scheduler thread).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRetryJob — scheduled delegation, exception-safe")
class NotificationRetryJobTest {

    @Mock private EmailNotificationService emailNotificationService;

    @Test
    @DisplayName("4.2 delegates the cycle to EmailNotificationService.retryFailed()")
    void delegates_to_service() {
        when(emailNotificationService.retryFailed()).thenReturn(2);
        NotificationRetryJob job = new NotificationRetryJob(emailNotificationService);

        job.retryFailedNotifications();

        verify(emailNotificationService).retryFailed();
    }

    @Test
    @DisplayName("4.2 a failure inside the cycle never propagates out of the scheduled method")
    void swallows_exceptions() {
        when(emailNotificationService.retryFailed())
                .thenThrow(new RuntimeException("unexpected"));
        NotificationRetryJob job = new NotificationRetryJob(emailNotificationService);

        assertThatCode(job::retryFailedNotifications).doesNotThrowAnyException();
    }
}
