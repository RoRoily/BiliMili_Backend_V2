package com.bilimili.buaa13.service.article;

import java.util.Map;
import com.bilimili.buaa13.entity.ResponseResult;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface ArticleService {
    /**
     * 根据vid查询单个视频信息，包含用户信息和分区信息
     * @param aid 文章ID
     * @return 包含用户信息、分区信息、视频信息的map
     */


    public Map<String, Object> getArticleWithDataById(Integer aid);
    Map<String, Object> getArticleById(Integer aid);

    /**
     * 更新视频状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param aid   视频ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回，仅返回响应信息
     */
    ResponseResult updateArticleStatus(Integer aid, Integer status) throws IOException;


    public List<Map<String, Object>> getArticlesWithDataByIds(Set<Object> set, Integer index, Integer quantity);

    public ResponseResult getArticlesByPage(Integer uid, Integer page, Integer quantity);
}
