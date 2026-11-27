package com.sense.prolt_operations.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sense.prolt_operations.dto.auth.AuthResponse;
import com.sense.prolt_operations.dto.auth.AuthUserResponse;
import com.sense.prolt_operations.dto.auth.SendOtpDTO;
import com.sense.prolt_operations.dto.auth.SuccessResponse;
import com.sense.prolt_operations.dto.auth.VerifyOtpDTO;
import com.sense.prolt_operations.dto.response.UserMeData;
import com.sense.prolt_operations.dto.response.UserMeResponse;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.exception.UnauthorizedException;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       OtpService otpService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.refreshTokenService = refreshTokenService;
    }

    public SuccessResponse sendOtp(SendOtpDTO dto) {
        if (dto == null || dto.email() == null || dto.email().isBlank()) {
            throw new BadRequestException("Email is required");
        }
        String normalizedEmail = dto.email().trim().toLowerCase(java.util.Locale.ROOT);
        otpService.generateAndSendOtp(normalizedEmail);
        log.info("OTP sent successfully to {}", normalizedEmail);
        return new SuccessResponse(true, "OTP sent successfully");
    }

    public AuthResponse verifyOtp(VerifyOtpDTO dto) {
        return verifyOtp(dto, "127.0.0.1", "UNKNOWN").authResponse();
    }

    public com.sense.prolt_operations.dto.auth.RefreshResult verifyOtp(VerifyOtpDTO dto, String ip, String userAgent) {
        if (dto == null || dto.email() == null || dto.email().isBlank() || dto.otp() == null || dto.otp().isBlank()) {
            throw new BadRequestException("Email and OTP are required");
        }

        String email = dto.email().trim().toLowerCase(java.util.Locale.ROOT);
        String otp = dto.otp().trim();

        boolean isValid = otpService.verifyOtp(email, otp);
        if (!isValid) {
            throw new UnauthorizedException("Invalid or expired OTP");
        }

        User user = userRepository.findByNormalizedEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is disabled. Please contact administrator.");
        }

        log.info("User OTP login successful: {}", email);
        return refreshTokenService.createSession(user, ip, userAgent);
    }

    public com.sense.prolt_operations.dto.auth.RefreshResult refresh(String rawRefreshToken, String ip, String userAgent) {
        return refreshTokenService.rotateSession(rawRefreshToken, ip, userAgent);
    }

    public org.springframework.http.ResponseCookie logout(String rawRefreshToken) {
        return refreshTokenService.logout(rawRefreshToken);
    }

    public java.util.List<com.sense.prolt_operations.dto.auth.UserSessionDTO> getSessions(String userId, String currentRawToken) {
        return refreshTokenService.getUserSessions(userId, currentRawToken);
    }

    public void revokeSession(String userId, String sessionId) {
        refreshTokenService.revokeUserSession(userId, sessionId);
    }

    public String getRefreshTokenCookieName() {
        return refreshTokenService.getCookieName();
    }

    public UserMeResponse getMe(ProltUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is disabled. Please contact administrator.");
        }

        UserMeData userData = new UserMeData(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : principal.getRole(),
                user.isEnabled(),
                user.getHiddenModulesList(),
                user.getReadOnlyModulesList(),
                principal.getIssuedAt(),
                principal.getExpiresAt()
        );

        return UserMeResponse.of(userData);
    }

    private AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.isEnabled(),
                user.getHiddenModulesList(),
                user.getReadOnlyModulesList()
        );
    }
}
