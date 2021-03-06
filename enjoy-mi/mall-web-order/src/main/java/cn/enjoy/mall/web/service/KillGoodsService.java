package cn.enjoy.mall.web.service;

import cn.enjoy.core.utils.GridModel;
import cn.enjoy.mall.constant.KillConstants;
import cn.enjoy.mall.lock.RedisLock;
import cn.enjoy.mall.model.KillGoodsPrice;
import cn.enjoy.mall.model.Order;
import cn.enjoy.mall.service.IKillOrderService;
import cn.enjoy.mall.service.manage.IKillSpecManageService;
import cn.enjoy.mall.vo.KillGoodsSpecPriceDetailVo;
import cn.enjoy.mall.vo.KillOrderVo;
import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀商品相关
 */
@Slf4j
@Service
public class KillGoodsService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    // 库存未初始化，redis里没有这个key
    public static final long UNINITIALIZED_STOCK = -3L;

    public static final String STOCK_LUA;

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

    private static String SEG_PREIX = "seg_";

    public Integer segment;

    private ConcurrentHashMap<String, Boolean> segMap = null;


    @PostConstruct
    public void initLockSegMap(){
        segMap = new ConcurrentHashMap<>(segment);
        for (int i = 1 ;i <= segment;i++ ){
            segMap.put(SEG_PREIX+i,true);
        }
    }

    // @Autowired
    // @Qualifier("redissionClient")
    //private RedissionClient redissionClient;

    private RedisLock redisLock;

    private RLock lock;
    public static String REIDS_KEY = "stock:stock";




    static {
        /*
        扣减库存的LUA脚本
        -1 ： 不限库存
        0 : 无库存
        >0 ： 库存数量

        return:
        -3 ：库存未初始化
        -2 ： 库存不足
        -1 ： 不限库存
        >=0 : 扣减之后的剩余库存
         */
        StringBuilder sb = new StringBuilder();
        sb.append("if (reids.call('exits',KEYS[1] == 1) then )");
        sb.append("    local stock = tonumber(redis.call ('get ',KEYS[1]))");
        sb.append("    local num = tonumber(ARGV[1])");
        sb.append("    if(stock == -1) then");
        sb.append("        return -1");
        sb.append("    end;");
        sb.append("    if(stock >= num) then  "  );
        sb.append("        return redis.call('incrby) KEYS[1] ,0-num");
        sb.append("    end;");
        sb.append("    return -2");
        sb.append("return -3");
        sb.append("end;");
        STOCK_LUA = sb.toString();

    }

    int i = 0;

    public int getI(){
        return i;
    }

    private  String getLockSegment(){
        List<String> stockFlag = new ArrayList<>();
        for (Map.Entry<String,Boolean> entry :segMap.entrySet()){
            if(entry.getValue()){
                stockFlag.add(entry.getKey());
            }
        }
        return stockFlag.get(new Random().nextInt(stockFlag.size()));
    }

    private boolean isStockEmpty(){
        for(Map.Entry<String,Boolean> entry : segMap.entrySet()){
            if(entry.getValue()){
                return false;
            }
            return true;
        }


    }

    /**
     * 避免缓存雪崩情况出现
     *
     * @param
     * @return
     * @throws Exception
     * @author Jack
     * @date 2020/9/4
     * @version
     */
    public GridModel<KillGoodsSpecPriceDetailVo> queryByPage() {
        //1、先从缓存里面拿
        GridModel<KillGoodsSpecPriceDetailVo> gridModel = (GridModel) redisTemplate.opsForValue().get(KillConstants.KILLGOODS_LIST);
        if (null != gridModel) {
            return gridModel;
        }

        //所有线程在这里等待，避免大量请求怼到数据库，只有获取锁成功的线程允许去查询数据库
        synchronized (iKillSpecManageService) {
            //1、获取到锁后，先从缓存里面拿
            gridModel = (GridModel) redisTemplate.opsForValue().get(KillConstants.KILLGOODS_LIST);
            if (null != gridModel) {
                return gridModel;
            }

            //2、缓存里面没有，再去数据库拿
            gridModel = iKillSpecManageService.queryView(1, 100);

            //3、如果数据库里面能拿到就设置到缓存中
            if (null != gridModel) {
                redisTemplate.opsForValue().set(KillConstants.KILLGOODS_LIST, gridModel, 50000, TimeUnit.MILLISECONDS);//set缓存
            }
        }
        return gridModel;
    }

    public KillGoodsSpecPriceDetailVo detail(Integer id) {
        String killgoodDetail = KillConstants.KILLGOOD_DETAIL + id;

        //1、先查询本地缓存有没有
        Cache killgoodsCache = cacheManager.getCache("killgoodDetail");
        KillGoodsSpecPriceDetailVo killGoodsPrice = null;

        //2、如果本地缓存中有，直接返回
        if (null != killgoodsCache.get(killgoodDetail)) {
            log.info(Thread.currentThread().getName() + "---------ehcache缓存中得到数据----------");
            killGoodsPrice = (KillGoodsSpecPriceDetailVo)killgoodsCache.get(killgoodDetail).getObjectValue();
            return killGoodsPrice;
        }

        //3、如果本地缓存中没有，则走redis缓存
        killGoodsPrice = (KillGoodsSpecPriceDetailVo) redisTemplate.opsForValue()
                .get(killgoodDetail);
        if (null != killGoodsPrice) {
            log.info(Thread.currentThread().getName() + "---------redis缓存中得到数据----------");
            return killGoodsPrice;
        }
        //4、本地缓存，redis缓存都没有，走数据库，防止缓存雪崩情况出现，这里加锁
        synchronized (iKillSpecManageService) {
            //2、如果本地缓存中有，直接返回
            if (null != killgoodsCache.get(killgoodDetail)) {
                log.info(Thread.currentThread().getName() + "---------ehcache缓存中得到数据----------");
                killGoodsPrice = (KillGoodsSpecPriceDetailVo)killgoodsCache.get(killgoodDetail).getObjectValue();
                return killGoodsPrice;
            }

            killGoodsPrice = (KillGoodsSpecPriceDetailVo) redisTemplate.opsForValue().get(killgoodDetail);
            if (null != killGoodsPrice) {
                log.info(Thread.currentThread().getName() + "---------redis缓存中得到数据----------");
                return killGoodsPrice;
            }
            // 从数据库里查数据
            killGoodsPrice = iKillSpecManageService.detailById(id);
            if (null != killGoodsPrice) {
                killgoodsCache.putIfAbsent(new Element(killgoodDetail,killGoodsPrice));
                //缓存2天，redis缓存时间比本地缓存长
                redisTemplate.opsForValue().set(killgoodDetail, killGoodsPrice, 2, TimeUnit.DAYS);//set缓存
            } else{
                // 防止缓存穿透
                redisTemplate.opsForValue().set(killgoodDetail, "null ", 5, TimeUnit.MINUTES);//set缓存

            }
        }
        return killGoodsPrice;
    }

