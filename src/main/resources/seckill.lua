local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get',stockKey))<=0) then
    return 1
end

if (redis.call('sismember',orderKey,userId)) then
    return 2
end

redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)
-- 发送消息到消息队列中
redis.call('xadd','stream.order','*','userId',userId,'voucherId',voucherId,'Id',orderId)
return 0