package com.nongxinle.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import com.nongxinle.utils.ImagePaths;
import com.nongxinle.utils.UploadFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    /** 图片根目录（{@code application.properties} 的 {@code app.files.images-root}） */
    private final String imagesRoot;

    /**
     * 在构造阶段写入 {@link UploadFile}，避免仅依赖 {@code @PostConstruct} 时在少数环境下早于首次请求未完成初始化
     *（会导致 {@code UploadFile.deleteFile} / {@code upload} 报 “Image upload root not initialized”）。
     */
    /** 占位符未解析时兜底（与 application.yml / .properties 默认值一致） */
    public WebMvcConfig(
            @Value("${app.files.images-root:file:/opt/tomcat/latest/app-data/images/}") String imagesRoot) {
        this.imagesRoot = imagesRoot;
        String normalized = UploadFile.normalizeConfiguredImagesRoot(imagesRoot);
        UploadFile.setImagesRootDirectory(normalized);
        log.info("Resolved app.files.images-root (upload + 静态子目录映射): [{}]", normalized);
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // SseEmitter 会先写出一段 text/plain 的 "event:…\ndata:" 前缀字符串，再写 JSON 正文。
        // 若仅有 FastJsonHttpMessageConverter 且其对 String+TEXT_PLAIN 也 canWrite，则可能把整段前缀再 JSON 引用，
        // curl/xxd 首字节会变成 0x22 且出现字面 5c6e（"\n"）。
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
        converters.add(new FastJsonHttpMessageConverter());
    }

    /**
     * 跨域预检（OPTIONS）必须在此 Filter 里放行；仅 {@link WebMvcConfigurer#addCorsMappings} 时，
     * 部分环境下对 {@code http://localhost:*} 的匹配可能不生效，浏览器会收到 <strong>403 Forbidden</strong>。
     * <p>
     * 本地开发：显式列出 Vite 默认端口 + 通配本机 HTTP 源；生产部署请改为环境变量白名单或去掉通配。
     * <p>
     * {@code allowCredentials=true}：前端 fetch/EventSource 使用 {@code credentials:'include'}（Cookie）时，
     * 浏览器要求响应带 {@code Access-Control-Allow-Credentials: true}；不可用 {@code Access-Control-Allow-Origin:*}。
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowCredentials(true);
        c.setMaxAge(3600L);
        c.addAllowedMethod("GET");
        c.addAllowedMethod("POST");
        c.addAllowedMethod("PUT");
        c.addAllowedMethod("PATCH");
        c.addAllowedMethod("DELETE");
        c.addAllowedMethod("OPTIONS");
        c.addAllowedMethod("HEAD");
        c.addAllowedHeader("*");
        c.addExposedHeader("Content-Type");
        // 显式源（与常见预检 Origin 完全一致，避免 pattern 解析差异导致 403）
        c.addAllowedOrigin("http://localhost:5173");
        c.addAllowedOrigin("http://127.0.0.1:5173");
        // 其它本机 dev 端口（Vite 预览、CRA 等）
        c.addAllowedOriginPattern("http://localhost:*");
        c.addAllowedOriginPattern("http://127.0.0.1:*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return new CorsFilter(source);
    }

    /**
     * 静态资源映射：与旧 MVC {@code mvc:resources} 对齐，URL 前缀下挂子目录。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger UI 静态资源
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
        
        // Swagger API docs
        registry.addResourceHandler("/v3/api-docs/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");

        // 图片与上传文件：子目录与 {@link ImagePaths#RESOURCE_MOUNTS} 一致
        for (ImagePaths.ResourceMount m : ImagePaths.RESOURCE_MOUNTS) {
            mapImageSubdir(registry, m.urlPattern(), m.folderName());
        }
    }

    /** 将 {@code /xxx/**} 映射到 {@code imagesRoot/xxx/} */
    private void mapImageSubdir(ResourceHandlerRegistry registry, String urlPattern, String folderName) {
        registry.addResourceHandler(urlPattern).addResourceLocations(resolveImageLocation(folderName));
    }

    private String resolveImageLocation(String folderName) {
        String base = imagesRoot == null ? "" : imagesRoot.trim();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        if (!base.startsWith("file:")) {
            base = "file:" + (base.startsWith("/") ? "" : "/") + base;
        }
        return base + folderName + "/";
    }
}
