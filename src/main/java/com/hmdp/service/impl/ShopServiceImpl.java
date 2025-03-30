package com.hmdp.service.impl;


import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result QueryById(Long id) {
        //解决缓存穿透的方案
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //使用互斥锁解决缓存击穿的方案
//        Shop shop = queryWithMutex(id);

        //使用逻辑过期解决缓存击穿的方案
        Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    //    //使用redis存储null值 解决缓存穿透
//    public Shop queryWithPassThrough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在则返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //4.不存在则判断是否为空值
//        if (shopJson != null) {
//            return null;
//        }
//        //5.查询数据库
//        Shop shop = getById(id);
//        //6.不存在，返回错误
//        if (shop == null) {
//            //将空值写入redis，防缓存穿透
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //7.存在，写入redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //8.返回
//        return shop;
//    }
//
//    //互斥锁解决缓存击穿
//    public Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            //3.存在则返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //4.不存在则判断是否为空值
//        if (shopJson != null) {
//            return null;
//        }
//        String lockKey = null;
//        Shop shop = null;
//        try {
//            //实现缓存重建
//            //5.1获取互斥锁
//            lockKey = LOCK_SHOP_KEY + id;
//            boolean isLock = tryLock(lockKey);
//            //5.2判断是否成功
//            if (!isLock) {
//                //5.3失败则等待重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //5.4成功 查询数据库
//            shop = getById(id);
//            //6.不存在，返回错误
//            if (shop == null) {
//                //将空值写入redis，防缓存穿透
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //7.存在，写入redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //8.释放互斥锁
//            unLock(lockKey);
//        }
//
//
//        //9.返回
//        return shop;
//    }

    //使用逻辑过期解决缓存击穿
//    public Shop queryWithLogicExpire(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            //3.不存在则返回空
//            return null;
//        }
//        //命中则反序列化
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //未过期则返回数据
//            return shop;
//        }
//        //过期，则缓存重建
//        //获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        //判断获取是否成功
//        if (isLock) {
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShopToRedis(id, 30L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unLock(lockKey);
//                }
//            });
//        }
//        //返回旧数据
//        return shop;
//    }

    //互斥锁实现
//    private boolean tryLock(String key) {
//        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(lock);
//    }
//
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }

//    //热key 缓存预热
//    private void saveShopToRedis(Long id, Long expireSeconds) {
//        Shop shop = getById(id);
//
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }
}
