package com.bilimili.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.backend.pojo.UserRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRecordMapper extends BaseMapper<UserRecord> {
}
