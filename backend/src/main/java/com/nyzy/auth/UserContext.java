package com.nyzy.auth;

/** 当前登录用户上下文 (请求级 ThreadLocal) */
public class UserContext {

    public static class CurrentUser {
        public final Long id;
        public final String username;
        public final String role;
        public final Long regionId;

        public CurrentUser(Long id, String username, String role) {
            this(id, username, role, null);
        }

        public CurrentUser(Long id, String username, String role, Long regionId) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.regionId = regionId;
        }
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) { HOLDER.set(user); }
    public static CurrentUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public static String username() {
        CurrentUser u = HOLDER.get();
        return u == null ? "system" : u.username;
    }

    public static boolean isAdmin() {
        CurrentUser u = HOLDER.get();
        return u != null && "admin".equals(u.role);
    }
}
