package com.bilimili.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.backend.pojo.FavoriteVideo;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Date;

@Mapper
public interface FavoriteVideoMapper extends BaseMapper<FavoriteVideo> {
    @Select("select vid from favorite_video where fid = #{fid} and is_remove = 0 order by time desc")
    List<Integer> getVidByFid(Integer fid);
    @Select("select time from favorite_video where fid = #{fid} order by time desc")
    List<Date> getTimeByFid(Integer fid);

}
