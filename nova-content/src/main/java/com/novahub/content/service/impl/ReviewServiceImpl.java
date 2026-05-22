package com.novahub.content.service.impl;

import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.filter.SensitiveWordFilter;
import com.novahub.content.kafka.ContentEventProducer;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.content.service.IReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements IReviewService {

    private final ContentMapper contentMapper;
    private final ContentEventProducer contentEventProducer;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForReview(Long contentId) {
        Content content = contentMapper.selectById(contentId);
        if (content == null || content.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.CONTENT_NOT_FOUND);
        }

        if (content.getStatus() != 0) {
            throw new BusinessException(ResultCode.CONTENT_ALREADY_PUBLISHED, "内容已提交审核或已发布");
        }

        boolean titleSensitive = sensitiveWordFilter.containsSensitiveWord(content.getTitle());
        boolean contentSensitive = sensitiveWordFilter.containsSensitiveWord(content.getContent());

        if (titleSensitive || contentSensitive) {
            log.warn("敏感词检测拒绝: contentId={}, titleSensitive={}, contentSensitive={}",
                    contentId, titleSensitive, contentSensitive);
            content.setReviewStatus(2);
            content.setReviewRemark("含有敏感词，已被机审拒绝");
            contentMapper.updateById(content);

            String cacheKey = "content:detail:" + contentId;
            redisUtils.delete(cacheKey);
            return;
        }

        content.setStatus(1);
        content.setReviewStatus(0);
        contentMapper.updateById(content);

        try {
            contentEventProducer.sendReviewSubmitEvent(contentId, content.getUserId());
        } catch (Exception e) {
            log.warn("Kafka 审核事件发送失败: contentId={}", contentId, e);
        }

        log.info("内容提交审核: contentId={}, userId={}", contentId, content.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processReview(Long contentId, Boolean approved, String remark) {
        Content content = contentMapper.selectById(contentId);
        if (content == null || content.getIsDeleted() == 1) {
            throw new BusinessException(ResultCode.CONTENT_NOT_FOUND);
        }

        if (content.getStatus() != 1) {
            throw new BusinessException(ResultCode.CONTENT_UNDER_REVIEW, "内容不在待审核状态");
        }

        if (approved) {
            content.setStatus(2);
            content.setReviewStatus(1);
            content.setPublishTime(LocalDateTime.now());
            log.info("内容审核通过: contentId={}", contentId);
        } else {
            content.setStatus(0);
            content.setReviewStatus(2);
            log.info("内容审核拒绝: contentId={}, remark={}", contentId, remark);
        }

        if (remark != null) {
            content.setReviewRemark(remark);
        }

        contentMapper.updateById(content);

        try {
            contentEventProducer.sendReviewResultEvent(contentId, approved, remark);
        } catch (Exception e) {
            log.warn("Kafka 审核结果事件发送失败: contentId={}", contentId, e);
        }
    }
}
