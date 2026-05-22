local userLikeKey = KEYS[1]
local contentLikeKey = KEYS[2]
local userId = ARGV[1]
local contentId = ARGV[2]

if redis.call('SISMEMBER', userLikeKey, contentId) == 0 then
    return 0
end

redis.call('SREM', userLikeKey, contentId)
redis.call('SREM', contentLikeKey, userId)

return 1
