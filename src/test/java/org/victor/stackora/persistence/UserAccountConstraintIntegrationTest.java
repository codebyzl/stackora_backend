package org.victor.stackora.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class UserAccountConstraintIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 唯一索引应当阻止重复账号。
     */
    @Test
    void duplicateAccountShouldBeRejected() {
        String account = randomAccount();

        insertValidUser(account);

        assertThrows(
                DuplicateKeyException.class,
                () -> insertValidUser(account)
        );
    }


    /**
     * 账号格式约束应当阻止非法账号。
     */
    @Test
    void invalidAccountFormatShouldBeRejected() {
        assertThrows(
                UncategorizedSQLException.class,
                () -> insertValidUser("invalid-account!")
        );
    }

    /**
     * 数据库只允许保存小写账号。
     */
    @Test
    void uppercaseAccountShouldBeRejected() {
        assertThrows(
                UncategorizedSQLException.class,
                () -> insertValidUser("Testaccount")
        );
    }

    /**
     * 非空约束应当阻止 nickname 为 null。
     */
    @Test
    void nullNicknameShouldBeRejected() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO user_account
                                    (account, password_hash, nickname, role, status)
                                VALUES (?, ?, ?, ?, ?)
                                """,
                        randomAccount(),
                        "encoded-password",
                        null,
                        0,
                        0
                )
        );
    }

    /**
     * 非空约束应当阻止 account 为 null。
     */
    @Test
    void nullAccountShouldBeRejected() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO user_account
                                    (account, password_hash, nickname, role, status)
                                VALUES (?, ?, ?, ?, ?)
                                """,
                        null,
                        "encoded-password",
                        "account",
                        0,
                        0
                )
        );
    }

    /**
     * role 只能为 0 或 1。
     */
    @Test
    void invalidRoleShouldBeRejected() {
        assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO user_account
                                    (account, password_hash, nickname, role, status)
                                VALUES (?, ?, ?, ?, ?)
                                """,
                        randomAccount(),
                        "encoded-password",
                        "test-user",
                        2,
                        0
                )
        );
    }

    /**
     * status 只能为 0、1 或 2。
     */
    @Test
    void invalidStatusShouldBeRejected() {
        assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO user_account
                                    (account, password_hash, nickname, role, status)
                                VALUES (?, ?, ?, ?, ?)
                                """,
                        randomAccount(),
                        "encoded-password",
                        "test-user",
                        0,
                        3
                )
        );
    }

    /**
     * 注销状态必须同时存在注销时间。
     */
    @Test
    void cancelledStatusWithoutCancelledAtShouldBeRejected() {
        assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO user_account
                                    (account, password_hash, nickname,
                                     role, status, cancelled_at)
                                VALUES (?, ?, ?, ?, ?, ?)
                                """,
                        randomAccount(),
                        "encoded-password",
                        "test-user",
                        0,
                        2,
                        null
                )
        );
    }

    /**
     * 正常状态不能设置注销时间。
     */
    @Test
    void activeStatusWithCancelledAtShouldBeRejected() {
        assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate.update(
                        """
                                INSERT INTO user_account
                                    (account, password_hash, nickname,
                                     role, status, cancelled_at)
                                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(3))
                                """,
                        randomAccount(),
                        "encoded-password",
                        "test-user",
                        0,
                        0
                )
        );
    }

    private void insertValidUser(String account) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_account
                            (account, password_hash, nickname, role, status)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                account,
                "encoded-password",
                account,
                0,
                0
        );
    }

    private String randomAccount() {
        return "test_" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
    }
}