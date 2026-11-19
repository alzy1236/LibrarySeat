package com.library.seat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * 前端静态资源配置
 *
 * <p>Vue 打包产物位于 classpath:/static/。使用 history 模式时，
 * 刷新 /student/home、/admin/dashboard 等深层路由会自动回退到 index.html，
 * 保证“项目启动后进入登录页、刷新页面也不出现 404”。</p>
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // 接口、WebSocket 等请求不参与前端回退
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")
                                || resourcePath.contains(".")) {
                            return null;
                        }
                        // 前端路由统一回退到 index.html
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}