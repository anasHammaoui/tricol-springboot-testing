package com.example.tricol.tricolspringbootrestapi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for authenticated user information
 * Works for both local and Keycloak authenticated users
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {
    private String username;
    private String email;
    private List<String> roles;
    private String authProvider; // "LOCAL" or "KEYCLOAK"
    private String subject; // User ID or Keycloak subject
}
