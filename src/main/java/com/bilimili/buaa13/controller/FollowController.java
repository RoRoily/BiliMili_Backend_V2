package com.bilimili.buaa13.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.bilimili.buaa13.entity.CustomResponse;
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
    public CustomResponse getAllFollowingForUser(@RequestParam("uid") Integer uid) {
        Integer loginUid = currentUser.getUserId();
        CustomResponse customResponse = new CustomResponse();
        if (Objects.equals(loginUid, uid)) {
            customResponse.setData(followService.getUidFollow(uid, true));
        } else {
            customResponse.setData(followService.getUidFollow(uid, false));
        }
        return customResponse;
    }
    /**
     * 判断自己是否是某个用户的粉丝
     */
    @GetMapping("/isFans")
    public CustomResponse isFans(@RequestParam("uidFollow") Integer up,@RequestParam("uidFans") Integer fan) {
        CustomResponse customResponse = new CustomResponse();
        List<Integer> fans = followService.getUidFans(up,true);
        customResponse.setData(false);
        if(fans==null || fans.isEmpty()) {
            customResponse.setData(false);
        }
        else if (fans.contains(fan)) {
            customResponse.setData(true);
        }
        System.out.println(customResponse.getData());
        return customResponse;
    }
    /**
     * 测试
     */
    @GetMapping("/fans/get-all/userTest")
    public CustomResponse getAllFansForUserTest(@RequestParam("uid") Integer uid) {
        CustomResponse customResponse = new CustomResponse();
        customResponse.setData(followService.getUidFans(uid, true));
        return customResponse;
    }
    /**
     * 站内用户请求某个用户的粉丝列表（需要jwt鉴权）
     * @param uid   被查看的用户ID
     * @return  包含关注列表的响应对象
     */
    @GetMapping("/fans/get-all/user")
    public CustomResponse getAllFansForUser(@RequestParam("uid") Integer uid) {
        Integer loginUid = currentUser.getUserId();
        CustomResponse customResponse = new CustomResponse();
        if (Objects.equals(loginUid, uid)) {
            customResponse.setData(followService.getUidFans(uid, true));
        } else {
            customResponse.setData(followService.getUidFans(uid, false));
        }
        return customResponse;
    }
    /**
     * 站内用户请求关注某个用户
     * @param uidFollow   被关注者ID
     * @return  包含关注列表的响应对象
     */
    @PostMapping("/following/update")
    public CustomResponse addFollowing(@RequestParam("uidFollow") Integer uidFollow,
                                       @RequestParam("uidFans") Integer uidFans,
                                       @RequestParam("isfollowing") boolean isfollowing) throws JsonProcessingException {
        CustomResponse customResponse = new CustomResponse();
        System.out.println(isfollowing);
        if(!isfollowing){
            followService.addFollow(uidFollow, uidFans);
            customResponse.setMessage("关注成功");
        }
        else{
            followService.delFollow(uidFollow, uidFans);
            customResponse.setMessage("取关成功");
        }
        return customResponse;
    }
    /**
     * 站内用户请求取关某个用户
     * @param uidFollow   被关注者ID
     * @return  包含关注列表的响应对象
     */
    @PostMapping("/follow/delFollow-one/")
    public CustomResponse delFollowing(@RequestParam("uidFollow") Integer uidFollow) throws JsonProcessingException {
        Integer loginUid = currentUser.getUserId();
        CustomResponse customResponse = new CustomResponse();
        followService.delFollow(uidFollow, loginUid);
        customResponse.setMessage("取关成功");
        return customResponse;
    }
    /**
     * 站内用户请求修改可见权限
     * @param visible 权限
     * @return  包含关注列表的响应对象
     */
    @PostMapping("/follow/updateVisible/")
    public CustomResponse updateUserVisible(@RequestParam("visible") Integer visible){
        CustomResponse customResponse = new CustomResponse();
        Integer loginUid = currentUser.getUserId();
        followService.updateVisible(loginUid, visible);
        customResponse.setMessage("更新权限成功");
        return customResponse;
    }

    /**
     * 查询用户是否关注了另一个用户
     * @param uidFollow   被关注者
     * @param uidFans   粉丝ID
     * @return  包含关注列表的响应对象
     */
    @GetMapping("/followed/checkRelation")
    public CustomResponse getIsHisFans(@RequestParam("uidFollow") Integer uidFollow,
                                                @RequestParam("uidFans") Integer uidFans) {
        try{
            CustomResponse customResponse = new CustomResponse();
            boolean flag = followService.isHisFans(uidFollow,uidFans);
            if(flag){
                customResponse.setCode(200);
                customResponse.setMessage("查询成功，是该up主的粉丝");
            }
            if(flag){
                customResponse.setCode(200);
                customResponse.setMessage("查询成功，不是该up主的粉丝");
            }
            customResponse.setData(flag);
            return customResponse;}
        catch (Exception e) {
            e.printStackTrace();
            return new CustomResponse(500, "查询粉丝关系操作失败", null);
        }

    }
}
