package com.hostelmanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final PrincipalLoader principalLoader;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            jwtService.parse(token)
                    .flatMap(claims -> principalLoader.load(claims.userId(), claims.issuedAt()))
                    .ifPresent(user -> {
                        var auth = new UsernamePasswordAuthenticationToken(user, null, authorities(user));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }

    static List<GrantedAuthority> authorities(AuthUser user) {
        List<GrantedAuthority> list = new ArrayList<>();
        user.roles().forEach(r -> list.add(new SimpleGrantedAuthority("ROLE_" + r)));
        user.permissions().forEach(p -> list.add(new SimpleGrantedAuthority("PERM_" + p)));
        return list;
    }
}
