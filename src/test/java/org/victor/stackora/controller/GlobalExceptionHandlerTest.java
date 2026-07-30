package org.victor.stackora.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.exception.BusinessException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestApiController.class)
@Import(
        GlobalExceptionHandlerTest.TestApiController.class
)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessExceptionShouldReturnMappedHttpStatusAndApiResponse() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("请求失败"));
    }

    @Test
    void businessExceptionWithSafeMessageShouldReturnCustomMessage() throws Exception {
        mockMvc.perform(get("/test/business-error-custom-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("账号格式不正确"));
    }

    @Test
    void runtimeExceptionShouldReturnSystemErrorWithoutInternalMessage() throws Exception {
        mockMvc.perform(get("/test/runtime-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(99999))
                .andExpect(jsonPath("$.message").value("系统异常"));
    }

    @RestController
    static class TestApiController {

        @GetMapping("/test/business-error")
        void businessError() {
            throw new BusinessException(ErrorCode.ERROR);
        }

        @GetMapping("/test/business-error-custom-message")
        void businessErrorWithCustomMessage() {
            throw new BusinessException(ErrorCode.ERROR, "账号格式不正确");
        }

        @GetMapping("/test/runtime-error")
        void runtimeError() {
            throw new RuntimeException("database password leaked here");
        }
    }
}