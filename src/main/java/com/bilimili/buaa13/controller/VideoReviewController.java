package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.service.video.VideoReviewService;
import com.bilimili.buaa13.service.video.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class VideoReviewController {

    @Autowired
    private VideoReviewService videoReviewService;

    @Autowired
    private VideoService videoService;

    @Autowired
    private CurrentUser currentUser;

    /**
     * 审核 查询对应状态的视频数量
     * @param status 状态 0待审核 1通过 2未通过
     * @return
     */
    @GetMapping("/review/video/total")
    public ResponseResult getTotal(@RequestParam("vstatus") Integer status) {
        return videoReviewService.getTotalNumberByStatus(status);
    }

    /**
     * 审核 分页查询对应状态视频
     * @param status 状态 0待审核 1通过 2未通过
     * @param page  当前页
     * @param quantity  每页的数量
     * @return
     */
    @GetMapping("/review/video/getpage")
    public ResponseResult getVideos(@RequestParam("vstatus") Integer status,
                                    @RequestParam(value = "page", defaultValue = "1") Integer page,
                                    @RequestParam(value = "quantity", defaultValue = "10") Integer quantity) {
        return videoReviewService.getVideosByPageAndStatus(status, page, quantity);
    }

    /**
     * 审核 查询单个视频详情
     * @param vid 视频id
     * @return
     */
    @GetMapping("/review/video/getone")
    public ResponseResult getOneVideo(@RequestParam("vid") Integer vid) {
        ResponseResult responseResult = new ResponseResult();
        if (!currentUser.isAdmin()) {
            responseResult.setCode(403);
            responseResult.setMessage("您不是管理员，无权访问");
            return responseResult;
        }
        Map<String, Object> map = videoService.getVideoWithDataByVideoId(vid);
        responseResult.setData(map);    // 如果是是空照样返回，前端自动处理
        return responseResult;
    }
}
