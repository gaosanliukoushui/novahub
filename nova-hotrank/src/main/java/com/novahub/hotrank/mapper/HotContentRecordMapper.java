package com.novahub.hotrank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novahub.hotrank.entity.HotContentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface HotContentRecordMapper extends BaseMapper<HotContentRecord> {

    List<HotContentRecord> selectByDateAndType(@Param("recordDate") String recordDate, @Param("rankType") Integer rankType);

    void batchInsert(@Param("records") List<HotContentRecord> records);
}
