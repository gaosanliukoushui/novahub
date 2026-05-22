package com.novahub.recommend.algorithm;

import com.novahub.common.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A/B 测试框架引擎
 *
 * 核心功能：
 * 1. 确定性分流 - 基于 MurmurHash3 保证同一用户始终进入同一桶
 * 2. 多实验叠加 - 用户可同时参与多个实验
 * 3. 流量分配 - 支持按百分比分配实验流量
 *
 * 分流算法：
 * bucket = murmurhash3(userId + experimentId) % 100
 * if (bucket < traffic * 100) → 实验组
 * else → 对照组
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ABTestEngine {

    private final RedisUtils redisUtils;

    private static final String USER_EXPERIMENT_KEY = "recommend:ab:user:%s:%s";
    private static final int TOTAL_BUCKETS = 100;

    /**
     * 用户分桶结果
     */
    public record BucketAssignment(
            String experimentId,
            String bucketId,
            String experimentName,
            String bucketName
    ) {}

    /**
     * 为用户分配实验桶
     *
     * @param userId 用户ID
     * @param experimentId 实验ID
     * @param traffic 实验流量占比 (0.0 - 1.0)
     * @param buckets 桶配置列表
     * @return 分桶结果
     */
    public BucketAssignment assignBucket(Long userId, String experimentId, double traffic,
                                        java.util.List<BucketConfig> buckets) {
        if (userId == null) {
            return null;
        }

        // 尝试从缓存获取
        String cacheKey = String.format(USER_EXPERIMENT_KEY, userId, experimentId);
        String cachedBucketId = redisUtils.get(cacheKey);
        if (cachedBucketId != null) {
            return findBucketConfig(buckets, cachedBucketId, experimentId);
        }

        // 使用 MurmurHash3 进行确定性分流
        int bucketIndex = murmurHash3(userId.toString() + experimentId) % TOTAL_BUCKETS;
        int threshold = (int) (traffic * TOTAL_BUCKETS);

        String assignedBucketId;
        if (bucketIndex < threshold) {
            // 在实验组内，进一步分配到具体桶
            assignedBucketId = selectBucketByWeight(bucketIndex, threshold, buckets);
        } else {
            // 对照组，默认分配到第一个桶（通常是 control）
            assignedBucketId = buckets.isEmpty() ? "A" : buckets.get(0).bucketId;
        }

        // 缓存分配结果（永不过期，保证一致性）
        redisUtils.set(cacheKey, assignedBucketId);

        log.debug("A/B测试分流: userId={}, experimentId={}, bucketIndex={}, assignedBucket={}",
                userId, experimentId, bucketIndex, assignedBucketId);

        return findBucketConfig(buckets, assignedBucketId, experimentId);
    }

    /**
     * MurmurHash3 简化实现
     */
    private int murmurHash3(String key) {
        byte[] data = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = data.length;
        int h = 0;

        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        int i = 0;
        while (i + 4 <= len) {
            int k = (data[i] & 0xff)
                    | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16)
                    | ((data[i + 3] & 0xff) << 24);

            k *= c1;
            k = Integer.rotateLeft(k, 15);
            k *= c2;

            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;

            i += 4;
        }

        int remaining = len - i;
        if (remaining > 0) {
            int k = 0;
            switch (remaining) {
                case 3 -> k ^= (data[i + 2] & 0xff) << 16;
                case 2 -> k ^= (data[i + 1] & 0xff) << 8;
                case 1 -> {
                    k ^= data[i] & 0xff;
                    k *= c1;
                    k = Integer.rotateLeft(k, 15);
                    k *= c2;
                }
            }
            h ^= k;
        }

        h ^= len;
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);

        return Math.abs(h);
    }

    /**
     * 根据权重分配具体桶
     */
    private String selectBucketByWeight(int bucketIndex, int threshold, java.util.List<BucketConfig> buckets) {
        if (buckets.isEmpty()) {
            return "A";
        }

        int normalizedIndex = bucketIndex * buckets.size() / threshold;
        int selectedIndex = Math.min(normalizedIndex, buckets.size() - 1);

        return buckets.get(selectedIndex).bucketId;
    }

    private BucketAssignment findBucketConfig(java.util.List<BucketConfig> buckets,
                                             String bucketId, String experimentId) {
        for (BucketConfig config : buckets) {
            if (config.bucketId().equals(bucketId)) {
                return new BucketAssignment(
                        experimentId,
                        bucketId,
                        config.name(),
                        config.name()
                );
            }
        }
        return new BucketAssignment(experimentId, bucketId, experimentId, bucketId);
    }

    /**
     * 桶配置
     */
    public record BucketConfig(
            String bucketId,
            String name,
            double weight,
            java.util.Map<String, Object> config
    ) {}

    /**
     * 实验配置（从数据库或配置中心获取）
     */
    public record ExperimentConfig(
            String experimentId,
            String name,
            double traffic,
            java.util.List<BucketConfig> buckets
    ) {}

    /**
     * 根据实验分组获取推荐权重配置
     */
    public RecommendWeights getRecommendWeights(BucketAssignment assignment) {
        if (assignment == null) {
            return RecommendWeights.defaultWeights();
        }

        String bucketId = assignment.bucketId();

        // 根据桶ID返回不同的权重配置
        return switch (bucketId) {
            case "A" -> new RecommendWeights(0.6, 0.4, 0.0);
            case "B" -> new RecommendWeights(0.2, 0.8, 0.0);
            case "C" -> new RecommendWeights(0.4, 0.6, 0.0);
            default -> RecommendWeights.defaultWeights();
        };
    }

    /**
     * 推荐权重配置
     */
    public record RecommendWeights(
            double cfWeight,
            double cbWeight,
            double hotWeight
    ) {
        public static RecommendWeights defaultWeights() {
            return new RecommendWeights(0.4, 0.4, 0.2);
        }
    }
}
