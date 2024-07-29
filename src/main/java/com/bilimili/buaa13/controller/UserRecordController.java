package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.CustomResponse;
import com.bilimili.buaa13.service.record.UserRecordService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class UserRecordController {
    @Autowired
    private UserRecordService userRecordService;
    @Autowired
    private CurrentUser currentUser;
    /**
     * 查询用户7天数据
     * @param uid 用户uid
     */
    @GetMapping("/user_record")
    public CustomResponse getUserRecord(@RequestParam("uid") Integer uid) {
        CustomResponse customResponse = new CustomResponse();
        try{
            Map<String,List<Integer>> map = new HashMap<>();
            List<Integer> play = userRecordService.getPlayRecordByUid(uid);
            List<Integer> love = userRecordService.getLoveRecordByUid(uid);
            List<Integer> collect = userRecordService.getCollectRecordByUid(uid);
            List<Integer> fans = userRecordService.getFansRecordByUid(uid);
            if(play != null && !play.isEmpty()){
                map.put("view",play);
            }
            else{
                customResponse.setMessage("播放量为空");
            }
            if(love != null && !love.isEmpty()){
                map.put("like",love);
            }
            else{
                customResponse.setMessage(customResponse.getMessage()+" 点赞量为空");
            }
            if(collect != null && !collect.isEmpty()){
                map.put("favorite",collect);
            }
            else{
                customResponse.setMessage(customResponse.getMessage()+" 收藏量为空");
            }
            if(fans != null && !fans.isEmpty()){
                map.put("follow",fans);
            }
            else{
                customResponse.setMessage(customResponse.getMessage()+" 粉丝量为空");
            }
            customResponse.setData(map);
            if(map.size()==4){
                customResponse.setMessage("获取用户近七天数据成功");
            }
            else {
                throw new Exception("数据量不足4个数组");
            }
        }catch (Exception e){
            e.printStackTrace();
            customResponse.setCode(500);
            customResponse.setMessage(customResponse.getMessage()+" 获取用户近七天数据失败");
        }
        return customResponse;
    }
}

