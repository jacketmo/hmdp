package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key,Object value,Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String KEY_PREFIX,ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        String key = KEY_PREFIX + id;
        //1.从redis查询缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(Json)) {
            //3.存在则返回
            return JSONUtil.toBean(Json,type);
        }
        //4.不存在则判断是否为空值
        if (Json != null) {
            return null;
        }
        //5.查询数据库
        R r = dbFallBack.apply(id);
        //6.不存在，返回错误
        if (r == null) {
            //将空值写入redis，防缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", time,unit);
            return null;
        }
        //7.存在，写入redis
        this.set(key,r,time,unit);
        //8.返回
        return r;
    }

    public <R,ID> R queryWithLogicExpire(String KEY_PREFIX,ID id,Class<R> type,Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        String key =KEY_PREFIX+ id;
        //1.从redis查询缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(Json)) {
            //3.不存在则返回空
            return null;
        }
        //命中则反序列化
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期则返回数据
            return r;
        }
        //过期，则缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断获取是否成功
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    //查询数据库
                    R r1 = dbFallBack.apply(id);
                    //写回redis
                    this.setWithLogicExpire(key,r1, time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //返回旧数据
        return r;
    }

    //互斥锁解决缓存击穿
    public <R,ID>R queryWithMutex(String KEY_PREFIX,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(Json)) {
            //3.存在则返回
            return JSONUtil.toBean(Json, type);
        }
        //4.不存在则判断是否为空值
        if (Json != null) {
            return null;
        }
        String lockKey = null;
        R r = null;
        try {
            //实现缓存重建
            //5.1获取互斥锁
            lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);
            //5.2判断是否成功
            if (!isLock) {
                //5.3失败则等待重试
                Thread.sleep(50);
                return queryWithMutex(KEY_PREFIX,id,type,dbFallback,time,unit);
            }
            //5.4成功 查询数据库
            r = dbFallback.apply(id);
            //6.不存在，返回错误
            if (r == null) {
                //将空值写入redis，防缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //7.存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //8.释放互斥锁
            unLock(lockKey);
        }


        //9.返回
        return r;
    }
    //互斥锁实现
    private boolean tryLock(String key) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
