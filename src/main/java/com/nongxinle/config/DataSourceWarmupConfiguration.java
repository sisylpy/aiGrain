package com.nongxinle.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 启动后预热连接池，避免首个 AI Run 卡在首次建连时放大与 /stop 的竞态。
 * <p>
 * {@code application.yml}: {@code app.datasource.warmup-enabled}，默认启用；不需要时可设为 false。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.datasource.warmup-enabled", havingValue = "true", matchIfMissing = true)
public class DataSourceWarmupConfiguration {

    @Bean
    public ApplicationRunner dataSourceWarmupRunner(DataSource dataSource) {
        return args -> {
            long t0 = System.currentTimeMillis();
            try (var conn = dataSource.getConnection()) {
                conn.isValid(5);
            }
            log.info("[DataSourceWarmup] first connection ok in {} ms", System.currentTimeMillis() - t0);
        };
    }
}
