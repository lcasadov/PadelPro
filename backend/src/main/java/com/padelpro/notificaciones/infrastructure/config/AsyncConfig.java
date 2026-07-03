package com.padelpro.notificaciones.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables asynchronous method execution for the {@code notificaciones} capability (D4).
 *
 * <p>The welcome email is sent on this executor ({@code @Async} on
 * {@link com.padelpro.notificaciones.infrastructure.email.SmtpNotificationAdapter}) so a slow or
 * failing SMTP server never blocks account activation/creation.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Small bounded pool — email volume is low and best-effort. */
    @Bean(name = "notificationsTaskExecutor")
    public Executor notificationsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("notif-");
        executor.initialize();
        return executor;
    }
}
