package com.burndown.common.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Reads Gateway-injected headers and populates UserContext for downstream use.
 * Register this as a Spring bean in each service.
 */
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader("X-User-Id");
            String username = request.getHeader("X-Username");
            String permissionsHeader = request.getHeader("X-Permissions");

            if (userIdHeader != null) {
                UserContext ctx = new UserContext();
                ctx.setUserId(Long.parseLong(userIdHeader));
                ctx.setUsername(username);
                if (permissionsHeader != null && !permissionsHeader.isBlank()) {
                    List<String> perms = Arrays.asList(permissionsHeader.split(","));
                    ctx.setPermissions(perms);
                } else {
                    ctx.setPermissions(List.of());
                }
                UserContext.set(ctx);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
