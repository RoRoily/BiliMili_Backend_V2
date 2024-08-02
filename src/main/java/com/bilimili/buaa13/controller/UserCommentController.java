package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.comment.UserCommentService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
public class UserCommentController {
    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private UserCommentService userCommentService;

    /**
     * 获取用户点赞点踩评论集合
     */
    @GetMapping("/comment/get-like-and-dislike")
    public ResponseResult getLikeAndDislike() {
        Integer uid = currentUser.getUserId();

        ResponseResult response = new ResponseResult();
        response.setCode(200);
        response.setData(userCommentService.getUserUpVoteAndDownVote(uid));

        return response;
    }

    /**
     * 点赞或点踩某条评论
     * @param id    评论id
     * @param isLike true 赞 false 踩
     * @param isCancel  true 取消 false 点中
     */
    @PostMapping("/comment/love-or-not")
    public ResponseResult loveOrNot(@RequestParam("id") Integer id,
                                    @RequestParam("isLike") boolean isLike,
                                    @RequestParam("isCancel") boolean isCancel) {
        Integer uid = currentUser.getUserId();
        userCommentService.setUserUpVoteOrDownVote(uid, id, isLike, isCancel);
        return new ResponseResult();
    }

    /**
     * 获取UP主觉得很淦的评论
     * @param uid   UP主uid
     * @return  点赞的评论id列表
     */
    @GetMapping("/comment/get-up-like")
    public ResponseResult getUpLike(@RequestParam("uid") Integer uid) {
        ResponseResult responseResult = new ResponseResult();
        Map<String, Object> map = userCommentService.getUserUpVoteAndDownVote(uid);
        responseResult.setData(map.get("userLike"));
        return responseResult;
    }
}
