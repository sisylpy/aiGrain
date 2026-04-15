package com.nongxinle.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger3 API文档配置
 * 访问地址:
 *   - Swagger UI: http://localhost:8090/api/swagger-ui/index.html
 *   - API JSON:    http://localhost:8090/api/v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aigrain API 接口文档")
                        .version("1.0.0")
                        .description("农鑫 GB 模块 API 接口文档，包含批发商、部门、商品、订单等核心功能")
                        .contact(new Contact()
                                .name("Aigrain Team")
                                .email("support@aigrain.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8090/api").description("本地开发环境"),
                        new Server().url("http://192.168.0.102:8090/api").description("局域网测试环境")
                ));
    }
}
