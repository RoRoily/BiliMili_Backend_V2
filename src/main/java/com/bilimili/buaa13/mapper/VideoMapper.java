package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
    //查询所有对应状态的视频，并且除去已删除的
    @Select("select * from video where status = #{status};")
    List<Video> selectAllVideoByStatus(int status);

    //随机返回count个对应状态的视频，除去已删除的
    @Select("select * from video where status = #{status} order by RAND() LIMIT #{count};")
    List<Video> selectCountVideoByRandom(int status, int count);


}
