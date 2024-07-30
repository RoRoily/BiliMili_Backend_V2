package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private CurrentUser currentUser;

    /**
     * 更新用户部分个人信息
     * @param nickname  昵称
     * @param desc  个性签名
     * @param gender    性别：0 女 1 男 2 保密
     * @return
     */
    @PostMapping("/user/info/update")
    public ResponseResult updateUserInfo(@RequestParam("nickname") String nickname,
                                         @RequestParam("description") String desc,
                                         @RequestParam("gender") Integer gender) {
        Integer uid = currentUser.getUserId();
        try {
            return userService.updateUserInformation(uid, nickname, desc, gender);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseResult responseResult = new ResponseResult();
            responseResult.setCode(500);
            responseResult.setMessage("ERROR");
            return responseResult;
        }
    }

    /**
     * 更新用户头像
     * @param file  头像文件
     * @return  成功则返回新头像url
     */
    @PostMapping("/user/avatar/update")
    public ResponseResult updateUserAvatar(@RequestParam("file") MultipartFile file) {
        Integer uid = currentUser.getUserId();
        try {
            return userService.updateUserHeadPortrait(uid, file);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult(500, "头像更新失败", null);
        }
    }

    @GetMapping("/user/info/get-one")
    public ResponseResult getOneUserInfo(@RequestParam("uid") Integer uid) {
        ResponseResult responseResult = new ResponseResult();
        responseResult.setData(userService.getUserByUId(uid));
        return responseResult;
    }
}
