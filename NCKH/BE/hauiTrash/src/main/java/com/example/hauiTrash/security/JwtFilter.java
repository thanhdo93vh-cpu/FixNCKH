package com.example.hauiTrash.security;

import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AccountRepository accountRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Nếu không có token hoặc không phải Bearer token -> cho đi tiếp (có thể là request public)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        // 🔥 SỬA: Nếu token không hợp lệ -> trả về 401 luôn, không cho đi tiếp
        if (!jwtUtil.validate(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Token không hợp lệ hoặc đã hết hạn\"}");
            return;  // Dừng lại, không gọi filterChain.doFilter
        }

        // Nếu đã auth rồi thì bỏ qua
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Integer accountId = jwtUtil.getAccountId(token);

        Account acc = accountRepo.findById(accountId).orElse(null);
        if (acc == null || Boolean.FALSE.equals(acc.getIsActive())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Tài khoản không tồn tại hoặc đã bị vô hiệu hóa\"}");
            return;
        }

        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + acc.getRole().name()));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(acc, null, authorities);

        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}