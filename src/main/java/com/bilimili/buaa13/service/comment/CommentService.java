package com.bilimili.buaa13.service.comment;

import com.bilimili.buaa13.entity.Comment;
import com.bilimili.buaa13.entity.CommentTree;
import com.bilimili.buaa13.entity.ResponseResult;

import java.util.List;

public interface CommentService {
    /**
     * 根据视频id获取评论
     * @param vid 视频id
     * @param offset 已获取的评论树数量
     * @param sortType 排序方式
     * @return 评论树数组
     */
    List<CommentTree> getCommentTreeByVid(Integer vid, Long offset, Integer sortType);

    /**
     * 发送评论，字数不得大于2000或为空
     * @param vid   视频id
     * @param uid   发布者uid
     * @param rootId    楼层id（根评论id）
     * @param parentId  被回复的评论id
     * @param toUserId  被回复用户uid
     * @param content   评论内容
     * @return  true 发送成功 false 发送失败
     */
    CommentTree sendComment(Integer vid, Integer uid, Integer rootId, Integer parentId, Integer toUserId, String content);


    /**
     * 删除评论
     * @param cid    评论id
     * @param uid   当前用户id
     * @param isAdmin   是否是管理员
     * @return  响应对象
     */
    ResponseResult deleteComment(Integer cid, Integer uid, boolean isAdmin);


    /**
     * @param rootId 根级节点的评论 cid, 即楼层 cid
     * @return 1. 根据 redis 查找出回复该评论的子评论 cid 列表
     * 2. 根据 cid 查询出所有评论的详细信息
     */
    List<Comment> getChildCommentsByRootId(Integer rootId, Long start, Long end);

    /**
     * 根据视频 vid 获取根评论列表，一次查 10 条
     * @param vid 视频 cid
     * @param offset 偏移量，已经获取到的根评论数量
     * @param sortType 1:按热度排序 2:按时间排序
     * @return List<Comment>
     */
    List<Comment> getRootCommentsByVid(Integer vid, Long offset, Integer sortType);

    /**
     * 获取更多回复评论
     * @param cid 根评论id
     * @return  包含全部回复评论的评论树
     */
    CommentTree getMoreCommentsById(Integer cid);

    /*--------------------评论点赞点踩相关-----------------------*/
    /**
     * 同时相对更新点赞和点踩
     * 用于原本点踩了，现在直接点赞，一次改完。
     * @param cid    评论id
     * @param addUpVote   true 点赞 false 点踩
     */
    void updateLikeAndDisLike(Integer cid, boolean addUpVote);

    /**
     * 单独更新点赞或点踩
     * @param cid    评论id
     * @param column    "up_vote" 点赞 "down_vote" 点踩
     * @param increase  true 增加 false 减少
     * @param count     更改数量
     */
    void updateComment(Integer cid, String column, boolean increase, Integer count);
}
