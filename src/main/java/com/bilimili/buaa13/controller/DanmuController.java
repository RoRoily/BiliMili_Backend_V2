package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.Danmu;
import com.bilimili.buaa13.service.danmu.DanmuService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
public class DanmuController {
    @Autowired
    private DanmuService danmuService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CurrentUser currentUser;

    /**
     * 获取弹幕列表
     * @param vid   视频ID
     * @return  CustomResponse对象
     */
    @GetMapping("/danmu-list/{vid}")
    public ResponseResult getDanmuList(@PathVariable("vid") String vid) {
        Set<Object> idset = redisUtil.getMembers("danmu_idset:" + vid);
        List<Danmu> danmuList = danmuService.getDanmuListByIdset(idset);
        ResponseResult responseResult = new ResponseResult();
        responseResult.setData(danmuList);
        return responseResult;
    }

    /**
     * 删除弹幕
     * @param id    弹幕id
     * @return  响应对象
     */
    @PostMapping("/danmu/delete")
    public ResponseResult deleteDanmu(@RequestParam("id") Integer id) {
        Integer loginUid = currentUser.getUserId();
        return danmuService.deleteDanmu(id, loginUid, currentUser.isAdmin());
    }
}
