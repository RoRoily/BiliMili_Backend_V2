package com.bilimili.buaa13.service.impl.comment;

import com.bilimili.buaa13.service.comment.CommentService;
import com.bilimili.buaa13.service.comment.UserCommentService;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class UserCommentServiceImpl implements UserCommentService {
    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CommentService commentService;

    /**
     * 获取用户点赞和点踩的评论集合
     * @param uid   当前用户
     * @return  点赞和点踩的评论集合
     */
    @Override
    public Map<String, Object> getUserUpVoteAndDownVote(Integer uid) {
        Map<String, Object> map = new HashMap<>();
        Set<Object> userLike = redisUtil.getMembers("upVote:" + uid);
        Set<Object> userDislike = redisUtil.getMembers("downVote:" + uid);
        map.put("userLike", userLike==null?new ArrayList<>():userLike);
        map.put("userDislike", userDislike==null?new ArrayList<>():userDislike);
        //注释Redis
        //注释异步线程
        /*// 获取用户点赞列表，并放入map中
        CompletableFuture<Void> userLikeFuture = CompletableFuture.runAsync(() -> {

            if (userLike == null) {
                map.put("userLike", Collections.emptySet());
            } else{
                map.put("userLike", userLike);
            }
        }, taskExecutor);
        // 获取用户点踩列表，并放入map中
        CompletableFuture<Void> userDislikeFuture = CompletableFuture.runAsync(() -> {

            map.put("userDislike", userDislike);
            if (userDislike == null) {
                map.put("userDislike", Collections.emptySet());
            } else {
                map.put("userDislike", userDislike);
            }
        }, taskExecutor);

        userDislikeFuture.join();
        userLikeFuture.join();*/

        return map;
    }

    /**
     * 点赞或点踩某条评论
     * @param uid   当前用户id
     * @param id    评论id
     * @param isLike true 赞 false 踩
     * @param isCancel true 取消 false 点中
     */
    @Override
    public void setUserUpVoteOrDownVote(Integer uid, Integer id, boolean isLike, boolean isCancel) {
        Boolean likeExist = redisUtil.isMember("upVote:" + uid, id);
        Boolean dislikeExist = redisUtil.isMember("downVote:" + uid, id);

        // 点赞
        if (isLike && !isCancel) {
            // 原本就点了赞
            if (likeExist) {
                return;
            }
            // 添加点赞记录
            redisUtil.addMember("upVote:" + uid, id);
            // 原本点了踩，就要取消踩
            if (dislikeExist) {
                // 1.redis中删除点踩记录
                redisUtil.delMember("downVote:" + uid, id);
                // 2. 数据库中更改评论的点赞点踩数
                commentService.updateLikeAndDisLike(id, true);

            } else {
                // 原来没点踩，只需要点赞, 这里只更新评论的点赞数
                commentService.updateComment(id, "love", true, 1);
            }
        } else if (isLike) {// 取消点赞
            if (!likeExist) {
                // 原本就没有点赞，直接返回
                return;
            }
            // 移除点赞记录
            redisUtil.delMember("upVote:" + uid, id);
            // 更新评论点赞数
            commentService.updateComment(id, "love", false, 1);
        } else if (!isCancel) {
            // 点踩
            if (dislikeExist) {
                // 原本就点了踩，直接返回
                return;
            }
            // 更新用户点踩记录
            redisUtil.addMember("downVote:" + uid, id);
            if (likeExist) {
                // 原本点了赞，要取消赞
                redisUtil.delMember("upVote:" + uid, id);
                // 更新评论点赞点踩的记录
                commentService.updateLikeAndDisLike(id, false);
            } else {
                // 原本没有点赞，直接点踩，更新评论点踩数量
                commentService.updateComment(id, "bad", true, 1);
            }
        } else {
            // 取消点踩
            if (!dislikeExist) {
                // 原本就没有点踩直接返回
                return;
            }
            // 取消用户点踩记录
            redisUtil.delMember("downVote:" + uid, id);
            // 更新评论点踩数量
            commentService.updateComment(id, "bad", false, 1);
        }
    }
}
