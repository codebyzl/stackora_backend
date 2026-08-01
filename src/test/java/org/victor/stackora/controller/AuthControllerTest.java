package org.victor.stackora.controller;

/**
 * @author: Victor_zl
 * @version: 1.0
 * @Description:
 */

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.exception.BusinessException;
import org.victor.stackora.exception.GlobalExceptionHandler;
import org.victor.stackora.service.UserAccountService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Spring Boot 4 使用 @MockitoBean。
     * 该 Mock 会替代真实 UserAccountService，
     * 因此本测试不会连接 MySQL。
     */
    @MockitoBean
    private UserAccountService userAccountService;

    /**
     * 合法注册应返回 HTTP 200 和字符串形式的用户 ID。
     */
    @Test
    void userRegisterShouldReturnSuccessResponse() throws Exception {
        when(userAccountService.userRegister(
                "Victor_01",
                "Password123"
        )).thenReturn(123L);

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "account": "Victor_01",
                                          "rawPassword": "Password123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.userId").value("123"));

        verify(userAccountService).userRegister(
                "Victor_01",
                "Password123"
        );
    }

    /**
     * 参数校验失败时不应调用 Service。
     */
    @Test
    void invalidAccountShouldReturnBadRequest() throws Exception {
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "account": "ab",
                                          "rawPassword": "Password123"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.PARAMS_ERROR.getCode()))
                .andExpect(jsonPath("$.message")
                        .value("账号格式不正确"));

        verifyNoInteractions(userAccountService);
    }

    /**
     * Service 判断账号重复时应返回 HTTP 409。
     */
    @Test
    void duplicateAccountShouldReturnConflict() throws Exception {
        when(userAccountService.userRegister(
                "Victor_01",
                "Password123"
        )).thenThrow(
                new BusinessException(
                        ErrorCode.ACCOUNT_ALREADY_EXISTS
                )
        );

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "account": "Victor_01",
                                          "rawPassword": "Password123"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.ACCOUNT_ALREADY_EXISTS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.ACCOUNT_ALREADY_EXISTS.getMessage()));
    }
}