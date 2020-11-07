package cn.enjoy.mall.lock;

import org.redisson.api.RFuture;

import java.util.concurrent.TimeUnit;

/**
 * @program enjoy-mi
 * @description: redissionlock
 * @author: pengxuanyu
 * @create: 2020/11/07 12:38
 */
public class RedissionLock {
    private void lock(long lestTime, TimeUnit timeUnit, boolean interpered) throws InternalError{
        long threadId = Thread.currentThread().getId();
    }


    private Long tryAcquire(long lestTime,TimeUnit unit,long threadId){
        return get(tryAcquireAsync(lestTime,unit,threadId));
    }

    private <T> RFuture<Long> tryAcquireAsync(long lestTime, TimeUnit unit, long threadId){
        if(lestTime != -1){
            return tryAcquireAsync(lestTime,unit,threadId);
        }
        RFuture<Long> ttRemainFuture = tryLockInnerAsync();
    }
}
