package com.bilimili.backend.service.article;

import com.bilimili.backend.pojo.CustomResponse;

public interface ArticleReviewService {
    /**
     * 查询对应状态的专栏数量
     * @param status 状态 0审核中 1通过审核 2打回整改（指投稿信息不符） 3专栏违规删除
     * @return 包含专栏数量的CustomResponse对象
     */
    CustomResponse getTotalByStatus(Integer status);
    /**
     * 获取分页对应状态的专栏
     * @return CustomResponse对象，包含符合条件的视频列表
     */
    CustomResponse getArticlesByPage(Integer status, Integer page, Integer quantity);
}
