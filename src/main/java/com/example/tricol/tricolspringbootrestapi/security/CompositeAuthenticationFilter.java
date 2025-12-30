package com.example.tricol.tricolspringbootrestapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class CompositeAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider localTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtDecoder keycloakJwtDecoder;
    private final KeycloakJwtAuthenticationConverter keycloakJwtConverter;

    @Autowired
    public CompositeAuthenticationFilter(
            JwtTokenProvider localTokenProvider,
            CustomUserDetailsService customUserDetailsService,
            @Lazy JwtDecoder keycloakJwtDecoder,
            KeycloakJwtAuthenticationConverter keycloakJwtConverter) {
        this.localTokenProvider = localTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
        this.keycloakJwtDecoder = keycloakJwtDecoder;
        this.keycloakJwtConverter = keycloakJwtConverter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                // Try local JWT validation first
                if (tryLocalAuthentication(jwt, request)) {
                    log.debug("Authenticated via local JWT");
                }
                // Try Keycloak JWT validation
                else if (tryKeycloakAuthentication(jwt, request)) {
                    log.debug("Authenticated via Keycloak OAuth2");
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

     // Attempts to authenticate using local JWT (internal users)
    
    private boolean tryLocalAuthentication(String jwt, HttpServletRequest request) {
        try {
            if (localTokenProvider.validateToken(jwt)) {
                String username = localTokenProvider.getUsernameFromJWT(jwt);
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                return true;
            }
        } catch (Exception ex) {
            log.debug("Local JWT validation failed: {}", ex.getMessage());
        }
        return false;
    }

    // Attempts to authenticate using Keycloak JWT (OAuth2/SSO)
    private boolean tryKeycloakAuthentication(String jwt, HttpServletRequest request) {
        try {
            Jwt decodedJwt = keycloakJwtDecoder.decode(jwt);
            AbstractAuthenticationToken authentication = keycloakJwtConverter.convert(decodedJwt);
            
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                return true;
            }
        } catch (JwtException ex) {
            log.debug("Keycloak JWT validation failed: {}", ex.getMessage());
        }
        return false;
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken)) {
            if (bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
            return bearerToken;
        }
        return null;
    }
}
