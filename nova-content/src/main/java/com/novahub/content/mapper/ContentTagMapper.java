package com.novahub.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novahub.content.entity.ContentTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContentTagMapper extends BaseMapper<ContentTag> {

    /**
     * 查询热门标签
     */
    List<ContentTag> selectHotTags(@Param("limit") int limit);

    /**
     * 根据名称列表查询标签
     */
    List<ContentTag> selectByNames(@Param("names") List<String> names);

    /**
     * 批量增加使用次数
     */
    int incrementUseCount(@Param("ids") List<Long> ids);
}
