package com.ratana.task_management_api.infrastructure.security;

import com.ratana.task_management_api.common.cache.AppCaches;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static final String EMAIL = "e1@example.com";

    private JwtTokenProvider tokenProvider;
    private UserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                Base64.getEncoder().encodeToString("test-secret-test-secret-test-secret-test-secret".getBytes()));
        ReflectionTestUtils.setField(tokenProvider, "accessTokenExpiration", 60_000L);
        ReflectionTestUtils.setField(tokenProvider, "refreshTokenExpiration", 600_000L);
        userDetailsService = mock(UserDetailsService.class);
        // Fresh caches per test, so no principal carries over between tests.
        AppCaches caches = new AppCaches(new SimpleMeterRegistry(), Duration.ofMinutes(1), Duration.ofMinutes(10));
        filter = new JwtAuthenticationFilter(tokenProvider, new RequestPrincipalLoader(userDetailsService, caches));
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal principal(boolean enabled) {
        return new UserPrincipal(UUID.randomUUID(), EMAIL, "hash", null,
                List.of(new SimpleGrantedAuthority("tasks:read")), enabled);
    }

    private void runWith(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    @DisplayName("access token for an enabled user authenticates")
    void accessToken_authenticates() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(principal(true));

        runWith(tokenProvider.generateAccessToken(EMAIL));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("TC-AUTH-08: refresh token presented as bearer is rejected (DEF-01)")
    void refreshToken_isRejected() throws Exception {
        runWith(tokenProvider.generateRefreshToken(EMAIL));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(chain).doFilter(any(), any());   // continues unauthenticated → entry point returns 401
    }

    @Test
    @DisplayName("TC-AUTH-11: valid access token of a disabled user is rejected (DEF-04)")
    void disabledUser_isRejected() throws Exception {
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(principal(false));

        runWith(tokenProvider.generateAccessToken(EMAIL));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
