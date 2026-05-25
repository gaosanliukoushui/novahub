package com.novahub.web.controller;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.Result;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.hotrank.service.HotRankService;
import com.novahub.search.service.IndexSyncService;
import com.novahub.content.service.IReviewService;
import com.novahub.user.service.IPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Tag(name = "管理员工程化操作")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final HotRankService hotRankService;
    private final IndexSyncService indexSyncService;
    private final IReviewService reviewService;
    private final IPermissionService permissionService;
    private final JdbcTemplate jdbcTemplate;

    @Operation(summary = "管理员：从数据库快照预热热榜")
    @PostMapping("/hotrank/prewarm")
    public Result<Map<String, Object>> prewarmHotRank(@RequestParam(defaultValue = "100") int limit) {
        requireAdmin();
        int count = hotRankService.prewarmFromDatabase(limit);
        return Result.ok(Map.of("prewarmed", count, "limit", limit));
    }

    @Operation(summary = "管理员：重建搜索索引")
    @PostMapping("/search/rebuild")
    public Result<Map<String, Object>> rebuildSearch() {
        requireAdmin();
        indexSyncService.buildFullIndex();
        return Result.ok(Map.of(
                "status", "submitted",
                "strategy", "bulk rebuild with standard analyzer; alias switch can be enabled for zero-downtime rebuild"
        ));
    }

    @Operation(summary = "管理员：重新导入演示数据")
    @PostMapping("/demo-data/reload")
    public Result<Map<String, Object>> reloadDemoData(
            @RequestParam(defaultValue = "db/sql/003_demo_data.sql") String path) {
        requireAdmin();
        Path sqlPath = resolveDemoSql(path);
        if (!Files.exists(sqlPath)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "演示数据脚本不存在: " + sqlPath);
        }

        int executed = 0;
        try {
            String sql = Files.readString(sqlPath, StandardCharsets.UTF_8);
            String normalized = sql.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("--"))
                    .reduce("", (left, right) -> left + System.lineSeparator() + right);
            for (String statement : normalized.split(";\\s*(\\r?\\n|$)")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    jdbcTemplate.execute(trimmed);
                    executed++;
                }
            }
        } catch (Exception e) {
            log.warn("重新导入演示数据失败: path={}, error={}", sqlPath, e.getMessage());
            throw new BusinessException(ResultCode.DB_ERROR, "重新导入演示数据失败: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("script", sqlPath.toString());
        result.put("statements", executed);
        return Result.ok(result);
    }

    @Operation(summary = "管理员：审核通过内容")
    @PostMapping("/content/{contentId}/approve")
    public Result<Map<String, Object>> approveContent(@org.springframework.web.bind.annotation.PathVariable Long contentId,
                                                      @RequestParam(defaultValue = "管理员审核通过") String remark) {
        requireAdmin();
        reviewService.processReview(contentId, true, remark);
        return Result.ok(Map.of("contentId", contentId, "approved", true));
    }

    @Operation(summary = "管理员：审核拒绝内容")
    @PostMapping("/content/{contentId}/reject")
    public Result<Map<String, Object>> rejectContent(@org.springframework.web.bind.annotation.PathVariable Long contentId,
                                                     @RequestParam(defaultValue = "内容不符合演示审核规则") String remark) {
        requireAdmin();
        reviewService.processReview(contentId, false, remark);
        return Result.ok(Map.of("contentId", contentId, "approved", false));
    }

    private Path resolveDemoSql(String path) {
        Path requested = Path.of(path);
        if (requested.isAbsolute()) {
            return requested.normalize();
        }

        Path workdirPath = Path.of("").toAbsolutePath().resolve(path).normalize();
        if (Files.exists(workdirPath)) {
            return workdirPath;
        }

        Path dockerInitPath = Path.of("/docker-entrypoint-initdb.d/003_demo_data.sql");
        if (Files.exists(dockerInitPath)) {
            return dockerInitPath;
        }
        return workdirPath;
    }

    private void requireAdmin() {
        Long userId = SecurityUtils.requireUserId();
        if (!permissionService.hasRole(userId, "ROLE_ADMIN")) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可以执行该操作");
        }
    }
}
