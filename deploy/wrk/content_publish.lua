-- content_publish.lua
-- 内容发布请求的 body 生成脚本

counter = 0

request = function()
    counter = counter + 1
    local body = string.format([[{
        "title": "JMeter 压测内容 %d",
        "content": "这是自动化压测生成的内容，用于验证内容发布接口的性能表现。",
        "type": 1,
        "tagIds": [1, 4],
        "status": 1
    }]], counter)

    return wrk.format("POST", "/api/contents", {
        ["Content-Type"] = "application/json",
        ["Authorization"] = "Bearer test-token",
        ["X-Request-Id"] = counter
    }, body)
end
