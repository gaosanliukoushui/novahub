package com.novahub.content.service;

import com.novahub.content.dto.ContentQueryRequest;
import com.novahub.content.dto.PublishContentRequest;
import com.novahub.content.dto.UpdateContentRequest;
import com.novahub.content.vo.ContentListVO;
import com.novahub.content.vo.ContentVO;
import com.novahub.common.result.PageResult;

public interface IContentService {

    /**
     * 发布内容
     *
     * @param request 发布请求
     * @return 发布的contentId
     */
    Long publish(PublishContentRequest request);

    /**
     * 保存草稿
     *
     * @param request 发布请求（草稿状态）
     * @return 草稿contentId
     */
    Long saveDraft(PublishContentRequest request);

    /**
     * 获取内容详情
     *
     * @param contentId 内容ID
     * @return 内容详情
     */
    ContentVO getById(Long contentId);

    /**
     * 更新内容
     *
     * @param contentId 内容ID
     * @param request   更新请求
     */
    void update(Long contentId, UpdateContentRequest request);

    /**
     * 删除内容（软删除）
     *
     * @param contentId 内容ID
     */
    void delete(Long contentId);

    /**
     * 分页查询内容列表
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<ContentListVO> getPage(ContentQueryRequest request);

    /**
     * 获取当前用户的草稿列表
     *
     * @return 草稿列表
     */
    PageResult<ContentListVO> getDrafts();

    /**
     * 获取指定用户的已发布内容列表
     *
     * @param userId   用户ID
     * @param request  查询条件
     * @return 分页结果
     */
    PageResult<ContentListVO> getUserContents(Long userId, ContentQueryRequest request);

    /**
     * 异步增加浏览数
     *
     * @param contentId 内容ID
     */
    void incrementViewCount(Long contentId);
}
