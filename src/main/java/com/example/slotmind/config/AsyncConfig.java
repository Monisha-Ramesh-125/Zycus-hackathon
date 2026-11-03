package com.example.slotmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** A bounded pool, so a burst of status changes cannot exhaust the server. */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "rescheduleExecutor")
    public ThreadPoolTaskExecutor rescheduleExecutor(@Value("${slotmind.async.core-pool-size:2}") int core,
                                                    @Value("${slotmind.async.max-pool-size:4}") int max,
                                                    @Value("${slotmind.async.queue-capacity:500}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix("reschedule-");
        executor.initialize();
        return executor;
    }
}
