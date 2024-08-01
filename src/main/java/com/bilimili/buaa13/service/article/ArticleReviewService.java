package com.bilimili.buaa13.service.article;

import com.bilimili.buaa13.entity.ResponseResult;

public interface ArticleReviewService {
    /**
     * 查询对应状态的专栏数量
     * @param status 状态 0审核中 1通过审核 2打回整改（指投稿信息不符） 3专栏违规删除
     * @return 包含专栏数量的响应对象
     */
    ResponseResult getTotalNumberByStatus(Integer status);
    /**
     * 获取分页对应状态的专栏
     * @return 响应对象，包含符合条件的视频列表
     */
    ResponseResult getArticlesByPage(Integer status, Integer page, Integer quantity);
}
