package com.mall.gateway.config;

import com.mall.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    @RefreshScope
    public JwtUtil jwtUtil(@Value("${mall.jwt.secret}") String secret,
                           @Value("${mall.jwt.expire-minutes:120}") long expireMinutes) {
        return new JwtUtil(secret, expireMinutes);
    }
}
