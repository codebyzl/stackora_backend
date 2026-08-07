package org.victor.stackora.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.victor.stackora.common.ErrorCode;
import org.victor.stackora.exception.BusinessException;
import org.victor.stackora.mapper.UserAccountMapper;
import org.victor.stackora.model.entity.UserAccount;
import org.victor.stackora.model.enums.UserRole;
import org.victor.stackora.model.enums.UserStatus;
import org.victor.stackora.service.UserAccountService;
import org.victor.stackora.service.result.UserInfoResult;

import java.util.Locale;

/**
 * @author victorzl
 * @description 针对表【user_account(用户账号表)】的数据库操作Service实现
 * @createDate 2026-07-27 12:09:37
 */
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount>
        implements UserAccountService {


    private final UserAccountMapper userAccountMapper;

    private final PasswordEncoder passwordEncoder;


    @Override
    @Transactional
    public Long userRegister(String account, String rawPassword) {

        if (!StringUtils.hasText(account) || !StringUtils.hasText(rawPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 1.注册账户统一转化为小写
        String normalizedAccount = account.toLowerCase(Locale.ROOT);

        // 2.检查是否已有用户
//        if (lambdaQuery().eq(UserAccount::getAccount, normalizedAccount).exists()) {
//            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
//        }
        LambdaQueryWrapper<UserAccount> wrapper =
                Wrappers.lambdaQuery(UserAccount.class)
                        .eq(
                                UserAccount::getAccount,
                                normalizedAccount
                        );

        if (userAccountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ALREADY_EXISTS
            );
        }

        // 3.写入数据库
        UserAccount userAccount = new UserAccount();
        userAccount.setAccount(normalizedAccount);
        userAccount.setPasswordHash(passwordEncoder.encode(rawPassword));
        userAccount.setNickname(normalizedAccount);
        userAccount.setRole(UserRole.USER);
        userAccount.setStatus(UserStatus.ACTIVE);

        final boolean saved;

        try {
            saved = save(userAccount);

        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_ALREADY_EXISTS
            );
        }

        if (!saved || userAccount.getId() == null) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR
            );
        }

        // 5. 获取数据库回填的自增ID
        return userAccount.getId();
    }

    @Override
    public UserInfoResult userLogin(String account, String rawPassword) {

        // 1.校验非空
        if (!StringUtils.hasText(account) || !StringUtils.hasText(rawPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String normalizedAccount = account.toLowerCase(Locale.ROOT);

        // 2.数据库验证账号密码是否正确
        UserAccount userAccount = lambdaQuery().eq(UserAccount::getAccount, normalizedAccount).one();

        // 账号不存在或者密码不正确
        if (userAccount == null || !passwordEncoder.matches(
                rawPassword,
                userAccount.getPasswordHash()
        )) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_OR_PASSWORD_ERROR
            );
        }

        // 3.判断用户状态
        ensureAccountActive(userAccount.getStatus());

        return new UserInfoResult(
                userAccount.getId(),
                userAccount.getAccount(),
                userAccount.getNickname(),
                userAccount.getRole());
    }

    @Override
    public UserInfoResult getCurrentUserInfoById(Long userId) {

        // 1.校验非空
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.数据库查询用户信息
        UserAccount userAccount = lambdaQuery().eq(UserAccount::getId, userId).one();

        // 用户不存在
        if (userAccount == null) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_NOT_FOUND
            );
        }

        // 3.判断用户状态
        ensureAccountActive(userAccount.getStatus());

        return new UserInfoResult(
                userAccount.getId(),
                userAccount.getAccount(),
                userAccount.getNickname(),
                userAccount.getRole());
    }


    private static void ensureAccountActive(UserStatus status) {
        if (status == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        switch (status) {
            case ACTIVE -> {
                // 允许继续执行业务
            }
            case DISABLED -> throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            case CANCELLED -> throw new BusinessException(ErrorCode.ACCOUNT_CANCELLED);
        }
    }

}




