-- NovaHub 热榜接口压测脚本
-- 用法: wrk -t4 -c50 -d180s -s hotrank.lua http://localhost:9080

wrk.method = "GET"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Accept"] = "application/json"

-- 模拟不同的榜单类型轮询
local types = { 0, 1, 2, 3 }
local type_index = 0

request = function()
    type_index = (type_index % #types) + 1
    local rank_type = types[type_index]
    local path = string.format("/api/hotrank?type=%d&limit=20", rank_type)
    return wrk.format(nil, path, nil, nil)
end

response = function(status, headers, body)
    if status ~= 200 then
        print(string.format("[ERROR] Status: %d", status))
    end
end
