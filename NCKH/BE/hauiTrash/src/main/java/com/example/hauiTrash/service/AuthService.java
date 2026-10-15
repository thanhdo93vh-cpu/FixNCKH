package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.*;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {
    AuthResponse registerUser(RegisterRequest request);
    AuthResponse loginUser(LoginRequest request);
    AuthResponse loginAdmin(LoginRequest request);
    AuthResponse loginGoogle(LoginSocialRequest request);
    AuthResponse loginFacebook(LoginSocialRequest request);
    void logout(); // stateless
    AccountInfo getCurrentUser();
}
