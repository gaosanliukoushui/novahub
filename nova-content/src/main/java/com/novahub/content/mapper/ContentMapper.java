package com.novahub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novahub.content.entity.Content;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentMapper extends BaseMapper<Content> {

    /**
     * 分页查询已发布内容列表
     */
    List<Content> selectPublishedList(@Param("type") Integer type,
                                      @Param("tagId") Long tagId,
                                      @Param("sortBy") String sortBy);

    /**
     * 查询用户的已发布内容列表
     */
    List<Content> selectByUserId(@Param("userId") Long userId,
                                 @Param("status") Integer status,
                                 @Param("sortBy") String sortBy);

    /**
     * 查询用户的草稿列表
     */
    List<Content> selectDraftsByUserId(@Param("userId") Long userId);

    /**
     * 批量更新浏览数
     */
    int incrementViewCount(@Param("id") Long id);
}
