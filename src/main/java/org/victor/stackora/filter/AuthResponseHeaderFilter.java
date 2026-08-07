package org.victor.stackora.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 为认证相关接口设置禁止缓存响应头。
 */
@Component
public final class AuthResponseHeaderFilter extends OncePerRequestFilter {

    private static final Set<String> AUTH_ENDPOINTS = Set.of(
            "/auth/login",
            "/auth/me",
            "/auth/logout"
    );

    /**
     * 非认证接口跳过该过滤器。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AUTH_ENDPOINTS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        filterChain.doFilter(request, response);
    }
}