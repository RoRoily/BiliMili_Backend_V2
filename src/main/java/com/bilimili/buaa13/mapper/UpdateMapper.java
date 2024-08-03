package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Update;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UpdateMapper extends BaseMapper<Update> {
    @Select("SELECT * FROM `update` WHERE vid = #{vid} AND root_id = 0")
    List<Update> getRootUpdatesByUid(@Param("uid") Integer uid);
}
