package com.bilimili.buaa13.controller;


import com.bilimili.buaa13.entity.CommentTree;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.comment.CommentService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public ResponseResult addComment(
            @RequestParam("vid") Integer vid,
            @RequestParam("root_id") Integer rootId,
            @RequestParam("parent_id") Integer parentId,
            @RequestParam("to_user_id") Integer toUserId,
            @RequestParam("content") String content ) {
        Integer uid = currentUser.getUserId();

        ResponseResult responseResult = new ResponseResult();
        CommentTree commentTree = commentService.sendComment(vid, uid, rootId, parentId, toUserId, content);
        if (commentTree == null) {
            responseResult.setCode(500);
            responseResult.setMessage("发送失败！");
        }
        responseResult.setData(commentTree);
        return responseResult;
    }


}
