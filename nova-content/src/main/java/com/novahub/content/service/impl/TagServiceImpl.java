package com.novahub.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.novahub.content.entity.ContentTag;
import com.novahub.content.entity.ContentTagRel;
import com.novahub.content.mapper.ContentTagMapper;
import com.novahub.content.mapper.ContentTagRelMapper;
import com.novahub.content.service.ITagService;
import com.novahub.content.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements ITagService {

    private final ContentTagMapper contentTagMapper;
    private final ContentTagRelMapper contentTagRelMapper;

    @Override
    public List<TagVO> getAllTags() {
        LambdaQueryWrapper<ContentTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ContentTag::getUseCount);
        List<ContentTag> tags = contentTagMapper.selectList(wrapper);
        return tags.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<TagVO> getHotTags(int limit) {
        List<ContentTag> tags = contentTagMapper.selectHotTags(limit);
        return tags.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> getOrCreateTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return new ArrayList<>();
        }

        List<ContentTag> existingTags = contentTagMapper.selectByNames(tagNames);
        Map<String, ContentTag> existingMap = existingTags.stream()
                .collect(Collectors.toMap(ContentTag::getName, tag -> tag));

        List<ContentTag> newTags = new ArrayList<>();
        for (String name : tagNames) {
            if (!existingMap.containsKey(name)) {
                ContentTag tag = new ContentTag();
                tag.setName(name);
                tag.setUseCount(0);
                newTags.add(tag);
            }
        }

        if (!newTags.isEmpty()) {
            newTags.forEach(contentTagMapper::insert);
            log.info("创建新标签: {}", newTags.stream().map(ContentTag::getName).collect(Collectors.toList()));
        }

        List<ContentTag> allTags = new ArrayList<>();
        allTags.addAll(existingTags);
        allTags.addAll(newTags);

        return allTags.stream().map(ContentTag::getId).collect(Collectors.toList());
    }

    @Override
    public List<TagVO> getTagsByContentId(Long contentId) {
        List<Long> tagIds = contentTagRelMapper.selectTagIdsByContentId(contentId);
        if (tagIds == null || tagIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<ContentTag> tags = contentTagMapper.selectBatchIds(tagIds);
        return tags.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private TagVO convertToVO(ContentTag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        vo.setUseCount(tag.getUseCount());
        return vo;
    }

    @Override
    public void updateTagHotScore() {
        log.info("开始更新标签热度分...");

        List<ContentTag> allTags = contentTagMapper.selectList(null);
        if (allTags.isEmpty()) {
            return;
        }

        int updated = 0;
        for (ContentTag tag : allTags) {
            int count = contentTagRelMapper.countByTagId(tag.getId());
            if (count != tag.getUseCount()) {
                tag.setUseCount(count);
                contentTagMapper.updateById(tag);
                updated++;
            }
        }

        log.info("标签热度更新完成, 共更新 {} 个标签", updated);
    }
}
