package cn.enjoy.mall.config;


import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * @program enjoy-mi
 * @description:
 * @author: pengxuanyu
 * @create: 2020/11/06 23:18
 */
public class RedissionConfig {
    @Bean(name = "redissionClient",destroyMethod = "shutdown")
    public RedissonClient redissonClient() throws IOException{
        Config config = new Config();
        config.useSingleServer().setAddress("redis://");
        config.useSingleServer().setPassword("123456");
        config.useSingleServer().setConnectionPoolSize(5000);
        config.useSingleServer().setConnectionMinimumIdleSize(200);
        RedissonClient redission = RedissionConfig.create(config);
        return redission;
    }

    public RedissonClient redissonClient1() throws IOException{
        Config config = new Config();
        config.useSingleServer().setAddress("redis://");
        config.useSingleServer().setPassword("123456");
        // config.useSingleServer().setConnectionPoolSize(5000);
        // config.useSingleServer().setConnectionMinimumIdleSize(200);
        RedissonClient redission = RedissionConfig.create(config);
        return redission;
    }
}
