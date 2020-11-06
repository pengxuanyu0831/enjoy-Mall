package cn.enjoy.mall.config;


import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @program enjoy-mi
 * @description:
 * @author: pengxuanyu
 * @create: 2020/11/06 23:18
 */
public class Redission {
    @Bean(name = "redissionClient",destroyMethod = "shutdown")
    public RedissonClient redissonClient() throws IOException{
        Config config = new Config();
        config.useSingleServer().setAddress("redis://");
        config.useSingleServer().setPassword("123456");
        config.useSingleServer().setConnectionPoolSize(5000);
        config.useSingleServer().setConnectionMinimumIdleSize(200);
        RedissonClient redission = Redission.create(config);
        return redission;
    }
}
