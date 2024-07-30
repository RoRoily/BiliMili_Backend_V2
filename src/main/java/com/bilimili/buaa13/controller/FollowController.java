package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.bilimili.buaa13.service.user.FollowService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
public class FollowController {
    @Autowired
    private FollowService followService;
    @Autowired
    private CurrentUser currentUser;
    /**
     * 站内用户请求某个用户的关注列表（需要jwt鉴权）
     * @param uid   被查看的用户ID
     * @return  包含关注列表的响应对象
     */
    @GetMapping("/following/get-all/user")
    public ResponseResult getAllFollowingForUser(@RequestParam("uid") Integer uid) {
        Integer loginUid = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        if (Objects.equals(loginUid, uid)) {
            responseResult.setData(followService.getUidFollow(uid, true));
        } else {
            responseResult.setData(followService.getUidFollow(uid, false));
        }
        return responseResult;
    }
    /**
     * 判断自己是否是某个用户的粉丝
     */
    @GetMapping("/isFans")
    public ResponseResult isFans(@RequestParam("uidFollow") Integer up, @RequestParam("uidFans") Integer fan) {
        ResponseResult responseResult = new ResponseResult();
        List<Integer> fans = followService.getUidFans(up,true);
        responseResult.setData(false);
        if(fans==null || fans.isEmpty()) {
            responseResult.setData(false);
        }
        else if (fans.contains(fan)) {
            responseResult.setData(true);
        }
        System.out.println(responseResult.getData());
        return responseResult;
    }
    /**
     * 测试
     */
    @GetMapping("/fans/get-all/userTest")
    public ResponseResult getAllFansForUserTest(@RequestParam("uid") Integer uid) {
        ResponseResult responseResult = new ResponseResult();
        responseResult.setData(followService.getUidFans(uid, true));
        return responseResult;
    }
    /**
     * 站内用户请求某个用户的粉丝列表（需要jwt鉴权）
     * @param uid   被查看的用户ID
     * @return  包含关注列表的响应对象
     */
    @GetMapping("/fans/get-all/user")
    public ResponseResult getAllFansForUser(@RequestParam("uid") Integer uid) {
        Integer loginUid = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        if (Objects.equals(loginUid, uid)) {
            responseResult.setData(followService.getUidFans(uid, true));
        } else {
            responseResult.setData(followService.getUidFans(uid, false));
        }
        return responseResult;
    }
    /**
     * 站内用户请求关注某个用户
     * @param uidFollow   被关注者ID
     * @return  包含关注列表的响应对象
     */
    @PostMapping("/following/update")
    public ResponseResult addFollowing(@RequestParam("uidFollow") Integer uidFollow,
                                       @RequestParam("uidFans") Integer uidFans,
                                       @RequestParam("isfollowing") boolean isfollowing) throws JsonProcessingException {
        ResponseResult responseResult = new ResponseResult();
        System.out.println(isfollowing);
        if(!isfollowing){
            followService.addFollow(uidFollow, uidFans);
            responseResult.setMessage("关注成功");
        }
        else{
            followService.delFollow(uidFollow, uidFans);
            responseResult.setMessage("取关成功");
        }
        return responseResult;
    }
    /**
     * 站内用户请求取关某个用户
     * @param uidFollow   被关注者ID
     * @return  包含关注列表的响应对象
     */
    @PostMapping("/follow/delFollow-one/")
    public ResponseResult delFollowing(@RequestParam("uidFollow") Integer uidFollow) throws JsonProcessingException {
        Integer loginUid = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        followService.delFollow(uidFollow, loginUid);
        responseResult.setMessage("取关成功");
        return responseResult;
    }
    /**
     * 站内用户请求修改可见权限
     * @param visible 权限
     * @return  包含关注列表的响应对象
     */
    @PostMapping("/follow/updateVisible/")
    public ResponseResult updateUserVisible(@RequestParam("visible") Integer visible){
        ResponseResult responseResult = new ResponseResult();
        Integer loginUid = currentUser.getUserId();
        followService.updateVisible(loginUid, visible);
        responseResult.setMessage("更新权限成功");
        return responseResult;
    }

    /**
     * 查询用户是否关注了另一个用户
     * @param uidFollow   被关注者
     * @param uidFans   粉丝ID
     * @return  包含关注列表的响应对象
     */
    @GetMapping("/followed/checkRelation")
    public ResponseResult getIsHisFans(@RequestParam("uidFollow") Integer uidFollow,
                                       @RequestParam("uidFans") Integer uidFans) {
        try{
            ResponseResult responseResult = new ResponseResult();
            boolean flag = followService.isHisFans(uidFollow,uidFans);
            if(flag){
                responseResult.setCode(200);
                responseResult.setMessage("查询成功，是该up主的粉丝");
            }
            if(flag){
                responseResult.setCode(200);
                responseResult.setMessage("查询成功，不是该up主的粉丝");
            }
            responseResult.setData(flag);
            return responseResult;}
        catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult(500, "查询粉丝关系操作失败", null);
        }

    }
}
