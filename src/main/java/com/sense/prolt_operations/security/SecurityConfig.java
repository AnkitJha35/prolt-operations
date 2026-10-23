package com.sense.prolt_operations.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthRateLimiterFilter authRateLimiterFilter;
    private final UploadRateLimiterFilter uploadRateLimiterFilter;
    private final RestAuthenticationEntryPoint authEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            AuthRateLimiterFilter authRateLimiterFilter,
            UploadRateLimiterFilter uploadRateLimiterFilter,
            RestAuthenticationEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authRateLimiterFilter = authRateLimiterFilter;
        this.uploadRateLimiterFilter = uploadRateLimiterFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https:; img-src 'self' data: validator.swagger.io https:")
                )
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // Public health & docs
                .requestMatchers("/health", "/health/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                // Public auth endpoints
                .requestMatchers("/api/auth/login", "/api/auth/send-otp", "/api/auth/verify-otp").permitAll()

                // Public functional endpoints (exact parity with Node)
                .requestMatchers("/api/dashboard/**").permitAll()
                .requestMatchers("/api/resource-center/**").permitAll()
                .requestMatchers("/api/database/**").permitAll()
                .requestMatchers("/api/logs/**").permitAll()
                .requestMatchers("/api/market-operations/**").permitAll()
                .requestMatchers("/api/forecast/**", "/forecast/**").permitAll()
                .requestMatchers("/api/proposals/**").permitAll()
                .requestMatchers("/api/trader-performance/**", "/api/trader-performance-daily/**").permitAll()
                .requestMatchers("/api/up-market/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/scraper/import").permitAll()

                // Protected scraper sync endpoint
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/scraper/sync").authenticated()

                // Protected user management (Super Admin only)
                .requestMatchers("/api/users/**").hasRole("SUPER_ADMIN")

                // Protected business modules (exact parity with Node)
                .requestMatchers("/api/datasets/**").authenticated()
                .requestMatchers("/api/upload-history/**").authenticated()
                .requestMatchers("/api/landing-price-calc/**").authenticated()
                .requestMatchers("/api/savings-calculator/**", "/api/savings-calculator-new/**").authenticated()
                .requestMatchers("/api/uploads/**").authenticated()
                .requestMatchers("/api/auth/me").authenticated()

                // Fallback
                .anyRequest().authenticated()
            )
            .addFilterBefore(authRateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(uploadRateLimiterFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
