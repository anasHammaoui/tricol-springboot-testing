package com.example.tricol.tricolspringbootrestapi.controller;

import com.example.tricol.tricolspringbootrestapi.dto.request.LoginRequest;
import com.example.tricol.tricolspringbootrestapi.dto.request.RegisterRequest;
import com.example.tricol.tricolspringbootrestapi.dto.response.JwtResponse;
import com.example.tricol.tricolspringbootrestapi.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest,
                                            HttpServletRequest request) {
        try {
            JwtResponse response = authService.login(loginRequest, request);
            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(401).body("Invalid email or password");
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Invalid email or password")) {
                return ResponseEntity.status(401).body("Invalid email or password");
            }
            if (e.getMessage().contains("User not found")) {
                return ResponseEntity.status(401).body("Invalid email or password");
            }
            return ResponseEntity.status(500).body("Login failed: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Internal server error");
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest,
                                        HttpServletRequest request) {
        try {
            String message = authService.register(registerRequest, request);
            return ResponseEntity.status(201).body(message);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Email is already taken")) {
                return ResponseEntity.status(409).body("Email is already taken");
            }
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Registration failed");
        }
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        try {
            JwtResponse response = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("Invalid refresh token")) {
                return ResponseEntity.status(401).body("Invalid or expired refresh token");
            }
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Token refresh failed");
        }
    }
}