package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * mvc配置
 *
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登陆拦截器
        registry
                .addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code"
                        , "/user/login"
                        , "/blog/hot"
                        , "/shop/**"
                        , "/shop-type/**"
                        , "/upload/**"
                        , "/voucher/**"
                )
                .order(1);
        //Token续命刷新器
        registry
                .addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);
    }
}