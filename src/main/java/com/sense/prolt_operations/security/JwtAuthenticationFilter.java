package com.sense.prolt_operations.security;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Optional;
import io.jsonwebtoken.Claims;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.exception.SecurityStateUnavailableException;
import com.sense.prolt_operations.repository.UserSecurityState;
import com.sense.prolt_operations.security.state.UserSecurityStateProvider;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserSecurityStateProvider securityStateProvider;

    public JwtAuthenticationFilter(JwtService jwtService, UserSecurityStateProvider securityStateProvider) {
        this.jwtService = jwtService;
        this.securityStateProvider = securityStateProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    Claims claims = jwtService.getClaims(token);
                    String userId = claims.get("id", String.class);
                    Object tvObj = claims.get("tokenVersion");

                    if (userId != null && tvObj instanceof Number num) {
                        int tokenVersionInToken = num.intValue();
                        Optional<UserSecurityState> stateOpt = securityStateProvider.getSecurityState(userId);

                        if (stateOpt.isPresent()) {
                            UserSecurityState userState = stateOpt.get();

                            if (!userState.enabled()) {
                                log.debug("User '{}' ({}) is disabled - rejecting authentication", userState.username(), userId);
                                request.setAttribute("auth_error_message", "Account is disabled. Please contact administrator.");
                            } else if (userState.tokenVersion() != tokenVersionInToken) {
                                log.debug("Token version mismatch for user '{}': token={}, provider={} - rejecting authentication",
                                        userState.username(), tokenVersionInToken, userState.tokenVersion());
                            } else {
                                String username = claims.get("username", String.class);
                                String role = claims.get("role", String.class);
                                @SuppressWarnings("unchecked")
                                List<String> hidden = claims.get("hiddenModules", List.class);
                                @SuppressWarnings("unchecked")
                                List<String> readOnly = claims.get("readOnlyModules", List.class);
                                long issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() / 1000 : 0;
                                long expiresAt = claims.getExpiration() != null ? claims.getExpiration().getTime() / 1000 : 0;

                                ProltUserPrincipal principal = new ProltUserPrincipal(
                                        userId,
                                        username != null ? username : userState.username(),
                                        role != null ? role : (userState.role() != null ? userState.role().name() : Role.CLIENT.name()),
                                        tokenVersionInToken,
                                        hidden,
                                        readOnly,
                                        issuedAt,
                                        expiresAt
                                );

                                UsernamePasswordAuthenticationToken authentication =
                                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
                        } else {
                            log.debug("User ID '{}' not found in database - rejecting authentication", userId);
                        }
                    } else {
                        log.debug("Token missing required 'id' or 'tokenVersion' claim - rejecting authentication");
                    }
                } catch (SecurityStateUnavailableException e) {
                    log.error("Security state provider unavailable: {}", e.getMessage());
                    request.setAttribute("auth_error_message", "Security state service temporarily unavailable");
                    SecurityContextHolder.clearContext();
                } catch (Exception e) {
                    log.debug("Token authentication failed: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
