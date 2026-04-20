package com.nongxinle.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 图片等资源根目录（对应旧项目 {@code location="file:///opt/tomcat/.../images/"} 的「images」这一层）。
     * 由 {@code application.properties} 的 {@code app.files.images-root} 配置，生产用外挂盘，发版不覆盖上传文件。
     */
    @Value("${app.files.images-root}")
    private String imagesRoot;

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

        // 图片与上传文件：根目录可指向 Tomcat app-data 等包外路径（见 application.properties 说明）
        mapImageSubdir(registry, "/goodsVideo/**", "goodsVideo");
        mapImageSubdir(registry, "/goodsImage/**", "goodsImage");
        mapImageSubdir(registry, "/uploadImage/**", "uploadImage");
        mapImageSubdir(registry, "/foodImage/**", "foodImage");
        mapImageSubdir(registry, "/userImage/**", "userImage");
        mapImageSubdir(registry, "/uploadClock/**", "uploadClock");
        mapImageSubdir(registry, "/stockImages/**", "stockImages");
        mapImageSubdir(registry, "/traceReports/**", "traceReports");
        mapImageSubdir(registry, "/ocrImages/**", "ocrImages");
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
