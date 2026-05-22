package com.novahub.recommend.service;

import com.novahub.recommend.dto.RecommendRequest;
import com.novahub.recommend.vo.RecommendResponseVO;

public interface IRecommendService {

    /**
     * 获取推荐内容列表
     *
     * @param request 推荐请求参数
     * @return 推荐结果
     */
    RecommendResponseVO getRecommendations(RecommendRequest request);

    /**
     * 刷新用户推荐结果
     *
     * @param userId 用户ID
     */
    void refreshUserRecommendations(Long userId);

    /**
     * 记录推荐曝光事件
     *
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param position 展示位置
     * @param recommendWay 推荐方式
     */
    void recordExposure(Long userId, Long contentId, int position, String recommendWay);

    /**
     * 记录推荐点击事件
     *
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param recommendWay 推荐方式
     */
    void recordClick(Long userId, Long contentId, String recommendWay);
}
