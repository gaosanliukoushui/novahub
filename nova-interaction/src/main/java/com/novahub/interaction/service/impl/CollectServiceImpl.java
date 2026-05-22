package com.novahub.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import com.novahub.content.entity.Content;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.hotrank.service.StatsEventProducer;
import com.novahub.interaction.entity.CollectFolder;
import com.novahub.interaction.entity.ContentCollect;
import com.novahub.interaction.mapper.CollectFolderMapper;
import com.novahub.interaction.mapper.ContentCollectMapper;
import com.novahub.interaction.service.ICollectService;
import com.novahub.interaction.vo.FolderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CollectServiceImpl implements ICollectService {

    private static final String USER_COLLECT_KEY_PREFIX = "user:collects:";

    private final ContentCollectMapper contentCollectMapper;
    private final CollectFolderMapper collectFolderMapper;
    private final ContentMapper contentMapper;
    private final RedisUtils redisUtils;
    private final StatsEventProducer statsEventProducer;

    public CollectServiceImpl(ContentCollectMapper contentCollectMapper,
                            CollectFolderMapper collectFolderMapper,
                            ContentMapper contentMapper,
                            RedisUtils redisUtils,
                            StatsEventProducer statsEventProducer) {
        this.contentCollectMapper = contentCollectMapper;
        this.collectFolderMapper = collectFolderMapper;
        this.contentMapper = contentMapper;
        this.redisUtils = redisUtils;
        this.statsEventProducer = statsEventProducer;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean collect(Long userId, Long contentId, Long folderId) {
        LambdaQueryWrapper<ContentCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentCollect::getUserId, userId)
                .eq(ContentCollect::getContentId, contentId);

        if (contentCollectMapper.exists(wrapper)) {
            throw new BusinessException(ResultCode.COLLECT_ALREADY_EXISTS);
        }

        if (folderId == null) {
            folderId = getOrCreateDefaultFolder(userId);
        }

        ContentCollect collect = new ContentCollect();
        collect.setUserId(userId);
        collect.setContentId(contentId);
        collect.setFolderId(folderId);
        contentCollectMapper.insert(collect);

        LambdaUpdateWrapper<Content> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Content::getId, contentId)
                .setSql("collect_count = collect_count + 1");
        contentMapper.update(null, updateWrapper);

        String userCollectKey = USER_COLLECT_KEY_PREFIX + userId;
        redisUtils.sAdd(userCollectKey, contentId.toString());

        Content content = contentMapper.selectById(contentId);
        if (content != null) {
            statsEventProducer.sendCollectEvent(contentId, content.getUserId(), content.getType(), userId);
        }

        log.info("User {} collected content {} into folder {}", userId, contentId, folderId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean uncollect(Long userId, Long contentId) {
        LambdaQueryWrapper<ContentCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentCollect::getUserId, userId)
                .eq(ContentCollect::getContentId, contentId);

        int deleted = contentCollectMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException(ResultCode.COLLECT_NOT_EXISTS);
        }

        LambdaUpdateWrapper<Content> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Content::getId, contentId)
                .setSql("collect_count = collect_count - 1");
        contentMapper.update(null, updateWrapper);

        String userCollectKey = USER_COLLECT_KEY_PREFIX + userId;
        redisUtils.sRem(userCollectKey, contentId.toString());

        log.info("User {} uncollected content {}", userId, contentId);
        return true;
    }

    @Override
    public Object getCollections(Long userId, Long folderId) {
        LambdaQueryWrapper<ContentCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentCollect::getUserId, userId);

        if (folderId != null) {
            wrapper.eq(ContentCollect::getFolderId, folderId);
        }

        wrapper.orderByDesc(ContentCollect::getCreateTime);
        List<ContentCollect> collects = contentCollectMapper.selectList(wrapper);

        return collects.stream()
                .map(collect -> {
                    CollectVO vo = new CollectVO();
                    vo.setId(collect.getId());
                    vo.setContentId(collect.getContentId());
                    vo.setFolderId(collect.getFolderId());
                    vo.setCreateTime(collect.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean isCollected(Long userId, Long contentId) {
        LambdaQueryWrapper<ContentCollect> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContentCollect::getUserId, userId)
                .eq(ContentCollect::getContentId, contentId);

        return contentCollectMapper.exists(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FolderVO createFolder(Long userId, String name) {
        LambdaQueryWrapper<CollectFolder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectFolder::getUserId, userId)
                .eq(CollectFolder::getName, name);

        if (collectFolderMapper.exists(wrapper)) {
            throw new BusinessException(ResultCode.FAILED, "收藏夹名称已存在");
        }

        CollectFolder folder = new CollectFolder();
        folder.setUserId(userId);
        folder.setName(name);
        folder.setIsDefault(0);
        collectFolderMapper.insert(folder);

        FolderVO vo = new FolderVO();
        vo.setId(folder.getId());
        vo.setName(folder.getName());
        vo.setIsDefault(folder.getIsDefault());
        vo.setCreateTime(folder.getCreateTime());

        log.info("User {} created folder: {}", userId, name);
        return vo;
    }

    @Override
    public List<FolderVO> getFolders(Long userId) {
        LambdaQueryWrapper<CollectFolder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectFolder::getUserId, userId)
                .orderByAsc(CollectFolder::getIsDefault)
                .orderByDesc(CollectFolder::getCreateTime);

        List<CollectFolder> folders = collectFolderMapper.selectList(wrapper);

        return folders.stream()
                .map(folder -> {
                    FolderVO vo = new FolderVO();
                    vo.setId(folder.getId());
                    vo.setName(folder.getName());
                    vo.setIsDefault(folder.getIsDefault());
                    vo.setCreateTime(folder.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private Long getOrCreateDefaultFolder(Long userId) {
        LambdaQueryWrapper<CollectFolder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectFolder::getUserId, userId)
                .eq(CollectFolder::getIsDefault, 1);

        CollectFolder defaultFolder = collectFolderMapper.selectOne(wrapper);

        if (defaultFolder == null) {
            defaultFolder = new CollectFolder();
            defaultFolder.setUserId(userId);
            defaultFolder.setName("默认收藏");
            defaultFolder.setIsDefault(1);
            collectFolderMapper.insert(defaultFolder);
        }

        return defaultFolder.getId();
    }

    private static class CollectVO {
        private Long id;
        private Long contentId;
        private Long folderId;
        private java.time.LocalDateTime createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getContentId() { return contentId; }
        public void setContentId(Long contentId) { this.contentId = contentId; }
        public Long getFolderId() { return folderId; }
        public void setFolderId(Long folderId) { this.folderId = folderId; }
        public java.time.LocalDateTime getCreateTime() { return createTime; }
        public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    }
}
