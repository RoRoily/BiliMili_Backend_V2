package com.bilimili.buaa13.service.video;

import com.bilimili.buaa13.entity.ResponseResult;

public interface VideoReviewService {
    /**
     * 查询对应状态的视频数量
     * @param status 状态 0审核中 1通过审核 2打回整改（指投稿信息不符） 3 视频违规删除（视频内容违规）
     * @return 包含视频数量的响应对象
     */
    ResponseResult getTotalNumberByStatus(Integer status);

    /**
     * 获取分页对应状态的视频
     * @return 响应对象，包含符合条件的视频列表
     */
    ResponseResult getVideosByPageAndStatus(Integer status, Integer page, Integer quantity);
}
