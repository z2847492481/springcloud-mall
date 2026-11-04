package com.mall.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.enums.ErrorCode;
import com.mall.common.result.Result;
import com.mall.gateway.config.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter redisRateLimiter;
    private final KeyResolver userKeyResolver;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : exchange.getRequest().getPath().value();

        // Nacos 配置刷新后，把最新阈值写入该路由的限流配置，下次请求立即生效
        redisRateLimiter.getConfig().put(routeId, new RedisRateLimiter.Config()
                .setReplenishRate(rateLimitProperties.getReplenishRate())
                .setBurstCapacity(rateLimitProperties.getBurstCapacity())
                .setRequestedTokens(rateLimitProperties.getRequestedTokens()));

        return userKeyResolver.resolve(exchange)
                .switchIfEmpty(Mono.just("unknown"))
                .flatMap(key -> redisRateLimiter.isAllowed(routeId, key)
                        .flatMap(response -> {
                            response.getHeaders().forEach((name, value) ->
                                    exchange.getResponse().getHeaders().add(name, value));
                            if (response.isAllowed()) {
                                return chain.filter(exchange);
                            }
                            log.warn("触发限流, routeId={}, key={}", routeId, key);
                            return tooManyRequests(exchange);
                        }));
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.fail(ErrorCode.TOO_MANY_REQUESTS));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            log.error("写入 429 响应失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 鉴权之后、路由转发之前执行
        return -50;
    }
}
