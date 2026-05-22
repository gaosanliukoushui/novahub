package com.novahub.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.content.client.UserClient;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.hotrank.service.HotRankService;
import com.novahub.interaction.service.ILikeService;
import com.novahub.recommend.algorithm.ABTestEngine;
import com.novahub.recommend.algorithm.CollaborativeFilteringEngine;
import com.novahub.recommend.algorithm.ContentBasedEngine;
import com.novahub.recommend.dto.RecommendRequest;
import com.novahub.recommend.entity.AbBucket;
import com.novahub.recommend.entity.AbExperiment;
import com.novahub.recommend.entity.UserRecommendBehavior;
import com.novahub.recommend.enums.RecommendType;
import com.novahub.recommend.kafka.RecommendEventProducer;
import com.novahub.recommend.mapper.AbBucketMapper;
import com.novahub.recommend.mapper.AbExperimentMapper;
import com.novahub.recommend.mapper.UserRecommendBehaviorMapper;
import com.novahub.recommend.service.IRecommendService;
import com.novahub.recommend.service.RecommendFilterService;
import com.novahub.recommend.vo.ExperimentInfoVO;
import com.novahub.recommend.vo.RecommendItemVO;
import com.novahub.recommend.vo.RecommendResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements IRecommendService {

    private final CollaborativeFilteringEngine cfEngine;
    private final ContentBasedEngine cbEngine;
    private final ABTestEngine abTestEngine;
    private final RecommendFilterService filterService;
    private final HotRankService hotRankService;
    private final RedisUtils redisUtils;
    private final ContentMapper contentMapper;
    private final UserClient userClient;
    private final ILikeService likeService;
    private final RecommendEventProducer recommendEventProducer;
    private final AbExperimentMapper experimentMapper;
    private final AbBucketMapper bucketMapper;
    private final UserRecommendBehaviorMapper behaviorMapper;

    private static final String RECOMMEND_CACHE_KEY = "recommend:result:%d:%s";
    private static final int DEFAULT_RECOMMEND_SIZE = 100;

    @Override
    public RecommendResponseVO getRecommendations(RecommendRequest request) {
        Long userId = request.getUserId() != null
                ? request.getUserId()
                : SecurityUtils.getUserId();

        String recommendType = request.getRecommendType() != null
                ? request.getRecommendType()
                : "hybrid";

        String requestId = UUID.randomUUID().toString();
        log.info("获取推荐: userId={}, type={}, requestId={}", userId, recommendType, requestId);

        // 1. 确定推荐策略和权重
        ABTestEngine.RecommendWeights weights = ABTestEngine.RecommendWeights.defaultWeights();
        ExperimentInfoVO experimentInfo = null;

        if (Boolean.TRUE.equals(request.getNeedExperiment()) && userId != null) {
            experimentInfo = resolveExperiment(userId, request.getExperimentId());
            if (experimentInfo != null) {
                weights = abTestEngine.getRecommendWeights(
                        new ABTestEngine.BucketAssignment(
                                experimentInfo.getExperimentId(),
                                experimentInfo.getBucketId(),
                                experimentInfo.getExperimentName(),
                                experimentInfo.getBucketName()
                        )
                );
                log.debug("A/B测试分组: userId={}, expId={}, bucket={}, weights={}",
                        userId, experimentInfo.getExperimentId(),
                        experimentInfo.getBucketId(), weights);
            }
        }

        // 2. 生成推荐候选
        List<Long> candidateIds = generateCandidates(userId, RecommendType.fromCode(recommendType), weights);

        // 3. 过滤
        List<Long> filteredIds = filterService.filter(
                userId,
                candidateIds,
                RecommendFilterService.FilterConfig.defaultConfig()
        );

        // 4. 分页
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        int pageNum = request.getPageNum() != null ? request.getPageNum() : 1;
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filteredIds.size());

        List<Long> pagedIds = fromIndex < filteredIds.size()
                ? filteredIds.subList(fromIndex, toIndex)
                : Collections.emptyList();

        // 5. 加载内容详情
        List<RecommendItemVO> items = buildRecommendItems(pagedIds, userId, recommendType);

        // 6. 异步记录曝光事件
        if (Boolean.TRUE.equals(request.getNeedExperiment()) && userId != null) {
            recordExposuresAsync(userId, pagedIds, recommendType, experimentInfo, requestId);
        }

        return RecommendResponseVO.builder()
                .list(items)
                .total((long) filteredIds.size())
                .pageNum(pageNum)
                .pageSize(pageSize)
                .experiment(experimentInfo)
                .requestId(requestId)
                .build();
    }

    /**
     * 生成推荐候选列表
     */
    private List<Long> generateCandidates(Long userId, RecommendType type,
                                          ABTestEngine.RecommendWeights weights) {
        int candidateSize = DEFAULT_RECOMMEND_SIZE;

        switch (type) {
            case COLLABORATIVE_FILTER: {
                List<Long> cfResults = cfEngine.generateRecommendations(userId, candidateSize);
                return cfResults.isEmpty()
                        ? cbEngine.generateRecommendations(userId, candidateSize, null)
                        : cfResults;
            }
            case CONTENT_BASED: {
                return cbEngine.generateRecommendations(userId, candidateSize, null);
            }
            case HOT: {
                return getHotContentIds(candidateSize);
            }
            case HYBRID: {
                return generateHybridCandidates(userId, candidateSize, weights);
            }
        }

        return Collections.emptyList();
    }

    /**
     * 混合推荐
     */
    private List<Long> generateHybridCandidates(Long userId, int size,
                                                ABTestEngine.RecommendWeights weights) {
        // 检查是否为冷启动用户（无点赞行为）
        boolean isColdStart = cfEngine.getUserBehaviorVector(userId).isEmpty();

        if (isColdStart) {
            log.debug("冷启动用户，使用基于内容推荐: userId={}", userId);
            return cbEngine.generateRecommendations(userId, size, null);
        }

        // 并行获取两种推荐结果
        List<Long> cfResults = cfEngine.generateRecommendations(userId, size);
        List<Long> cbResults = cbEngine.generateRecommendations(userId, size, null);
        List<Long> hotResults = getHotContentIds(size);

        // 加权混合
        Map<Long, Double> scoreMap = new HashMap<>();

        // 协同过滤结果
        for (int i = 0; i < cfResults.size(); i++) {
            double score = (cfResults.size() - i) * weights.cfWeight();
            scoreMap.merge(cfResults.get(i), score, Double::sum);
        }

        // 基于内容结果
        for (int i = 0; i < cbResults.size(); i++) {
            double score = (cbResults.size() - i) * weights.cbWeight();
            scoreMap.merge(cbResults.get(i), score, Double::sum);
        }

        // 热门内容结果
        for (int i = 0; i < hotResults.size(); i++) {
            double score = (hotResults.size() - i) * weights.hotWeight();
            scoreMap.merge(hotResults.get(i), score, Double::sum);
        }

        // 按分排序
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(size)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取热门内容
     */
    private List<Long> getHotContentIds(int limit) {
        try {
            Set<String> hotIds = redisUtils.zReverseRange("hotrank:list:all", 0, limit - 1);
            if (hotIds != null && !hotIds.isEmpty()) {
                return hotIds.stream().map(Long::parseLong).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("获取热榜内容失败: {}", e.getMessage());
        }

        // Fallback 到数据库查询
        LambdaQueryWrapper<Content> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Content::getIsDeleted, 0)
                .eq(Content::getStatus, 2)
                .orderByDesc(Content::getLikeCount, Content::getCreateTime)
                .last("LIMIT " + limit);

        return contentMapper.selectList(wrapper).stream()
                .map(Content::getId)
                .collect(Collectors.toList());
    }

    /**
     * 构建推荐项列表
     */
    private List<RecommendItemVO> buildRecommendItems(List<Long> contentIds, Long userId, String recommendType) {
        if (contentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Content> contents = contentMapper.selectList(
                new LambdaQueryWrapper<Content>()
                        .in(Content::getId, contentIds)
                        .eq(Content::getIsDeleted, 0)
                        .eq(Content::getStatus, 2)
        );

        // 保持原有顺序
        Map<Long, Content> contentMap = contents.stream()
                .collect(Collectors.toMap(Content::getId, c -> c, (a, b) -> a));

        List<RecommendItemVO> items = new ArrayList<>();
        int position = 0;

        for (Long contentId : contentIds) {
            Content content = contentMap.get(contentId);
            if (content == null) continue;

            position++;
            UserClient.UserInfo userInfo = userClient.getUserInfo(content.getUserId());

            String summary = content.getContent() != null
                    ? (content.getContent().length() > 150
                            ? content.getContent().substring(0, 150) + "..."
                            : content.getContent())
                    : null;

            long timestamp = content.getCreateTime() != null
                    ? content.getCreateTime().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
                    : System.currentTimeMillis();

            String reason = generateRecommendReason(content, userId, recommendType);

            items.add(RecommendItemVO.builder()
                    .contentId(content.getId())
                    .title(content.getTitle())
                    .summary(summary)
                    .contentType(content.getType())
                    .coverUrl(content.getCoverUrl())
                    .mediaUrl(content.getMediaUrl())
                    .mediaType(content.getMediaType())
                    .authorUserId(content.getUserId())
                    .authorNickname(userInfo != null ? userInfo.getNickname() : "未知用户")
                    .authorAvatar(userInfo != null ? userInfo.getAvatar() : null)
                    .likeCount(content.getLikeCount())
                    .commentCount(content.getCommentCount())
                    .viewCount(content.getViewCount())
                    .publishTimestamp(timestamp)
                    .score(null)
                    .reason(reason)
                    .recommendWay(recommendType)
                    .position(position)
                    .build());
        }

        return items;
    }

    /**
     * 生成推荐理由
     */
    private String generateRecommendReason(Content content, Long userId, String recommendType) {
        return switch (recommendType) {
            case "cf" -> "与你相似的人都喜欢";
            case "cb" -> "你感兴趣的内容";
            case "hot" -> "热门推荐";
            default -> content.getLikeCount() > 1000 ? "热门内容" : "为你推荐";
        };
    }

    /**
     * 解析实验配置
     */
    private ExperimentInfoVO resolveExperiment(Long userId, String experimentId) {
        // 查询正在运行的实验
        LambdaQueryWrapper<AbExperiment> expWrapper = new LambdaQueryWrapper<>();
        expWrapper.eq(AbExperiment::getStatus, 1); // RUNNING
        if (experimentId != null) {
            expWrapper.eq(AbExperiment::getExperimentId, experimentId);
        }
        expWrapper.last("LIMIT 1");

        AbExperiment experiment = experimentMapper.selectOne(expWrapper);
        if (experiment == null) {
            return null;
        }

        // 查询桶配置
        LambdaQueryWrapper<AbBucket> bucketWrapper = new LambdaQueryWrapper<>();
        bucketWrapper.eq(AbBucket::getExperimentId, experiment.getExperimentId())
                .orderByAsc(AbBucket::getWeight);
        List<AbBucket> buckets = bucketMapper.selectList(bucketWrapper);

        if (buckets.isEmpty()) {
            return null;
        }

        List<ABTestEngine.BucketConfig> bucketConfigs = buckets.stream()
                .map(b -> new ABTestEngine.BucketConfig(
                        b.getBucketId(),
                        b.getBucketName(),
                        b.getWeight(),
                        parseConfig(b.getConfig())
                ))
                .collect(Collectors.toList());

        ABTestEngine.BucketAssignment assignment = abTestEngine.assignBucket(
                userId,
                experiment.getExperimentId(),
                experiment.getTraffic(),
                bucketConfigs
        );

        if (assignment == null) {
            return null;
        }

        return ExperimentInfoVO.builder()
                .experimentId(assignment.experimentId())
                .bucketId(assignment.bucketId())
                .experimentName(assignment.experimentName())
                .bucketName(assignment.bucketName())
                .requestId(UUID.randomUUID().toString())
                .build();
    }

    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return com.alibaba.fastjson2.JSON.parseObject(config);
        } catch (Exception e) {
            log.warn("解析实验配置失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    @Async
    public void refreshUserRecommendations(Long userId) {
        log.info("刷新用户推荐结果: userId={}", userId);

        // 重建用户画像
        cbEngine.buildAndCacheUserTagProfile(userId);

        // 预热协同过滤
        cfEngine.generateRecommendations(userId, DEFAULT_RECOMMEND_SIZE);
    }

    @Override
    public void recordExposure(Long userId, Long contentId, int position, String recommendWay) {
        if (userId == null) return;

        UserRecommendBehavior behavior = new UserRecommendBehavior();
        behavior.setUserId(userId);
        behavior.setContentId(contentId);
        behavior.setBehaviorType("EXPOSURE");
        behavior.setRecommendWay(recommendWay);
        behavior.setPosition(position);
        behaviorMapper.insert(behavior);
    }

    @Override
    public void recordClick(Long userId, Long contentId, String recommendWay) {
        if (userId == null) return;

        UserRecommendBehavior behavior = new UserRecommendBehavior();
        behavior.setUserId(userId);
        behavior.setContentId(contentId);
        behavior.setBehaviorType("CLICK");
        behavior.setRecommendWay(recommendWay);
        behaviorMapper.insert(behavior);

        // 发送 Kafka 事件
        recommendEventProducer.sendClickEvent(userId, contentId, recommendWay);
    }

    private void recordExposuresAsync(Long userId, List<Long> contentIds,
                                       String recommendType, ExperimentInfoVO experiment,
                                       String requestId) {
        if (contentIds.isEmpty()) return;

        for (int i = 0; i < contentIds.size(); i++) {
            final int position = i + 1;
            recommendEventProducer.sendExposureEvent(
                    userId,
                    contentIds.get(i),
                    position,
                    recommendType,
                    experiment != null ? experiment.getExperimentId() : null,
                    experiment != null ? experiment.getBucketId() : null,
                    requestId
            );
        }
    }
}
