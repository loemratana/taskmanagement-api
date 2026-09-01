package com.ratana.task_management_api.security;


import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Component
@AllArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain filterChain)
            throws ServletException, IOException {
            final String token = extractTokenFromHeader(request);

            if (StringUtils.hasText(token))
            {
                try
                {
                    authenticate(token,request);
                }
                catch (ExpiredJwtException ex)
                {
                    log.warn("JWT Token has expired: {}", ex.getMessage());
                    SecurityContextHolder.clearContext();

                }
                catch (MalformedJwtException | IllegalArgumentException ex)
                {
                    log.warn("Invalid JWT Token: {}", ex.getMessage());
                    SecurityContextHolder.clearContext();
                }
                catch (Exception ex)
                {
                    log.error("Could not authenticate JWT: {}", ex.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }
            filterChain.doFilter(request,response);

    }

    private void authenticate(String token , HttpServletRequest request)
    {

        // Don't re-authenticate if already authenticated in this context
        if (SecurityContextHolder.getContext().getAuthentication()!=null)
        {
            return;
        }
        String email = jwtTokenProvider.extractUsername(token);

        if (StringUtils.hasText(email))
        {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            log.info("Authenticated user: {}", userDetails.getUsername());
            log.info("Authorities: {}", userDetails.getAuthorities());
            if (jwtTokenProvider.validateToken(token,userDetails))
            {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,null,userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
    }
    // Extract Bearer token from the Authorization header
    private String extractTokenFromHeader(HttpServletRequest request)
    {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasLength(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }


        return null;
    }
}
