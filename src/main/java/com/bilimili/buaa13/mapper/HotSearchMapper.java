package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.HotSearch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Mapper
public interface HotSearchMapper extends BaseMapper<HotSearch> {
    List<HotSearch> selectHotSearch(@Param("keyword") String keyword);
    @Select("select * from hot_search order by hot desc limit #{count}")
    List<HotSearch> getHotSearchByCount(@Param("count") Integer count);
}
