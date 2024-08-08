package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.message.ChatDetailedService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatDetailedController {
    @Autowired
    private ChatDetailedService chatDetailedService;

    @Autowired
    private CurrentUser currentUser;

    /**
     * 获取更多历史消息记录
     * @param uid   聊天对象的UID
     * @param offset    偏移量，即已经获取过的消息数量，从哪条开始获取更多
     * @return  响应对象，包含更多消息记录的map
     */
    @GetMapping("/msg/chat-detailed/get-more")
    public ResponseResult getMoreChatDetails(@RequestParam("uid") Integer uid,
                                             @RequestParam("offset") Long offset) {
        Integer loginUid = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        responseResult.setData(chatDetailedService.getMessage(uid, loginUid, offset));
        return responseResult;
    }

    /**
     * 删除消息
     * @param id    消息ID
     * @return  响应对象
     */
    @PostMapping("/msg/chat-detailed/delete")
    public ResponseResult delDetail(@RequestParam("id") Integer id) {
        Integer loginUid = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        if (!chatDetailedService.deleteChatDetail(id, loginUid)) {
            responseResult.setCode(500);
            responseResult.setMessage("删除消息失败");
        }
        return responseResult;
    }
}
