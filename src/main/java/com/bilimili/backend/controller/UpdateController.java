package com.bilimili.backend.controller;


import com.bilimili.backend.pojo.CommentTree;
import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.service.comment.CommentService;
import com.bilimili.backend.service.utils.CurrentUser;
import com.bilimili.backend.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
@RestController
public class UpdateController {
    @Autowired
    private CommentService commentService;
    @Autowired
    private CurrentUser currentUser;
    @Autowired
    private RedisUtil redisUtil;

    /**
     * 发表动态
     * @param vid   视频id
     * @param rootId    根评论id
     * @param parentId  被回复评论id
     * @param toUserId  被回复者uid
     * @param content   评论内容
     * @return  响应对象
     */
    @PostMapping("/update/add")
    public CustomResponse addComment(
            @RequestParam("vid") Integer vid,
            @RequestParam("root_id") Integer rootId,
            @RequestParam("parent_id") Integer parentId,
            @RequestParam("to_user_id") Integer toUserId,
            @RequestParam("content") String content ) {
        Integer uid = currentUser.getUserId();

        CustomResponse customResponse = new CustomResponse();
        CommentTree commentTree = commentService.sendComment(vid, uid, rootId, parentId, toUserId, content);
        if (commentTree == null) {
            customResponse.setCode(500);
            customResponse.setMessage("发送失败！");
        }
        customResponse.setData(commentTree);
        return customResponse;
    }


}
