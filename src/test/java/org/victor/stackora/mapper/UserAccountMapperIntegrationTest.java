package org.victor.stackora.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.victor.stackora.model.entity.UserAccount;
import org.victor.stackora.model.enums.UserRole;
import org.victor.stackora.model.enums.UserStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserAccountMapperIntegrationTest {

    @Autowired
    private UserAccountMapper userAccountMapper;

    /**
     * 验证 Mapper 新增和主键回填。
     */
    @Test
    void insertShouldSaveUserAndFillId() {
        UserAccount user = createUser();

        int affectedRows = userAccountMapper.insert(user);

        assertEquals(1, affectedRows);
        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);
    }

    /**
     * 验证 Mapper 根据主键查询。
     */
    @Test
    void selectByIdShouldReturnSavedUser() {
        UserAccount user = createUser();
        userAccountMapper.insert(user);

        UserAccount savedUser =
                userAccountMapper.selectById(user.getId());

        assertNotNull(savedUser);
        assertEquals(user.getAccount(), savedUser.getAccount());
        assertEquals(user.getPasswordHash(), savedUser.getPasswordHash());
        assertEquals(UserRole.USER, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());

        // 验证数据库默认时间生效
        assertNotNull(savedUser.getCreatedAt());
        assertNotNull(savedUser.getUpdatedAt());
    }

    /**
     * 验证 Mapper 修改用户。
     */
    @Test
    void updateByIdShouldUpdateNickname() {
        UserAccount user = createUser();
        userAccountMapper.insert(user);

        user.setNickname("new-nickname");

        int affectedRows =
                userAccountMapper.updateById(user);

        assertEquals(1, affectedRows);

        UserAccount updatedUser =
                userAccountMapper.selectById(user.getId());

        assertNotNull(updatedUser);
        assertEquals(
                "new-nickname",
                updatedUser.getNickname()
        );
    }

    /**
     * 验证 Mapper 删除用户。
     */
    @Test
    void deleteByIdShouldDeleteUser() {
        UserAccount user = createUser();
        userAccountMapper.insert(user);

        int affectedRows =
                userAccountMapper.deleteById(user.getId());

        assertEquals(1, affectedRows);

        UserAccount deletedUser =
                userAccountMapper.selectById(user.getId());

        assertNull(deletedUser);
    }

    private UserAccount createUser() {
        String account = randomAccount();

        UserAccount user = new UserAccount();
        user.setAccount(account);
        user.setPasswordHash("encoded-password");
        user.setNickname(account);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);

        return user;
    }

    private String randomAccount() {
        return "test_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
    }
}