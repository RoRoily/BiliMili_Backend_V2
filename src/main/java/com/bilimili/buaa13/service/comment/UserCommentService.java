package com.bilimili.buaa13.service.comment;

import java.util.Map;

public interface UserCommentService {

    /**
     * 获取用户点赞和点踩的评论集合
     * @param uid   当前用户
     * @return  点赞和点踩的评论集合
     */
    Map<String, Object> getUserUpVoteAndDownVote(Integer uid);

    /**
     * 点赞或点踩某条评论
     * @param uid   当前用户id
     * @param cid    评论id
     * @param isLike true 赞 false 踩
     * @param isCancel true 取消  false 点中
     */
    void setUserUpVoteOrDownVote(Integer uid, Integer cid, boolean isLike, boolean isCancel);

}
