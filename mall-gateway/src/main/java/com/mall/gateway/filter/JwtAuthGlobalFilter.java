package com.mall.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.constant.CommonConstants;
import com.mall.common.enums.ErrorCode;
import com.mall.common.jwt.JwtUtil;
import com.mall.common.result.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 白名单：登录接口、商品浏览接口、网关自身健康检查。
     */
    private static final Set<String> WHITELIST = Set.of("/api/user/login");
    private static final List<String> WHITELIST_PREFIXES = List.of("/api/product/", "/actuator/");

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (request.getMethod() == HttpMethod.OPTIONS || isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "缺少 Authorization: Bearer token");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败, path={}, reason={}", path, e.getMessage());
            return unauthorized(exchange, "token 无效或已过期");
        }

        String userId = claims.getSubject();
        String username = claims.get("username", String.class);
        log.info("JWT 鉴权通过, userId={}, username={}, path={}", userId, username, path);

        // 先删除客户端自带的 X-User-Id，防止伪造，再写入网关解析出的用户身份
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(CommonConstants.HEADER_USER_ID);
                    headers.set(CommonConstants.HEADER_USER_ID, userId);
                    if (username != null) {
                        headers.set("X-Username", username);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.contains(path)
                || WHITELIST_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(
                    Result.fail(ErrorCode.UNAUTHORIZED.getCode(), message));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            log.error("写入 401 响应失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 早于路由过滤器执行，鉴权通过后再进入转发
        return -100;
    }
}
