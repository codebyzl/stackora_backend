package org.victor.stackora.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration.
 */
@Configuration
@MapperScan("org.victor.stackora.mapper")
public class MybatisPlusConfig {

    /**
     * Registers MyBatis-Plus interceptors.
     *
     * @return configured interceptor chain
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        // 超出总页数时不自动跳回第一页
        paginationInterceptor.setOverflow(false);

        // 防止单次请求查询过多数据
        paginationInterceptor.setMaxLimit(100L);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}