/*    public KillGoodsSpecPriceDetailVo detail(Integer id) {
        String killgoodDetail = KillConstants.KILLGOOD_DETAIL + id;
        KillGoodsSpecPriceDetailVo killGoodsPrice = (KillGoodsSpecPriceDetailVo) redisTemplate.opsForValue()
                .get(killgoodDetail);
        if (null != killGoodsPrice) {
            log.info(Thread.currentThread().getName() + "---------缓存中得到数据----------");
            return killGoodsPrice;
        }
        synchronized (iKillSpecManageService) {
            killGoodsPrice = (KillGoodsSpecPriceDetailVo) redisTemplate.opsForValue().get(killgoodDetail);
            if (null != killGoodsPrice) {
                log.info(Thread.currentThread().getName() + "---------缓存中得到数据----------");
                return killGoodsPrice;
            }

            killGoodsPrice = iKillSpecManageService.detailById(id);
            if (null != killGoodsPrice) {
                redisTemplate.opsForValue().set(killgoodDetail, killGoodsPrice, 50000, TimeUnit.MILLISECONDS);//set缓存
            }
        }
        return killGoodsPrice;
    }*/

    /**
     * @param killId
     * @param userId
     * @return boolean
     * @throws Exception
     * @author Jack
     * @date 2020/8/3
     * @version
     */
    public boolean kill(String killId, String userId) {

        final String killGoodCount = KillConstants.KILL_GOOD_COUNT + killId;
        try {
            // 这就是在redis 里扣减库存
            long count = redisTemplate.opsForValue().increment(killGoodCount, -1);
            Object obj = redisTemplate.execute(new SessionCallback() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {

                    operations.watch(killGoodCount);
                    Object val = operations.opsForValue().get(killGoodCount);
                    int valint = Integer.valueOf(val.toString());

                    if (valint > 0) {
                        operations.multi();
                        operations.opsForValue().increment(killGoodCount, -1);
                        Object rs = operations.exec();
                        System.out.println(rs);
                        return rs;
                    }

                    return null;
                }
            });
            if (null != obj) {
                redisTemplate.opsForSet().add(KillConstants.KILLGOOD_USER, killId + userId);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * @param killId
     * @param userId
     * @return boolean
     * @throws Exception
     * @author Jack
     * @date 2020/8/4
     * @version
     */
    public boolean secKill(int killId, String userId) {
//        long is = redisTemplate.opsForSet().add(KillConstants.KILLED_GOOD_USER+killId,userId);
//        if (is == 0){//判断用户已经秒杀过，直接返回当次秒杀失败
////            return false;
//        }
        Boolean member = redisTemplate.opsForSet().isMember(KillConstants.KILLED_GOOD_USER + killId, userId);
        if (member) {
            logger.info("--------userId:" + userId + "--has secKilled");
            return false;
        }

        final String killGoodCount = KillConstants.KILL_GOOD_COUNT + killId;
        // increment 会出现 扣减的数量过多，无法补偿的情况
            if (redisTemplate.opsForValue().increment(killGoodCount, -1) < 0) {
            logger.info("--------Insufficient stock:------------");
            return false;
        }

        //秒杀成功，缓存秒杀成功的用户和商品到redis记录里
        redisTemplate.opsForSet().add(KillConstants.KILLGOOD_USER, killId + userId);
        return true;
    }

    /**
     * 基于数据库的秒杀实现
     *
     * @param
     * @return
     * @throws Exception
     * @author Jack
     * @date 2020/8/5
     * @version
     */
    public boolean secKillByDb(int killId, String userId) {

        //1、先判断有没有库存，没有库存就直接秒杀结束
        KillGoodsPrice kgp = killSpecManageService.selectByPrimaryKey(killId);
        if (kgp.getKillCount() <= 0) {
            logger.info("--------Insufficient stock:------------");
            return false;
        }

        //2、先判断该用户是否已经秒杀
        List<Order> orders = orderService.queryOrderByUserId(userId);
        if (orders != null && orders.size() > 0) {
            logger.info("--------userId:" + userId + "--has secKilled");
            return false;
        }

        KillGoodsPrice killGoodsPrice = new KillGoodsPrice();
        killGoodsPrice.setKillCount(1);
        killGoodsPrice.setId(killId);
        int i = killSpecManageService.updateSecKill(killGoodsPrice);

        //返回为0，秒杀完了
        if (i == 0) {
            logger.info("--------Insufficient stock:------------");
            return false;
        }

        //秒杀成功，缓存秒杀用户和商品
        redisTemplate.opsForSet().add(KillConstants.KILLGOOD_USER, killId + userId);
        return true;
    }

    public boolean chkKillOrder(String killId, String userId) {
        //校验用户和商品是否有缓存，无则表明当前是非法请求
        boolean isKilld = redisTemplate.opsForSet().isMember(KillConstants.KILLGOOD_USER, killId + userId);
        if (isKilld) {
            redisTemplate.opsForSet().remove(KillConstants.KILLGOOD_USER, killId + userId);
        }
        return isKilld;
    }

    public String submitOrder(Long addressId, int killId, String userId) {
        KillGoodsSpecPriceDetailVo killGoods = detail(killId);

        KillOrderVo vo = new KillOrderVo();
        vo.setUserId(userId);
        vo.setKillGoodsSpecPriceDetailVo(killGoods);
        vo.setAddressId(addressId);

        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();

        //订单有效时间3秒
        String kill_order_user = KillConstants.KILL_ORDER_USER + killId + userId;
        valueOperations.set(kill_order_user, KillConstants.KILL_ORDER_USER_UNDO, 3000, TimeUnit.MILLISECONDS);
        /*同步转异步，发送到消息队列*/
        secKillSender.send(vo);

        String orderId = "";
        try {
            while (true) {
                orderId = valueOperations.get(kill_order_user);
                if (null == orderId) {//处理超时，则直接置秒杀失败，取消秒杀订单
                    return null;
                }
                if (!KillConstants.KILL_ORDER_USER_UNDO.equals(orderId)) {//订单已处理成功
                    stringRedisTemplate.delete(kill_order_user);
                    return orderId.toString();//
                }
                Thread.sleep(300l);//300ms轮循1次
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String submitOrderByDb(Long addressId, int killId, String userId) {
        KillGoodsSpecPriceDetailVo killGoods = detail(killId);

        KillOrderVo vo = new KillOrderVo();
        vo.setUserId(userId);
        vo.setKillGoodsSpecPriceDetailVo(killGoods);
        vo.setAddressId(addressId);

        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();

        //订单有效时间3秒
        String kill_order_user = KillConstants.KILL_ORDER_USER + killId + userId;
        valueOperations.set(kill_order_user, KillConstants.KILL_ORDER_USER_UNDO, 3000, TimeUnit.MILLISECONDS);
        /*同步转异步，发送到消息队列*/
//        secKillSender.send(vo);
        Long orderId = orderService.killOrder(vo);

        String flag = valueOperations.get(kill_order_user);
        if (null == flag) {//处理超时，则直接置秒杀失败，取消秒杀订单
            orderService.cancel(orderId);
            stringRedisTemplate.delete(kill_order_user);
            return null;
        }
        return orderId + "";
    }

    public boolean secKillGoodsByLock(int killId, String userId){
        Boolean member = redisTemplate.opsForSet().isMember(KillConstants.KILLED_GOOD_USER + killId, userId);
        if (member) {
            logger.info("--------userId:" + userId + "--has secKilled");
            return false;
        }
        // fanhu
        String killGoodCount = KillConstants.KILL_GOOD_COUNT + killId;

        // 返回的数值，是已经执行完LUA脚本之后的数据
        // 整个扣减库存的核心，是这一行代码
        long stock = stock(killGoodCount,1);

        if(stock == UNINITIALIZED_STOCK){
            Timer timer = null;
            RedisLock redisLock = new RedisLock(redisTemplate,REIDS_LOCK);
            try {
                // 这个地方如果有大量请求访问数据库，故此，在这里考虑加一个分布式锁
                // TODO 分布式锁
                if(redisLock.tryLock) {

                    // 给竞争到锁的线程，增加持有锁的时间,防止死锁
                    timer= contiuneLock(REIDS_KEY);
                    stock = stock(killGoodCount, 1);
                    if (stock == UNINITIALIZED_STOCK) {
                        KillGoodsPrice killGoodsPrice = iKillSpecManageService.selectByPrimaryKey(killId);
                        redisTemplate.opsForValue().set(killGoodCount, killGoodsPrice.getKillCount(), 60 * 60, TimeUnit.MINUTES);
                        // 返回-3，未初始化这个key，所以下面再执行一次LUA脚本去扣减库存
                        stock = stock(killGoodCount, 1);
                    }
                }
            } catch (Exception e) {
                logger.error(e.getMessage(),e);
            }finally {
                if(timer != null){
                    timer.cancel();
                }
            }
            redisLock.unlock();
        }
        // 大于等于0，表示秒杀成功
        boolean flag = stock >=0;
        if(flag){
            redisTemplate.opsForSet().add(KillConstants.KILLGOOD_USER, killId + userId);

        }
        return flag;

    }

    public Long stock(String key ,int num){
        List<String> keys = new ArrayList<>();
        keys.add(key);

        List<String> args = new ArrayList<>();
        args.add(Integer.toString(num));


        long result = (long)redisTemplate.execute(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                Object nativeConnection = connection.getNativeConnection();
                if(nativeConnection instanceof JedisCluster){
                    return (long) ((JedisCluster) nativeConnection).eval(STOCK_LUA , keys, args);
                }else if(nativeConnection instanceof Jedis){
                    return (long) ( ((Jedis) nativeConnection).eval(STOCK_LUA,keys,args));
                }
                return UNINITIALIZED_STOCK;
            }
        });
        return result;
    }


    private Timer contiuneLock(String lockValue){
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                redisTemplate.expire(lockKey,60,TimeUnit.SECONDS);
            }
        },0,1);
    }

    public boolean secKillByRedissionLock(int killId,String userId){
        Boolean member = redisTemplate.opsForSet().isMember(KillConstants.KILLED_GOOD_USER + killId, userId);
        if (member) {
            logger.info("--------userId:" + userId + "--has secKilled");
            return false;
        }
        final String killGoodCount = KillConstants.KILL_GOOD_COUNT + killId;
        long stock = stock(killGoodCount ,1);
        if(stock == UNINITIALIZED_STOCK){
            // 获取到锁，""里的就是锁的key
            RLock lock = redissionClient.getLock("stock_lock_on_order");
            try{
                // 获取锁，并在2分钟之后释放锁
                lock.lock(2,TimeUnit.SECONDS);
                // 双重验证
                stock = stock(killGoodCount,-1);
                if(stock ==UNINITIALIZED_STOCK){
                    KillGoodsService killGoodsService = iKillSpecManageService.selectByPrimaryKey( );

                }
            }catch (Exception e){

            }

        }
    }

    // 除了Lua脚本，直接用redis的分布式锁也可以实现分布式锁的需求
    public boolean secKillGoodsByRedissionLockNoLua(int killId , String userId){
        Boolean member = redisTemplate.opsForSet().isMember(KillConstants.KILLED_GOOD_USER + killId, userId);
        if (member) {
            logger.info("--------userId:" + userId + "--has secKilled");
            return false;
        }
        // fanhu
        final  String killGoodCount = KillConstants.KILL_GOOD_COUNT + killId;
        RLock lock = redissionClient.getLock("stock_lock_on_order");
        if(redisTemplate.hasKey(killGoodCount)){
            lock.lock(2,TimeUnit.SECONDS);
            try{
                // 获取初始化时的库存
                KillGoodsPrice killGoodsPrice = iKillSpecManageService.selectByPrimaryKey(killId);
                // 把库存缓存进redis里
                redisTemplate.opsForValue().set(killGoodCount, killGoodsPrice.getKillCount(), 60 * 60, TimeUnit.MINUTES);
            }finally {
                lock.unlock();
            }
        }
        lock.lock(2,TimeUnit.SECONDS);
        try{
            // 1 避免库存出现负数，先查库存
            Integer stock  = (Integer) redisTemplate.opsForValue().get(killGoodCount);
            if(stock <= 0){
                logger.info("库存不足");
                return  false;
            }if(redisTemplate.opsForValue().increment(killGoodCount,-1)>=0){
                redisTemplate.opsForValue().add(KillConstants.KILL_ORDER_USER,killId+ userId);
                return true;
            }
        }finally {
            lock.unlock();
        }
        return false;
    }


}
