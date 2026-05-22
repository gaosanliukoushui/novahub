package com.novahub.interaction.service;

import com.novahub.interaction.vo.FolderVO;

import java.util.List;

public interface ICollectService {

    /**
     * 收藏内容
     */
    boolean collect(Long userId, Long contentId, Long folderId);

    /**
     * 取消收藏
     */
    boolean uncollect(Long userId, Long contentId);

    /**
     * 获取我的收藏列表
     */
    Object getCollections(Long userId, Long folderId);

    /**
     * 检查是否已收藏
     */
    boolean isCollected(Long userId, Long contentId);

    /**
     * 创建收藏夹
     */
    FolderVO createFolder(Long userId, String name);

    /**
     * 获取我的收藏夹列表
     */
    List<FolderVO> getFolders(Long userId);
}
