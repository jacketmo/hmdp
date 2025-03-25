package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShopTypeList() {
        String key = CACHE_SHOP_TYPE_KEY; // CACHE_SHOP_TYPE_KEY = "cache:shopType";
        //1.首先查询redis中是否有该数据
        List<String> shopTypeListJson = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //2.有该数据，返回
        if (shopTypeListJson!=null&&!shopTypeListJson.isEmpty()){
            ArrayList<ShopType> shopTypes = new ArrayList<>();
            for (String s : shopTypeListJson) {
                shopTypes.add(JSONUtil.toBean(s,ShopType.class));
            }
            return Result.ok(shopTypes);
        }
        //3.无该数据，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //4.若数据库无该数据，返回错误
        if (shopTypes==null||shopTypes.isEmpty()){
            return Result.fail("分类不存在！");
        }
        //5.若数据库有该数据，则先添加到redis中
        for (ShopType shopType : shopTypes) {
            stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopType));
        }

        //6.返回该数据
        return Result.ok(shopTypes);
    }
}
