package com.example.tricol.tricolspringbootrestapi.controller;

import com.example.tricol.tricolspringbootrestapi.dto.request.LoginRequest;
import com.example.tricol.tricolspringbootrestapi.dto.request.RegisterRequest;
import com.example.tricol.tricolspringbootrestapi.dto.response.JwtResponse;
import com.example.tricol.tricolspringbootrestapi.dto.response.UserInfoResponse;
import com.example.tricol.tricolspringbootrestapi.security.CustomUserDetails;
import com.example.tricol.tricolspringbootrestapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    /**
     * Local authentication endpoint for internal users
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                            HttpServletRequest request) {

            JwtResponse response = authService.login(loginRequest, request);
            return ResponseEntity.ok(response);

    }
    
    /**
     * Register new local user
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest,
                                        HttpServletRequest request) {
            String message = authService.register(registerRequest, request);
            return ResponseEntity.status(201).body(message);
    }
    
    /**
     * Refresh local JWT token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
            JwtResponse response = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(response);
    }
    
    /**
     * Get current user information
     * Works for both local JWT and Keycloak OAuth2 authenticated users
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        
        UserInfoResponse response;
        
        // Check if authenticated via Keycloak (OAuth2 JWT)
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            response = UserInfoResponse.builder()
                    .username(jwt.getClaimAsString("preferred_username"))
                    .email(jwt.getClaimAsString("email"))
                    .subject(jwt.getSubject())
                    .authProvider("KEYCLOAK")
                    .roles(authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()))
                    .build();
        }
        // Local JWT authentication
        else if (authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            response = UserInfoResponse.builder()
                    .username(userDetails.getUsername())
                    .email(userDetails.getUsername()) // Email is used as username
                    .subject(userDetails.getUserId().toString())
                    .authProvider("LOCAL")
                    .roles(userDetails.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()))
                    .build();
        }
        else {
            response = UserInfoResponse.builder()
                    .username(authentication.getName())
                    .authProvider("UNKNOWN")
                    .roles(authentication.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toList()))
                    .build();
        }
        
        return ResponseEntity.ok(response);
    }
}