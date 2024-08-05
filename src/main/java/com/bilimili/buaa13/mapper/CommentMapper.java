package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    /**
     * 获取vid视频对应的评论，要求是根评论
     * @param vid 视频id
     * @return 根评论数组
     */
    @Select("SELECT * FROM comment WHERE vid = #{vid} AND root_id = 0")
    List<Comment> getRootCommentsByVid(@Param("vid") Integer vid);

    /**
     * 根据开始的位置和偏移量获取子评论
     * @param rootId 根级节点的cid
     * @param start 开始位置
     * @param limit 限制的个数
     * @return
     */
    @Select("select * from comment where parent_id = #{root_id} and comment.is_deleted = 0 limit #{limit} offset #{start}")
    List<Comment> getRootCommentsByStartAndLimit(@Param("root_id") Integer rootId,
                                                 @Param("start") Long start,
                                                 @Param("limit") Long limit
    );
    @Select("select * from comment where parent_id = #{root_id} and comment.is_deleted = 0 LIMIT 18446744073709551615 offset #{start}")
    List<Comment> getRootCommentByStartNoLimit(@Param("root_id") Integer rootId,
                                               @Param("limit") Long limit
    );

    /**
     * 根据vid和开始位置，限制个数查询，按照热度排序
     */
    @Select("select * from comment where vid = #{vid} order by " +
            "(select up_vote - down_vote from comment where comment.vid = #{vid})" +
            "limit #{limit} offset #{start}")
    List<Comment> getVidRootCommentsByHeat(@Param("vid") Integer vid,
                                           @Param("start") Long start,
                                           @Param("limit") Long limit);

    /**
     * 根据vid和时间排序查询
     */
    @Select("select * from comment where vid = #{vid} order by " +
            "(select comment.create_time from comment where comment.vid = #{vid})" +
            "limit #{limit} offset #{start}")
    List<Comment> getVidRootCommentsByTime(@Param("vid") Integer vid,
                                                @Param("start") Long start,
                                                @Param("limit") Long limit);

}
