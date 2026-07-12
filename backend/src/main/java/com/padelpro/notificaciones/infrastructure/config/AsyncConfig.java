package com.padelpro.notificaciones.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables asynchronous method execution for the {@code notificaciones} capability (D4).
 *
 * <p>The welcome email is sent on this executor ({@code @Async} on
 * {@link com.padelpro.notificaciones.infrastructure.email.SmtpNotificationAdapter}) so a slow or
 * failing SMTP server never blocks account activation/creation. Since #199 the whole event→email
 * pipeline ({@code NotificationEventListener} handlers) also runs here, keeping the owner lookup and
 * template building off the request/webhook thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Small bounded pool — email/notification volume is low and best-effort.
     *
     * <p>Rejection policy is {@link ThreadPoolExecutor.CallerRunsPolicy}: if all threads are busy and
     * the 50-slot queue is full, the task runs inline on the submitting thread instead of the default
     * {@code AbortPolicy} throwing a {@code TaskRejectedException}. Because the notification handlers
     * are submitted from the post-commit phase of a reservation/payment transaction, an abort would
     * surface an exception back on that thread; caller-runs degrades gracefully to synchronous
     * execution under saturation while never dropping a notification.
     */
    @Bean(name = "notificationsTaskExecutor")
    public Executor notificationsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
