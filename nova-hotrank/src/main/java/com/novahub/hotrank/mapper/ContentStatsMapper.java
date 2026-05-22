package com.novahub.hotrank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novahub.hotrank.entity.ContentStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContentStatsMapper extends BaseMapper<ContentStats> {

    ContentStats selectByContentId(@Param("contentId") Long contentId);

    List<ContentStats> selectTopByHeatScore(@Param("limit") int limit);

    List<ContentStats> selectTopByTypeAndHeatScore(@Param("contentType") Integer type, @Param("limit") int limit);

    void upsert(ContentStats stats);
}
