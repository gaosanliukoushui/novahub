package com.novahub.recommend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.common.result.Result;
import com.novahub.recommend.entity.AbBucket;
import com.novahub.recommend.entity.AbExperiment;
import com.novahub.recommend.enums.ExperimentStatus;
import com.novahub.recommend.mapper.AbBucketMapper;
import com.novahub.recommend.mapper.AbExperimentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ab")
@RequiredArgsConstructor
@Tag(name = "A/B测试管理", description = "A/B测试实验配置管理")
public class ABTestController {

    private final AbExperimentMapper experimentMapper;
    private final AbBucketMapper bucketMapper;

    @GetMapping("/experiments")
    @Operation(summary = "查询所有实验", description = "获取所有A/B测试实验列表")
    public Result<List<AbExperiment>> listExperiments() {
        List<AbExperiment> experiments = experimentMapper.selectList(null);
        return Result.success(experiments);
    }

    @GetMapping("/experiments/{experimentId}")
    @Operation(summary = "查询实验详情", description = "获取指定实验的详细信息")
    public Result<Map<String, Object>> getExperiment(@PathVariable String experimentId) {
        AbExperiment experiment = experimentMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getExperimentId, experimentId)
        );

        if (experiment == null) {
            return Result.success(null);
        }

        List<AbBucket> buckets = bucketMapper.selectList(
                new LambdaQueryWrapper<AbBucket>()
                        .eq(AbBucket::getExperimentId, experimentId)
        );

        return Result.success(Map.of(
                "experiment", experiment,
                "buckets", buckets
        ));
    }

    @PostMapping("/experiments")
    @Operation(summary = "创建实验", description = "创建新的A/B测试实验")
    public Result<Void> createExperiment(@RequestBody Map<String, Object> params) {
        String experimentId = (String) params.get("experimentId");
        String name = (String) params.get("name");
        String description = (String) params.get("description");
        Double traffic = Double.parseDouble(params.get("traffic").toString());
        List<Map<String, Object>> bucketConfigs = (List<Map<String, Object>>) params.get("buckets");

        AbExperiment experiment = new AbExperiment();
        experiment.setExperimentId(experimentId);
        experiment.setName(name);
        experiment.setDescription(description);
        experiment.setTraffic(traffic);
        experiment.setStatus(ExperimentStatus.PENDING.getCode());
        experiment.setMetrics("[\"ctr\", \"like_rate\", \"share_rate\"]");
        experiment.setCreateTime(LocalDateTime.now());
        experiment.setUpdateTime(LocalDateTime.now());

        experimentMapper.insert(experiment);

        for (Map<String, Object> bucketConfig : bucketConfigs) {
            AbBucket bucket = new AbBucket();
            bucket.setExperimentId(experimentId);
            bucket.setBucketId((String) bucketConfig.get("bucketId"));
            bucket.setBucketName((String) bucketConfig.get("bucketName"));
            bucket.setWeight(Double.parseDouble(bucketConfig.get("weight").toString()));
            bucket.setConfig(bucketConfig.get("config") != null
                    ? com.alibaba.fastjson2.JSON.toJSONString(bucketConfig.get("config"))
                    : null);
            bucket.setDescription((String) bucketConfig.getOrDefault("description", ""));
            bucket.setCreateTime(LocalDateTime.now());

            bucketMapper.insert(bucket);
        }

        return Result.success();
    }

    @PutMapping("/experiments/{experimentId}/status")
    @Operation(summary = "更新实验状态", description = "启动/停止/结束实验")
    public Result<Void> updateExperimentStatus(
            @PathVariable String experimentId,
            @RequestBody Map<String, Object> params) {

        Integer status = Integer.parseInt(params.get("status").toString());

        AbExperiment experiment = experimentMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getExperimentId, experimentId)
        );

        if (experiment == null) {
            return Result.success();
        }

        experiment.setStatus(status);
        if (status == ExperimentStatus.RUNNING.getCode()) {
            experiment.setStartTime(LocalDateTime.now());
        } else if (status == ExperimentStatus.ENDED.getCode()) {
            experiment.setEndTime(LocalDateTime.now());
        }
        experiment.setUpdateTime(LocalDateTime.now());

        experimentMapper.updateById(experiment);

        return Result.success();
    }

    @GetMapping("/preview")
    @Operation(summary = "预览分流结果", description = "预览指定用户ID在实验中的分流结果")
    public Result<Map<String, Object>> previewAssignment(
            @RequestParam String experimentId,
            @RequestParam Long userId) {

        AbExperiment experiment = experimentMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getExperimentId, experimentId)
        );

        if (experiment == null) {
            return Result.success();
        }

        List<AbBucket> buckets = bucketMapper.selectList(
                new LambdaQueryWrapper<AbBucket>()
                        .eq(AbBucket::getExperimentId, experimentId)
        );

        int bucketIndex = Math.abs((userId.toString() + experimentId).hashCode()) % 100;
        int threshold = (int) (experiment.getTraffic() * 100);
        boolean inExperiment = bucketIndex < threshold;

        return Result.success(Map.of(
                "userId", userId,
                "experimentId", experimentId,
                "bucketIndex", bucketIndex,
                "threshold", threshold,
                "inExperiment", inExperiment,
                "assignedBucket", inExperiment ? "A" : "control"
        ));
    }
}
