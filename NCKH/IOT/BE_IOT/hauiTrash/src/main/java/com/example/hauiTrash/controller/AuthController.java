package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {
@Autowired
 private AuthService authService;
 @PostMapping("/register")
 public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
  AuthResponse data = authService.registerUser(req);
  return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
          .message("Đăng ký thành công")
          .data(data)
          .build());
 }

 @PostMapping("/login/user")
 public ResponseEntity<ApiResponse<AuthResponse>> loginUser(@Valid @RequestBody LoginRequest req) {
  AuthResponse data = authService.loginUser(req);
  return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
          .message("Đăng nhập thành công")
          .data(data)
          .build());
 }

 @PostMapping("/login/admin")
 public ResponseEntity<ApiResponse<AuthResponse>> loginAdmin(@Valid @RequestBody LoginRequest req) {
  AuthResponse data = authService.loginAdmin(req);
  return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
          .message("Đăng nhập thành công")
          .data(data)
          .build());
 }

 @PostMapping("/login/google")
 public ResponseEntity<ApiResponse<AuthResponse>> loginGoogle(@Valid @RequestBody com.example.hauiTrash.dto.LoginSocialRequest req) {
  AuthResponse data = authService.loginGoogle(req);
  return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
          .message("Đăng nhập Google thành công")
          .data(data)
          .build());
 }

 @PostMapping("/login/facebook")
 public ResponseEntity<ApiResponse<AuthResponse>> loginFacebook(@Valid @RequestBody com.example.hauiTrash.dto.LoginSocialRequest req) {
  AuthResponse data = authService.loginFacebook(req);
  return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
          .message("Đăng nhập Facebook thành công")
          .data(data)
          .build());
 }

 @PostMapping("/logout")
 public ResponseEntity<ApiResponse<Object>> logout() {
  authService.logout();
  return ResponseEntity.ok(ApiResponse.builder()
          .message("Đăng xuất thành công")
          .data(null)
          .build());
 }
 @GetMapping("/me")
 public ResponseEntity<ApiResponse<AccountInfo>> getCurrentUser() {
  AccountInfo accountInfo = authService.getCurrentUser();
     return ResponseEntity.ok(
             ApiResponse.<AccountInfo>builder()
                     .message("Lấy thông tin thành công")
                     .data(accountInfo)
                     .build()
     );
 }
}

