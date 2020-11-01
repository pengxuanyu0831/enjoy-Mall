package cn.enjoy.mall.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.UUID;

/**
 * @program enjoy-mi
 * @description:
 * @author: pengxuanyu
 * @create: 2020/11/01 16:56
 */

public class RedisLock {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private RedisTemplate<String, Object> redisTemplate;


    public static final String NX  = "NX";

    private String lockValue;
    private String lockKey;



    // 默认超时时间(ms)
    private static final long TIME_OUT = 100;
    private long timeOut = TIME_OUT;
    // 默认锁的有效时间
    private static final int EXPIRE = 60;
    private int expireTime = EXPIRE;

    public static final String  LUA_UNLOCK;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append("if redis.call(\"get\",KEYS[1]) == ARGS[1]");
        sb.append("then ");
        sb.append("    return redis.call(\"del\",KEYS[1])");
        sb.append("else");
        sb.append("    return 0");
        sb.append("end");
        LUA_UNLOCK = sb.toString();

    }

    /*
    获取锁的方法
     */
    public boolean tryLock(){
        Serializable lockValue = UUID.randomUUID().toString();
        // 系统时间
        long nowTime = System.nanoTime();
        // 超时时间
        long timeout = timeOut * 100000;
        while((System.nanoTime() - nowTime) < timeout){
            if(this.set(lockKey,lockValue,expireTime)){
                locked = true;
                return locked;
            }
            Thread.sleep(10,50000);
        }
        return locked;
    }
}
