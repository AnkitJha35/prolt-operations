package com.sense.prolt_operations.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sense.prolt_operations.dto.auth.AuthResponse;
import com.sense.prolt_operations.dto.auth.AuthUserResponse;
import com.sense.prolt_operations.dto.auth.LoginDTO;
import com.sense.prolt_operations.dto.auth.SendOtpDTO;
import com.sense.prolt_operations.dto.auth.SuccessResponse;
import com.sense.prolt_operations.dto.auth.VerifyOtpDTO;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.exception.UnauthorizedException;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.security.ProltUserPrincipal;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
    }

    public AuthResponse login(LoginDTO dto) {
        if (dto == null || dto.effectiveUsername() == null || dto.effectiveUsername().isBlank()) {
            throw new BadRequestException("Username is required");
        }
        if (dto.password() == null || dto.password().isBlank()) {
            throw new BadRequestException("Password is required");
        }

        String username = dto.effectiveUsername().trim();
        String password = dto.password().trim();

        User user = userRepository.findByNormalizedUsername(username)
                .or(() -> userRepository.findByNormalizedEmail(username))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        log.info("User login successful: {}", user.getUsername());
        return new AuthResponse(true, token, toAuthUserResponse(user));
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

        String token = jwtService.generateToken(user);
        log.info("User OTP login successful: {}", email);
        return new AuthResponse(true, token, toAuthUserResponse(user));
    }

    public Map<String, Object> getMe(ProltUserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", principal.getId());
        userData.put("username", principal.getUsername());
        userData.put("role", principal.getRole());
        userData.put("hiddenModules", principal.getHiddenModules());
        userData.put("readOnlyModules", principal.getReadOnlyModules());
        userData.put("iat", principal.getIssuedAt());
        userData.put("exp", principal.getExpiresAt());

        response.put("user", userData);
        return response;
    }

    private AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getHiddenModulesList(),
                user.getReadOnlyModulesList()
        );
    }
}
