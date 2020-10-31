package cn.enjoy.mall.web.service;

import cn.enjoy.mall.constant.KillConstants;
import cn.enjoy.mall.service.IKillOrderService;
import cn.enjoy.mall.service.manage.IKillSpecManageService;
import cn.enjoy.mall.vo.KillGoodsSpecPriceDetailVo;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

public class TestKillGoodsService {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private IKillSpecManageService iKillSpecManageService;
    @Resource
    private RedisTemplate redisTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private SecKillSender secKillSender;

    @Autowired
    private IKillOrderService orderService;

    @Autowired
    private IKillSpecManageService killSpecManageService;

    @Autowired
    private CacheManager cacheManager;

    // 判断客户是不是参与过秒杀
    public boolean ifKilled(int killId,String userId){
        Boolean member = redisTemplate.opsForSet().isMember(KillConstants.KILLED_GOOD_USER + killId,userId);
        if(member){
            logger.info(userId + " 客户仅允许参加一次秒杀");
            return false;
        }
        final String killGoodsCount = KillConstants.KILL_GOOD_COUNT + killId;
        // 从redis里取数，做一个判断
        if (redisTemplate.opsForValue().increment(killGoodsCount,-1)<0){
            logger.info("==============余量不足，秒杀失败=============");
            return false;
        }

        redisTemplate.opsForSet().add(KillConstants.KILLGOOD_USER ,userId + killId);
        return true;

    }

    public KillGoodsSpecPriceDetailVo detailVo(Integer id){
        // 这个东西是被需要查询的数据
        String killgoodDetail = KillConstants.KILLGOOD_DETAIL + id;

        // 1 先从本地缓存里拿数据
        Cache killGoodsCache = cacheManager.getCache("killGoodsCache");
        KillGoodsSpecPriceDetailVo killGoodsPrice = null;
        // 2 判断本地缓存里是不是有这个数据
        if(null != killGoodsCache.get(killGoodsPrice)){
            logger.info(Thread.currentThread().getName() + "=======从本地缓存查询是否存在======");
            killGoodsPrice = (KillGoodsSpecPriceDetailVo)killGoodsCache.get(killgoodDetail).getObjectValue();
            return killGoodsPrice;
        }

        // 3 如果没有本地缓存，那么去redis的缓存里查询
        killGoodsPrice = (KillGoodsSpecPriceDetailVo)redisTemplate.opsForValue().get(killgoodDetail);
        if(null != killGoodsPrice){
            logger.info(Thread.currentThread().getName() + "==========从redis的缓存里找数据=========");
            return killGoodsPrice;
    }

        // 4如果redis 里也没有数据，就从数据库里找,但是为了防止雪崩现象发生，给被查询的数据上锁
        synchronized (iKillSpecManageService){
            if (null != killGoodsCache.get(killGoodsPrice)){
                logger.info(Thread.currentThread().getName() + " =======从本地缓存中获取数据=======");
                killGoodsPrice = (KillGoodsSpecPriceDetailVo)killGoodsCache.get(killgoodDetail).getObjectValue();
                return killGoodsPrice;
            }
            // 如果要从redis 里拿数据
            killGoodsPrice =(KillGoodsSpecPriceDetailVo)redisTemplate.opsForValue().get(killgoodDetail);
            if(null != killGoodsPrice){
                logger.info(Thread.currentThread().getName() + "==========从redis里拿数据=======");
                return killGoodsPrice;
            }
            // 这次是真的从数据库里取数据
            killGoodsPrice = iKillSpecManageService.detailById(id );
            if(null != killGoodsPrice){
                // Elment 是一个键值对
                killGoodsCache.putIfAbsent(new Element(killgoodDetail,killGoodsPrice));
                // 在redis里缓存两天
                redisTemplate.opsForValue().set(killgoodDetail,killGoodsPrice,2, TimeUnit.DAYS);
            }else{
                redisTemplate.opsForValue().set(killgoodDetail,killGoodsPrice,5,TimeUnit.MICROSECONDS);
            }


        }
        return killGoodsPrice;

        }


}
