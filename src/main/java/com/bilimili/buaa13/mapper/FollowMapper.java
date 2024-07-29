package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {
    @Select("select follow.uid_fans from follow where uid_follow = #{uidFollow}")
    List<Integer> getUidFansByUid(@Param("uidFollow") Integer uidFollow);
    @Select("select follow.uid_follow from follow where uid_fans = #{uidFans}")
    List<Integer> getUidFollowByUid(@Param("uidFans")Integer uidFans);
}
