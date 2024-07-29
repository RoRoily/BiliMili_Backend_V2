package com.bilimili.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.backend.pojo.Comment;
import com.bilimili.backend.pojo.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostCommentMapper extends BaseMapper<PostComment> {
//    @Select("SELECT * FROM comment WHERE root_id = #{rootId} AND vid = #{vid}")
//    List<Comment> getChildCommentsByRootId(@Param("rootId") Integer rootId, @Param("vid") Integer vid);

    @Select("SELECT * FROM postComment WHERE pid = #{vid} AND root_id = 0")
    List<PostComment> getRootPostCommentsByPid(@Param("pid") Integer pid);

}

