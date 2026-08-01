package org.victor.stackora.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.exception.BusinessException;
import org.victor.stackora.mapper.UserAccountMapper;
import org.victor.stackora.model.entity.UserAccount;
import org.victor.stackora.service.impl.UserAccountServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 用户账户service unit_test
 */

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAccountServiceImpl userAccountService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                userAccountService,
                "baseMapper",
                userAccountMapper
        );
    }

    /**
     * 注册时应当将账号统一转换为小写。
     */
    @Test
    void userRegisterShouldConvertAccountToLowercase() {
        // 账号不存在
        when(userAccountMapper.selectCount(any()))
                .thenReturn(0L);

        // 密码编码成功
        when(passwordEncoder.encode("Password123"))
                .thenReturn("encoded-password");

        // 数据库插入成功，并模拟自增 ID 回填
        when(userAccountMapper.insert(any(UserAccount.class)))
                .thenAnswer(invocation -> {
                    UserAccount user = invocation.getArgument(0);
                    user.setId(1L);
                    return 1;
                });

        userAccountService.userRegister(
                "Victor",
                "Password123"
        );

        ArgumentCaptor<UserAccount> captor =
                ArgumentCaptor.forClass(UserAccount.class);

        verify(userAccountMapper)
                .insert(captor.capture());

        UserAccount insertedUser = captor.getValue();

        assertEquals(
                "victor",
                insertedUser.getAccount()
        );
        assertEquals(
                "victor",
                insertedUser.getNickname()
        );
    }

    /**
     * 账号已存在时应当抛出业务异常。
     */
    @Test
    void duplicateAccountShouldThrowException() {
        // 模拟账号已存在
        when(userAccountMapper.selectCount(any()))
                .thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userAccountService.userRegister(
                        "Victor",
                        "Password123"
                )
        );

        assertEquals(
                ErrorCode.ACCOUNT_ALREADY_EXISTS,
                exception.getErrorCode()
        );

        // 重复账号不应继续编码密码或插入数据库
        verify(passwordEncoder, never())
                .encode(any());

        verify(userAccountMapper, never())
                .insert(any(UserAccount.class));
    }

    /**
     * 数据库插入失败时应当抛出系统异常。
     */
    @Test
    void insertFailureShouldThrowException() {
        // 账号不存在
        when(userAccountMapper.selectCount(any()))
                .thenReturn(0L);

        when(passwordEncoder.encode("Password123"))
                .thenReturn("encoded-password");

        // insert 返回 0，表示没有插入记录
        when(userAccountMapper.insert(any(UserAccount.class)))
                .thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userAccountService.userRegister(
                        "Victor",
                        "Password123"
                )
        );

        assertEquals(
                ErrorCode.SYSTEM_ERROR,
                exception.getErrorCode()
        );
    }
}
