package org.victor.stackora.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.constant.RequestConstants;
import org.victor.stackora.constant.SessionConstants;
import org.victor.stackora.exception.BusinessException;
import org.victor.stackora.service.UserAccountService;
import org.victor.stackora.service.result.UserInfoResult;
import org.victor.stackora.utils.SessionUtils;

@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final UserAccountService userAccountService;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        // 静态资源等非 Controller 请求不处理。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HttpSession session = request.getSession(false);

        if (session == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        Long userId = resolveLoginUserId(session);

        try {
            // 每次查询数据库，确保封禁、注销和资料变化立即生效。
            UserInfoResult authenticatedUser = userAccountService.getCurrentUserInfoById(userId);

            // 只在本次请求中保存，不写回 Session。
            request.setAttribute(
                    RequestConstants.AUTHENTICATED_USER,
                    authenticatedUser
            );

            return true;
        } catch (BusinessException exception) {
            ErrorCode errorCode = exception.getErrorCode();

            if (errorCode == ErrorCode.ACCOUNT_NOT_FOUND) {
                // Session 指向的用户已经不存在。
                SessionUtils.invalidateQuietly(session);
                throw new BusinessException(ErrorCode.NOT_LOGIN);
            }

            if (errorCode == ErrorCode.ACCOUNT_DISABLED
                    || errorCode == ErrorCode.ACCOUNT_CANCELLED) {
                // 账号状态已经失效，立即清除旧登录态。
                SessionUtils.invalidateQuietly(session);
            }

            // 数据库异常、系统异常不能随便清除 Session。
            throw exception;
        }
    }

    /**
     * 从 Session 中安全解析用户 ID。
     */
    private static Long resolveLoginUserId(HttpSession session) {
        final Object loginUserId;

        try {
            loginUserId = session.getAttribute(
                    SessionConstants.LOGIN_USER_ID
            );
        } catch (IllegalStateException exception) {
            // Session 已被并发请求失效。
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        if (!(loginUserId instanceof Long userId) || userId <= 0) {
            SessionUtils.invalidateQuietly(session);
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        return userId;
    }
}