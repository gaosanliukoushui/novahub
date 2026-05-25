package com.novahub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novahub.content.entity.ContentTagRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentTagRelMapper extends BaseMapper<ContentTagRel> {

    /**
     * 根据内容ID查询标签ID列表
     */
    List<Long> selectTagIdsByContentId(@Param("contentId") Long contentId);

    List<ContentTagRel> selectByContentIds(@Param("contentIds") List<Long> contentIds);

    /**
     * 根据内容ID删除所有关联
     */
    int deleteByContentId(@Param("contentId") Long contentId);

    /**
     * 批量插入关联
     */
    int batchInsert(@Param("list") List<ContentTagRel> list);

    /**
     * 根据标签ID统计关联内容数量
     */
    int countByTagId(@Param("tagId") Long tagId);
}
