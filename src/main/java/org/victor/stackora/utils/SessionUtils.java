package org.victor.stackora.utils;

import jakarta.servlet.http.HttpSession;

/**
 * HttpSession 生命周期工具。
 */
public final class SessionUtils {

    private SessionUtils() {
    }

    /**
     * 尝试使 Session 失效。
     * <p>
     * Session 不存在或者已经被其他并发请求失效时，
     * 最终状态都符合“Session 已失效”的目标。
     */
    public static void invalidateQuietly(HttpSession session) {
        if (session == null) {
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Session 已经失效，视为清理完成。
        }
    }
}