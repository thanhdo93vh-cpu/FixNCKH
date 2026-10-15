package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.entity.AccountRole;
import com.example.hauiTrash.exception.BadRequestException;
import com.example.hauiTrash.exception.ForbiddenException;
import com.example.hauiTrash.exception.UnauthorizedException;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.security.JwtUtil;
import com.example.hauiTrash.service.AuthService;
import com.example.hauiTrash.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final PointService  pointService;
    @Override
    public AuthResponse registerUser(RegisterRequest req) {
        if (req.getPhone() == null || !req.getPhone().matches("^0\\d{9,10}$")) {
            throw new BadRequestException("Số điện thoại không hợp lệ");
        }
        if (accountRepository.existsByPhone(req.getPhone())) {
            throw new BadRequestException("Số điện thoại đã tồn tại");
        }

        Account acc = Account.builder()
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .role(AccountRole.USER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isActive(true)
                .build();
        acc = accountRepository.save(acc);
        return buildAuthResponse(acc);
    }
    @Override
    public AccountInfo getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Bạn chưa đăng nhập");
        }
        if (!(auth.getPrincipal() instanceof Account account)) {
            throw new UnauthorizedException("Thông tin đăng nhập không hợp lệ");// lấy từ context luôn
        }
        UserPointsDTO points = pointService.getUserPoints(account.getId());
        return AccountInfo.builder()
                .id(account.getId())
                .fullName(account.getFullName())
                .phone(account.getPhone())
                .email(account.getEmail())
                .role(account.getRole().name())
                .totalPoints(points.getTotalPoints())
                .rank(points.getRank())
                .rankDisplayName(points.getRankDisplayName())
                .build();
    }
    @Override
    public void logout() {
        // Stateless logout: backend không lưu token, FE tự xoá token
    }

    @Override
    public AuthResponse loginFacebook(LoginSocialRequest req) {
        String url = "https://graph.facebook.com/me?fields=id,name,email,picture.type(large)&access_token=" + req.getToken();
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        try {
            java.util.Map<String, Object> payload = restTemplate.getForObject(url, java.util.Map.class);
            if (payload == null) {
                throw new UnauthorizedException("Facebook Token không hợp lệ");
            }

            String name = (String) payload.get("name");
            String email = payload.containsKey("email") ? (String) payload.get("email") : payload.get("id") + "@facebook.com";

            String picture = null;
            if (payload.containsKey("picture")) {
                java.util.Map<String, Object> picObj = (java.util.Map<String, Object>) payload.get("picture");
                if (picObj != null && picObj.containsKey("data")) {
                    java.util.Map<String, Object> dataObj = (java.util.Map<String, Object>) picObj.get("data");
                    if (dataObj != null) {
                        picture = (String) dataObj.get("url");
                    }
                }
            }

            Account acc = accountRepository.findByEmail(email).orElse(null);
            if (acc == null) {
                java.util.Random rnd = new java.util.Random();
                String fakePhone = "099" + String.format("%07d", rnd.nextInt(10000000));
                while (accountRepository.existsByPhone(fakePhone)) {
                    fakePhone = "099" + String.format("%07d", rnd.nextInt(10000000));
                }

                acc = Account.builder()
                        .phone(fakePhone)
                        .email(email)
                        .fullName(name)
                        .avatarUrl(picture)
                        .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role(AccountRole.USER)
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                acc = accountRepository.save(acc);
            }

            if (Boolean.FALSE.equals(acc.getIsActive())) {
                throw new ForbiddenException("Tài khoản đã bị khóa");
            }
            return buildAuthResponse(acc);
        } catch (Exception e) {
            throw new UnauthorizedException("Lỗi xác thực Facebook Token: " + e.getMessage());
        }
    }
    private void validateActiveAndPassword(Account acc, String rawPassword) {
        if (Boolean.FALSE.equals(acc.getIsActive())) {
            throw new ForbiddenException("Tài khoản đã bị khóa");
        }
        if (!passwordEncoder.matches(rawPassword, acc.getPasswordHash())) {
            throw new UnauthorizedException("Sai tài khoản hoặc mật khẩu");
        }
    }


    @Override
    public AuthResponse loginGoogle(LoginSocialRequest req) {
        // Sử dụng google userinfo API để dễ dàng lấy profile từ access_token
        String url = "https://www.googleapis.com/oauth2/v3/userinfo?access_token=" + req.getToken();
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        try {
            java.util.Map<String, Object> payload = restTemplate.getForObject(url, java.util.Map.class);
            if (payload == null || !payload.containsKey("email")) {
                throw new UnauthorizedException("Google Token không hợp lệ hoặc không có email");
            }
            String email = (String) payload.get("email");
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            Account acc = accountRepository.findByEmail(email).orElse(null);
            if (acc == null) {
                java.util.Random rnd = new java.util.Random();
                String fakePhone = "099" + String.format("%07d", rnd.nextInt(10000000));
                while (accountRepository.existsByPhone(fakePhone)) {
                    fakePhone = "099" + String.format("%07d", rnd.nextInt(10000000));
                }

                acc = Account.builder()
                        .phone(fakePhone)
                        .email(email)
                        .fullName(name)
                        .avatarUrl(picture)
                        .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .role(AccountRole.USER)
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                acc = accountRepository.save(acc);
            }

            if (Boolean.FALSE.equals(acc.getIsActive())) {
                throw new ForbiddenException("Tài khoản đã bị khóa");
            }
            return buildAuthResponse(acc);
        } catch (Exception e) {
            throw new UnauthorizedException("Lỗi xác thực Google Token: " + e.getMessage());
        }
    }


    @Override
    public AuthResponse loginAdmin(LoginRequest req) {
        Account acc = accountRepository.findByEmail(req.getIdentifier())
                .orElseThrow(() -> new UnauthorizedException("Sai tài khoản hoặc mật khẩu"));

        if (acc.getRole() == AccountRole.USER) {
            throw new ForbiddenException("Tài khoản này không thể đăng nhập vào Admin");
        }

        validateActiveAndPassword(acc, req.getPassword());
        return buildAuthResponse(acc);
    }

    @Override
    public AuthResponse loginUser(LoginRequest req) {
        Account acc = accountRepository.findByPhone(req.getIdentifier())
                .orElseThrow(() -> new UnauthorizedException("Sai tài khoản hoặc mật khẩu"));

        if (acc.getRole() != AccountRole.USER) {
            throw new ForbiddenException("Tài khoản này không thể đăng nhập vào User");
        }

        validateActiveAndPassword(acc, req.getPassword());
        return buildAuthResponse(acc);
    }
    private AuthResponse buildAuthResponse(Account acc) {
        String token = jwtUtil.generateToken(acc.getId(), acc.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationSeconds())
                .account(AccountInfo.builder()
                        .id(acc.getId())
                        .fullName(acc.getFullName())
                        .phone(acc.getPhone())
                        .email(acc.getEmail())
                        .role(acc.getRole().name())
                        .build())
                .build();
    }
}
