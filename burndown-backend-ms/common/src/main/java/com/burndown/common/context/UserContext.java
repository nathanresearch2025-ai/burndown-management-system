package com.burndown.common.context;

import lombok.Data;

import java.util.List;

/**
 * Holds the current authenticated user, populated from Gateway-injected headers:
 * X-User-Id, X-Username, X-Permissions
 */
@Data
public class UserContext {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private Long userId;
    private String username;
    private List<String> permissions;

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long currentUserId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static boolean hasPermission(String permission) {
        UserContext ctx = HOLDER.get();
        return ctx != null && ctx.getPermissions() != null
                && ctx.getPermissions().contains(permission);
    }
}
