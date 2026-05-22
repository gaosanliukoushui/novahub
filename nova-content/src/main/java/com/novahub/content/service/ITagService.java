package com.novahub.content.service;

import com.novahub.content.vo.TagVO;

import java.util.List;

public interface ITagService {

    /**
     * 获取所有标签
     *
     * @return 标签列表
     */
    List<TagVO> getAllTags();

    /**
     * 获取热门标签
     *
     * @param limit 数量限制
     * @return 热门标签列表
     */
    List<TagVO> getHotTags(int limit);

    /**
     * 根据标签名获取或创建标签
     *
     * @param tagNames 标签名列表
     * @return 标签ID列表
     */
    List<Long> getOrCreateTags(List<String> tagNames);

    /**
     * 获取内容关联的标签
     *
     * @param contentId 内容ID
     * @return 标签列表
     */
    List<TagVO> getTagsByContentId(Long contentId);

    /**
     * 更新标签热度分
     * 根据标签下的内容点赞数/评论数计算热度，更新 use_count 字段
     */
    void updateTagHotScore();
}
