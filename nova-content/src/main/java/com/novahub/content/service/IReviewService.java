package com.novahub.content.service;

public interface IReviewService {

    /**
     * 提交内容审核
     *
     * @param contentId 内容ID
     */
    void submitForReview(Long contentId);

    /**
     * 处理审核结果
     *
     * @param contentId 内容ID
     * @param approved  是否通过
     * @param remark    审核备注
     */
    void processReview(Long contentId, Boolean approved, String remark);
}
