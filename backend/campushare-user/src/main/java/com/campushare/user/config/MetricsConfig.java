package com.campushare.user.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter registerCounter(MeterRegistry registry) {
        return Counter.builder("campushare.register.total")
                .description("Total number of user registrations")
                .tag("type", "register")
                .register(registry);
    }

    @Bean
    public Counter loginCounter(MeterRegistry registry) {
        return Counter.builder("campushare.login.total")
                .description("Total number of user logins")
                .tag("type", "login")
                .register(registry);
    }

    @Bean
    public Counter postCreateCounter(MeterRegistry registry) {
        return Counter.builder("campushare.post.create.total")
                .description("Total number of posts created")
                .tag("type", "post")
                .register(registry);
    }

    @Bean
    public Counter fileUploadCounter(MeterRegistry registry) {
        return Counter.builder("campushare.file.upload.total")
                .description("Total number of file uploads")
                .tag("type", "file")
                .register(registry);
    }

    @Bean
    public DistributionSummary fileUploadSizeSummary(MeterRegistry registry) {
        return DistributionSummary.builder("campushare.file.upload.size")
                .description("File upload size distribution")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.75, 0.9, 0.95, 0.99)
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
