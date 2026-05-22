-- NovaHub Feed 流压测脚本
-- 用法: wrk -t4 -c100 -d300s -s feed.lua http://localhost:9080

wrk.method = "GET"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Accept"] = "application/json"

-- 请求计数器，用于模拟翻页
local counter = 0
local last_id = 0

request = function()
    counter = counter + 1

    -- 每 20 个请求重置一次，模拟翻页
    if counter % 20 == 1 then
        last_id = 0
    else
        last_id = last_id + 1
    end

    local path = string.format("/api/feed/recommend?pageSize=20&lastId=%d", last_id)
    return wrk.format(nil, path, nil, nil)
end

response = function(status, headers, body)
    if status ~= 200 then
        print(string.format("[ERROR] Status: %d, Body: %s", status, body))
    end
end
