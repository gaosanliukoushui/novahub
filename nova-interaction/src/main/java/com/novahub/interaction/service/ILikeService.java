package com.novahub.interaction.service;

import com.novahub.common.result.PageResult;
import com.novahub.interaction.vo.LikeUserVO;

import java.util.List;
import java.util.Map;

public interface ILikeService {

    /**
     * 点赞
     */
    boolean like(Long userId, Long contentId);

    /**
     * 取消点赞
     */
    boolean unlike(Long userId, Long contentId);

    /**
     * 检查是否已点赞
     */
    boolean isLiked(Long userId, Long contentId);

    /**
     * 获取用户点赞的所有内容ID
     */
    List<Long> getLikedContentIds(Long userId);

    /**
     * 获取内容的点赞用户列表
     */
    PageResult<LikeUserVO> getContentLikeUsers(Long page, Long pageSize, Long contentId);

    /**
     * 获取点赞排行榜TOP N
     */
    List<Map<String, Object>> getLikeRank(Integer topN);
}
