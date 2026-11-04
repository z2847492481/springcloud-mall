package com.mall.user.config;

import com.mall.common.jwt.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class JwtConfig {

    @Bean
    @RefreshScope
    public JwtUtil jwtUtil(@Value("${mall.jwt.secret}") String secret,
                           @Value("${mall.jwt.expire-minutes:120}") long expireMinutes) {
        log.info("初始化 JwtUtil, expireMinutes={}", expireMinutes);
        return new JwtUtil(secret, expireMinutes);
    }
}
