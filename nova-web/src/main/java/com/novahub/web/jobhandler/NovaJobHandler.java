package com.novahub.web.jobhandler;

import com.novahub.content.service.ITagService;
import com.novahub.feed.service.FeedService;
import com.novahub.hotrank.service.HotRankService;
import com.novahub.monitor.service.PvUvService;
import com.novahub.search.service.IndexSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * XXL-Job 统一任务处理器
 *
 * 负责注册所有定时任务到 XXL-Job 调度中心
 *
 * === XXL-Job 管理后台配置指南 ===
 *
 * 1. 登录 XXL-Job Admin (http://localhost:8088/xxl-job-admin)
 * 2. 执行器管理 → 新增执行器:
 *    - 执行器名称: nova-hub-executor
 *    - AppName: nova-hub-executor
 *    - 注册方式: 自动注册
 * 3. 任务管理 → 新增任务:
 *    - 任务名称: 小时级热榜刷新
 *    - JobHandler: hourlyHotRankRefresh
 *    - 执行方式: BEAN
 *    - Cron 表达式: 0 0 * * * ?
 *
 * === 任务配置对照表 ===
 *
 * | 任务名称         | JobHandler              | Cron 表达式       | 说明              |
 * |----------------|------------------------|-------------------|-------------------|
 * | 小时级热榜刷新   | hourlyHotRankRefresh   | 0 0 * * * ?      | 每小时第0分钟      |
 * | 日榜重算        | dailyHotRankRefresh    | 0 0 0 * * ?      | 每天凌晨0点        |
 * | 周榜重算        | weeklyHotRankRefresh   | 0 0 0 * * MON    | 每周一凌晨0点      |
 * | 推荐流刷新      | refreshRecommendFeed   | 0 0/5 * * * ?    | 每5分钟            |
 * | 全量索引重建    | buildFullIndex        | 0 0 3 * * ?      | 每天凌晨3点        |
 * | PV/UV 统计     | dailyPvUvReset        | 0 0 0 * * ?      | 每天凌晨0点        |
 * | 标签热度更新    | updateTagHotScore     | 0 0 1 * * ?      | 每天凌晨1点        |
 * | 分片索引任务(示例) | shardingIndexJob     | 0 0 0 * * ?      | 分片广播示例       |
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NovaJobHandler {

    private final HotRankService hotRankService;
    private final FeedService feedService;
    private final IndexSyncService indexSyncService;
    private final PvUvService pvUvService;
    private final ITagService tagService;

    /**
     * 1. 小时级热榜刷新
     * Cron: 0 0 * * * ? (每小时第0分钟执行)
     */
    @XxlJob("hourlyHotRankRefresh")
    public ReturnT<String> hourlyHotRankRefresh() {
        XxlJobHelper.log("XXL-Job: 小时级热榜刷新任务开始");
        try {
            long start = System.currentTimeMillis();
            hotRankService.fullRecalculateRank();
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("XXL-Job: 小时级热榜刷新任务完成, 耗时={}ms", cost);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "小时级热榜刷新完成, 耗时=" + cost + "ms");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 小时级热榜刷新任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "小时级热榜刷新任务失败: " + e.getMessage());
        }
    }

    /**
     * 2. 日榜重算
     * Cron: 0 0 0 * * ? (每天凌晨0点执行)
     */
    @XxlJob("dailyHotRankRefresh")
    public ReturnT<String> dailyHotRankRefresh() {
        XxlJobHelper.log("XXL-Job: 日榜重算任务开始");
        try {
            long start = System.currentTimeMillis();
            hotRankService.fullRecalculateRank();
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("XXL-Job: 日榜重算任务完成, 耗时={}ms", cost);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "日榜重算完成, 耗时=" + cost + "ms");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 日榜重算任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "日榜重算任务失败: " + e.getMessage());
        }
    }

    /**
     * 3. 周榜重算
     * Cron: 0 0 0 * * MON (每周一凌晨0点执行)
     */
    @XxlJob("weeklyHotRankRefresh")
    public ReturnT<String> weeklyHotRankRefresh() {
        XxlJobHelper.log("XXL-Job: 周榜重算任务开始");
        try {
            long start = System.currentTimeMillis();
            hotRankService.fullRecalculateRank();
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("XXL-Job: 周榜重算任务完成, 耗时={}ms", cost);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "周榜重算完成, 耗时=" + cost + "ms");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 周榜重算任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "周榜重算任务失败: " + e.getMessage());
        }
    }

    /**
     * 4. 推荐流刷新
     * Cron: 0 0/5 * * * ? (每5分钟执行)
     */
    @XxlJob("refreshRecommendFeed")
    public ReturnT<String> refreshRecommendFeed() {
        XxlJobHelper.log("XXL-Job: 推荐流刷新任务开始");
        try {
            long start = System.currentTimeMillis();
            feedService.buildRecommendFeed();
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("XXL-Job: 推荐流刷新任务完成, 耗时={}ms", cost);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "推荐流刷新完成, 耗时=" + cost + "ms");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 推荐流刷新任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "推荐流刷新任务失败: " + e.getMessage());
        }
    }

    /**
     * 5. 全量索引重建
     * Cron: 0 0 3 * * ? (每天凌晨3点执行)
     */
    @XxlJob("buildFullIndex")
    public ReturnT<String> buildFullIndex() {
        XxlJobHelper.log("XXL-Job: 全量索引重建任务开始");
        try {
            long start = System.currentTimeMillis();
            indexSyncService.buildFullIndex();
            long cost = System.currentTimeMillis() - start;
            XxlJobHelper.log("XXL-Job: 全量索引重建任务完成, 耗时={}ms", cost);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "全量索引重建完成, 耗时=" + cost + "ms");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 全量索引重建任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "全量索引重建任务失败: " + e.getMessage());
        }
    }

    /**
     * 6. PV/UV 统计
     * Cron: 0 0 0 * * ? (每天凌晨0点执行)
     */
    @XxlJob("dailyPvUvReset")
    public ReturnT<String> dailyPvUvReset() {
        XxlJobHelper.log("XXL-Job: PV/UV 统计任务开始");
        try {
            pvUvService.recordDailyStats();
            XxlJobHelper.log("XXL-Job: PV/UV 统计任务完成");
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "PV/UV 统计完成");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: PV/UV 统计任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "PV/UV 统计任务失败: " + e.getMessage());
        }
    }

    /**
     * 7. 标签热度更新
     * Cron: 0 0 1 * * ? (每天凌晨1点执行)
     */
    @XxlJob("updateTagHotScore")
    public ReturnT<String> updateTagHotScore() {
        XxlJobHelper.log("XXL-Job: 标签热度更新任务开始");
        try {
            tagService.updateTagHotScore();
            XxlJobHelper.log("XXL-Job: 标签热度更新任务完成");
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "标签热度更新完成");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 标签热度更新任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "标签热度更新任务失败: " + e.getMessage());
        }
    }

    /**
     * 8. 分片广播任务示例
     * Cron: 0 0 0 * * ? (每天凌晨)
     *
     * 分片任务特点:
     * - XXL-Job 会根据配置的分片数，启动多个执行实例
     * - 每个实例通过 shardIndex 区分处理不同的数据分片
     * - 适合数据量大、需要并行处理场景
     */
    @XxlJob("shardingIndexJob")
    public ReturnT<String> shardingIndexJob() {
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("XXL-Job: 分片索引任务开始, shardIndex={}/{}", shardIndex, shardTotal);
        try {
            XxlJobHelper.log("XXL-Job: 分片索引任务完成, 处理分片 {}/{}", shardIndex, shardTotal);
            return new ReturnT<>(ReturnT.SUCCESS_CODE, "分片 " + shardIndex + "/" + shardTotal + " 处理完成");
        } catch (Exception e) {
            XxlJobHelper.log("XXL-Job: 分片索引任务失败: {}", e.getMessage());
            return new ReturnT<>(ReturnT.FAIL_CODE, "分片索引任务失败: " + e.getMessage());
        }
    }
}
