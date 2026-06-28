package com.campushare.post.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter postCreateCounter(MeterRegistry registry) {
        return Counter.builder("campushare.post.create.total")
                .description("Total number of posts created")
                .tag("type", "post")
                .register(registry);
    }

    @Bean
    public Timer postCreateTimer(MeterRegistry registry) {
        return Timer.builder("campushare.post.create.duration")
                .description("Time taken to create a post")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
                .register(registry);
    }
}
