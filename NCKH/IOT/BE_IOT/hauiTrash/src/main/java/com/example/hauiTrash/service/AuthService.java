package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.*;

public interface AuthService {
    AuthResponse registerUser(RegisterRequest request);
    AuthResponse loginUser(LoginRequest request);
    AuthResponse loginAdmin(LoginRequest request);
    AuthResponse loginGoogle(LoginSocialRequest request);
    AuthResponse loginFacebook(LoginSocialRequest request);
    void logout(); // stateless
    AccountInfo getCurrentUser();
}
