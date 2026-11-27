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
    private final com.sense.prolt_operations.security.cors.CorsConfigValidator corsValidator;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            AuthRateLimiterFilter authRateLimiterFilter,
            UploadRateLimiterFilter uploadRateLimiterFilter,
            RestAuthenticationEntryPoint authEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            com.sense.prolt_operations.security.cors.CorsConfigValidator corsValidator) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authRateLimiterFilter = authRateLimiterFilter;
        this.uploadRateLimiterFilter = uploadRateLimiterFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsValidator = corsValidator;
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
                .referrerPolicy(ref -> ref.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Origin-Agent-Cluster", "?1"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("X-DNS-Prefetch-Control", "off"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("X-Download-Options", "noopen"))
                .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"))
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

                // Public auth endpoints (decommissioned /login returns 404 via MVC dispatcher rather than 401)
                .requestMatchers("/api/auth/send-otp", "/api/auth/verify-otp", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()

                // Public functional endpoints (exact parity with Node)
                .requestMatchers("/api/dashboard/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/resource-center/**").permitAll()
                .requestMatchers("/api/resource-center/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/database/**").permitAll()
                .requestMatchers("/api/database/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
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
                .requestMatchers("/api/auth/me", "/api/auth/sessions", "/api/auth/sessions/**").authenticated()

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
        CorsConfiguration configuration = new CorsConfiguration();

        if (corsValidator != null && corsValidator.isProduction()) {
            configuration.setAllowedOrigins(corsValidator.getParsedAllowedOrigins());
            configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "User-Agent",
                "Cache-Control",
                "Keep-Alive",
                "X-Forwarded-For",
                "RateLimit-Limit",
                "RateLimit-Remaining",
                "RateLimit-Reset"
            ));
            configuration.setExposedHeaders(List.of(
                "Authorization",
                "Set-Cookie",
                "RateLimit-Limit",
                "RateLimit-Remaining",
                "RateLimit-Reset",
                "Retry-After",
                "Content-Disposition",
                "Content-Type"
            ));
        } else {
            configuration.setAllowedOriginPatterns(List.of("*"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setExposedHeaders(List.of("*"));
        }

        configuration.setAllowedMethods(List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS",
            "HEAD"
        ));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException("No default user details service configured for " + username);
        };
    }
}
