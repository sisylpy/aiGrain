package com.nongxinle.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import com.nongxinle.utils.ImagePaths;
import com.nongxinle.utils.UploadFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    public WebMvcConfig(@Value("${app.files.images-root}") String imagesRoot) {
        this.imagesRoot = imagesRoot;
        String normalized = UploadFile.normalizeConfiguredImagesRoot(imagesRoot);
        UploadFile.setImagesRootDirectory(normalized);
        log.info("Resolved app.files.images-root (upload + 静态子目录映射): [{}]", normalized);
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new FastJsonHttpMessageConverter());
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
