package com.mall.order.config;

import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class FeignCircuitBreakerConfig {

    /**
     * CircuitBreaker 默认 TimeLimiter 只有 1s，会把 Feign 配置的 5s readTimeout 提前打断，
     * 这里放宽到 6s，让超时策略以 Feign 配置为准。
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultTimeLimiterCustomizer() {
        TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(6))
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(timeLimiterConfig)
                .build());
    }
}
