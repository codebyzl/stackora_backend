package org.victor.stackora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.victor.stackora.common.ApiResponse;
import org.victor.stackora.common.ApiResponseFactory;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.constant.RequestConstants;
import org.victor.stackora.constant.SessionConstants;
import org.victor.stackora.exception.BusinessException;
import org.victor.stackora.model.dto.user.LoginRequest;
import org.victor.stackora.model.dto.user.RegisterRequest;
import org.victor.stackora.model.vo.user.RegisterResponse;
import org.victor.stackora.model.vo.user.UserInfoResponse;
import org.victor.stackora.service.UserAccountService;
import org.victor.stackora.service.result.UserInfoResult;
import org.victor.stackora.utils.SessionUtils;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:
 */
@Tag(name = "认证接口", description = "注册、登录等认证相关接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {


    private final UserAccountService userAccountService;

    @Operation(summary = "用户注册", description = "使用账号和密码创建用户")
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> userRegister(@Valid @RequestBody RegisterRequest registerRequest) {
        Long userId = userAccountService.userRegister(registerRequest.account(), registerRequest.rawPassword());
        return ApiResponseFactory.success(new RegisterResponse(userId));
    }

    @Operation(summary = "用户登录", description = "使用账号和密码登录")
    @PostMapping("/login")
    public ApiResponse<UserInfoResponse> userLogin(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {

        UserInfoResult userInfoResult = userAccountService.userLogin(loginRequest.account(), loginRequest.rawPassword());

        // 2. 只有凭证验证成功后才清理旧 Session。
        SessionUtils.invalidateQuietly(request.getSession(false));

        // 3. 创建全新的 Session
        HttpSession newSession = request.getSession(true);

        // 4. 保存登录用户 ID
        newSession.setAttribute(SessionConstants.LOGIN_USER_ID, userInfoResult.userId());

        return ApiResponseFactory.success(UserInfoResponse.from(userInfoResult), "登录成功");
    }


    @Operation(summary = "登录用户信息", description = "查询当前登录用户信息")
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getAuthenticatedUser(HttpServletRequest request) {
        UserInfoResult authenticatedUser = requireAuthenticatedUser(request);

        return ApiResponseFactory.success(UserInfoResponse.from(authenticatedUser));
    }


    @Operation(summary = "退出登录", description = "退出当前用户")
    @PostMapping("/logout")
    public ApiResponse<Void> userLogout(HttpServletRequest request) {
        SessionUtils.invalidateQuietly(request.getSession(false));
        return ApiResponseFactory.success();
    }


    private static UserInfoResult requireAuthenticatedUser(HttpServletRequest request) {
        Object authenticatedUser = request.getAttribute(
                RequestConstants.AUTHENTICATED_USER
        );

        if (!(authenticatedUser instanceof UserInfoResult userInfoResult)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        return userInfoResult;
    }
}