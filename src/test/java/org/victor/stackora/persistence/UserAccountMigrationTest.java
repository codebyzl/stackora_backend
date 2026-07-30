package org.victor.stackora.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class UserAccountMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证 Flyway 是否成功创建 user_account 表。
     */
    @Test
    void migrationShouldCreateUserAccountTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = 'user_account'
                        """,
                Integer.class
        );

        assertEquals(1, tableCount);
    }

    /**
     * 验证关键字段是否创建。
     */
    @Test
    void userAccountTableShouldContainRequiredColumns() {
        assertColumnExists("id");
        assertColumnExists("account");
        assertColumnExists("password_hash");
        assertColumnExists("nickname");
        assertColumnExists("role");
        assertColumnExists("status");
        assertColumnExists("created_at");
        assertColumnExists("updated_at");
        assertColumnExists("cancelled_at");
    }

    private void assertColumnExists(String columnName) {
        Integer columnCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'user_account'
                          AND column_name = ?
                        """,
                Integer.class,
                columnName
        );

        assertEquals(
                1,
                columnCount,
                "缺少数据库字段：" + columnName
        );
    }
}