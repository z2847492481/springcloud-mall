package com.mall.gateway.config;

import com.mall.common.constant.CommonConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * 已登录用户按 userId 限流，未登录（登录接口、商品浏览）退化为按 IP 限流。
     * JwtAuthGlobalFilter 的 order 更靠前，这里能拿到 X-User-Id。
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            return Mono.just("ip:" + ip);
        };
    }
}
