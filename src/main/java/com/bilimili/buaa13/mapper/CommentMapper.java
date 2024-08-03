package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    @Select("SELECT * FROM comment WHERE vid = #{vid} AND root_id = 0")
    List<Comment> getRootCommentsByVid(@Param("vid") Integer vid);
    @Select("select * from comment where parent_id = #{root_id} and comment.is_deleted = 0 limit #{limit} offset #{start}")
    List<Comment> getRootCommentsByStartAndLimit(@Param("root_id") Integer rootId,
                                                 @Param("start") Long start,
                                                 @Param("limit") Long limit
    );
    @Select("select * from comment where parent_id = #{root_id} and comment.is_deleted = 0 LIMIT 18446744073709551615 offset #{start}")
    List<Comment> getRootCommentByStartNoLimit(@Param("root_id") Integer rootId,
                                               @Param("limit") Long limit
    );
}
