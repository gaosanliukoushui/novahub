package com.novahub.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novahub.common.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
