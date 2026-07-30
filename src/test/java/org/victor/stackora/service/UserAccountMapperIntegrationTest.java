package org.victor.stackora.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.exception.BusinessException;
import org.victor.stackora.mapper.UserAccountMapper;
import org.victor.stackora.model.entity.UserAccount;
import org.victor.stackora.model.enums.UserRole;
import org.victor.stackora.model.enums.UserStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserAccountMapperIntegrationTest {

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private UserAccountMapper userAccountMapper;


    @Test
    void userRegisterShouldCreateUserAndReturnId() {
        String account = randomAccount();
        String rawPassword = "Password123";

        Long userId = userAccountService.userRegister(
                account,
                rawPassword
        );

        // 检查自增ID是否成功回填
        assertNotNull(userId);
        assertTrue(userId > 0);

        // 从数据库重新查询
        UserAccount savedUser =
                userAccountMapper.selectById(userId);

        assertNotNull(savedUser);

        // 检查账号是否统一转换成小写
        assertEquals(
                account.toLowerCase(),
                savedUser.getAccount()
        );

        // 检查默认昵称
        assertEquals(
                account.toLowerCase(),
                savedUser.getNickname()
        );

        // 数据库不能保存原始密码
        assertNotEquals(
                rawPassword,
                savedUser.getPasswordHash()
        );


        // 检查默认角色和状态
        assertEquals(
                UserRole.USER,
                savedUser.getRole()
        );

        assertEquals(
                UserStatus.ACTIVE,
                savedUser.getStatus()
        );
    }

    @Test
    void userRegisterShouldRejectDuplicateAccountIgnoringCase() {
        String account = randomAccount();
        String rawPassword = "Password123";

        userAccountService.userRegister(
                account,
                rawPassword
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userAccountService.userRegister(
                        account.toUpperCase(),
                        rawPassword
                )
        );

        assertEquals(
                ErrorCode.ACCOUNT_ALREADY_EXISTS,
                exception.getErrorCode()
        );
    }

    private String randomAccount() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

        return "Test_" + suffix;
    }
}