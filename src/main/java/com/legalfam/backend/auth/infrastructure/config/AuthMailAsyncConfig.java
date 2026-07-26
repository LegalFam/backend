package com.legalfam.backend.auth.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @EnableAsync is already active application-wide via the chat module's AsyncConfig,
 * so only the executor bean is declared here.
 */
@Configuration
public class AuthMailAsyncConfig {

    @Bean(name = "authMailTaskExecutor")
    public Executor authMailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("auth-mail-");
        executor.initialize();
        return executor;
    }
}
