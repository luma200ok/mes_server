package com.mes.global.security;

import com.mes.domain.user.UserService;
import com.mes.domain.user.dto.RegisterRequest;
import com.mes.domain.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final LoginRateLimiter loginRateLimiter;

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        // 잠금 여부 사전 확인
        loginRateLimiter.checkNotLocked(request.username());

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(request.username());
        } catch (Exception e) {
            loginRateLimiter.recordFailure(request.username());
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        if (!passwordEncoder.matches(request.password(), userDetails.getPassword())) {
            loginRateLimiter.recordFailure(request.username());
            throw new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        loginRateLimiter.clearFailures(request.username());

        String token = jwtTokenProvider.generateToken(userDetails.getUsername());
        String role  = userService.findRoleByUsername(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("token", token, "type", "Bearer", "role", role));
    }

    @Operation(summary = "사용자 등록 (ADMIN 전용)")
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
