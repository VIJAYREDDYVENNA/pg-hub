package com.hostelmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /** Small, bounded pool for outgoing mail: a slow SMTP server can delay mail but never exhaust request threads. */
    @Bean(name = "mailExecutor")
    public ThreadPoolTaskExecutor mailExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("mail-");
        // When saturated, drop the oldest queued work rather than block the caller
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(15);
        return ex;
    }
}
