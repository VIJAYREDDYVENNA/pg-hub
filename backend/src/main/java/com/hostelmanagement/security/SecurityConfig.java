package com.hostelmanagement.security;

import com.hostelmanagement.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JsonAuthHandlers jsonAuthHandlers;
    private final AppProperties props;
    private final org.springframework.core.env.Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: CSRF tokens are not needed. The refresh cookie is
                // HttpOnly + SameSite=Strict and scoped to /api/auth, so cross-site requests cannot use it.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentTypeOptions(c -> { })
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .exceptionHandling(e -> e.authenticationEntryPoint(jsonAuthHandlers).accessDeniedHandler(jsonAuthHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/whatsapp/webhook").permitAll()
                        // Resident pay links (capability-token URL) and gateway webhooks (signature-verified)
                        .requestMatchers("/api/public/pay/**", "/api/payments/webhook/*").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))))
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
