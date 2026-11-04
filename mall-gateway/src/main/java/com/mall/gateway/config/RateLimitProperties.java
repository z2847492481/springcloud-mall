package com.mall.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "mall.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int replenishRate = 10;
    private int burstCapacity = 20;
    private int requestedTokens = 1;
}
