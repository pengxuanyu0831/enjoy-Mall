package cn.enjoy.mall.lock;

import java.io.Serializable;
import java.util.UUID;

/**
 * @program enjoy-mi
 * @description:
 * @author: pengxuanyu
 * @create: 2020/11/01 16:56
 */
public class RedisLock {

    long timeOut = 0;

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
