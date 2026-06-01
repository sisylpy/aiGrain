package com.nongxinle;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.nongxinle.mapper")
public class AigrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(AigrainApplication.class, args);
    }
}
