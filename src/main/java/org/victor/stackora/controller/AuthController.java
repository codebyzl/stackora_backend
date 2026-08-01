package org.victor.stackora.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.victor.stackora.common.ApiResponse;
import org.victor.stackora.common.ApiResponseFactory;
import org.victor.stackora.model.dto.user.RegisterRequest;
import org.victor.stackora.model.vo.RegisterResponse;
import org.victor.stackora.service.UserAccountService;

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
}