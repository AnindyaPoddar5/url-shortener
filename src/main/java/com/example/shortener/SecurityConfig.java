package com.example.shortener;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class SecurityConfig {
    @Bean UserDetailsService users() { return new InMemoryUserDetailsManager(); }
    @Bean SecurityFilterChain security(HttpSecurity http, Clock clock,
            @Value("${app.api-key}") String key, @Value("${app.requests-per-minute}") int limit) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // Header API key, no cookies/session authentication.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                    .requestMatchers("/api/**", "/actuator/health", "/error").permitAll()
                    .requestMatchers(HttpMethod.GET, "/s/**").permitAll()
                    .requestMatchers(HttpMethod.HEAD, "/s/**").permitAll()
                    .anyRequest().denyAll())
                .addFilterBefore(new RequestGuard(key, limit, clock), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
