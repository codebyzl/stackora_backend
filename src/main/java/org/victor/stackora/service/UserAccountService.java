package org.victor.stackora.service;


/**
 * @author victorzl
 * @description 针对表【user_account(用户账号表)】的数据库操作Service
 * @createDate 2026-07-27 12:09:37
 */
public interface UserAccountService {


    /**
     * 用户注册
     *
     * @param account
     * @param rawPassword
     * @return 用户id
     */
    Long userRegister(String account, String rawPassword);


}
