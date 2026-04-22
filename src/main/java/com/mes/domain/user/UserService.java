package com.mes.domain.user;

import com.mes.domain.user.dto.RegisterRequest;
import com.mes.domain.user.dto.UpdateRoleRequest;
import com.mes.domain.user.dto.UserResponse;
import com.mes.global.exception.CustomException;
import com.mes.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new CustomException(ErrorCode.USER_ALREADY_EXISTS);
        }
        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.OPERATOR)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateRole(Long userId, UpdateRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        user.changeRole(request.role());
        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public String findRoleByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getRole().name())
                .orElse("OPERATOR");
    }
}
