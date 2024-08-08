package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Chat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMapper extends BaseMapper<Chat> {
    /**
     * 用于查询uid用户所有的消息记录
     * @return
     */
    @Select("select * from chat where post_id = #{uid} or accept_id = #{uid}")
    List<Chat> selectChatByUid(Integer uid);
}
