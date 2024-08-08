package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.ChatDetailed;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Mapper
public interface ChatDetailedMapper extends BaseMapper<ChatDetailed> {
    @Select("select * from chat_detailed where user_id = #{post_id} and another_id = #{accept_id}")
    List<ChatDetailed> selectAllChatDetailed(@Param("post_id") Integer post_id,
                                             @Param("accept_id") Integer accept_id);
}
