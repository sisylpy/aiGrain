package com.nongxinle.config;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new FastJsonHttpMessageConverter());
    }

    /**
     * 配置静态资源映射
     * 允许通过URL访问本地图片目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Swagger UI 静态资源
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
        
        // Swagger API docs
        registry.addResourceHandler("/v3/api-docs/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
        
        // 图片资源映射 - 本地开发路径
        String imageDir = "/Users/lpy/Documents/javaWeb/kuangjia/aigrain/images/";
        
        registry.addResourceHandler("/foodImage/**")
                .addResourceLocations("file:" + imageDir + "foodImage/");
        registry.addResourceHandler("/goodsImage/**")
                .addResourceLocations("file:" + imageDir + "goodsImage/");
        registry.addResourceHandler("/userImage/**")
                .addResourceLocations("file:" + imageDir + "userImage/");
        registry.addResourceHandler("/uploadImage/**")
                .addResourceLocations("file:" + imageDir + "uploadImage/");
    }
}
