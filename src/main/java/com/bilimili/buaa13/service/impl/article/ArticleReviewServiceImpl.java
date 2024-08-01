package com.bilimili.buaa13.service.impl.article;

import com.bilimili.buaa13.mapper.ArticleMapper;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.article.ArticleReviewService;
import com.bilimili.buaa13.service.article.ArticleService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ArticleReviewServiceImpl implements ArticleReviewService {
    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private ArticleService articleService;
    @Autowired
    private ArticleMapper articleMapper;
    /**
     * 查询对应状态的专栏数量
     *
     * @param status 状态 0审核中 1通过审核 2打回整改（指投稿信息不符） 3专栏违规删除
     * @return 包含专栏数量的响应对象
     */
    @Override
    public ResponseResult getTotalNumberByStatus(Integer status) {
        ResponseResult responseResult = new ResponseResult();
        if (!currentUser.isAdmin()) {
            responseResult.setCode(403);
            responseResult.setMessage("您不是管理员，无权访问");
            return responseResult;
        }
        Long total = (long) articleMapper.getArticleIdsByStatus(status).size();
        responseResult.setData(total);
        return responseResult;
    }

    /**
     * 获取分页对应状态的专栏
     *
     * @param status 状态
     * @param page 页数
     * @param quantity 一页多少条目
     * @return 响应对象，包含符合条件的视频列表
     */
    @Override
    public ResponseResult getArticlesByPage(Integer status, Integer page, Integer quantity) {
        ResponseResult responseResult = new ResponseResult();
        if (!currentUser.isAdmin()) {
            responseResult.setCode(403);
            responseResult.setMessage("您不是管理员，无权访问");
            return responseResult;
        }
        // 从 redis 获取待审核的专栏id集合，为了提升效率就不遍历数据库了，前提得保证 Redis 没崩，数据一致性采用定时同步或者中间件来保证
        //注释Redis
        //Set<Object> set = redisUtil.getMembers("article_status:" + status);
        /*if(set == null || set.isEmpty()){
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", status);
            List<Integer> aids = articleMapper.getArticleIdsByStatus(status);
            set = new HashSet<>(aids);
        }*/
        List<Integer> aids = articleMapper.getArticleIdsByStatus(status);
        Set<Integer> aidSet = new HashSet<>(aids);
        if (!aidSet.isEmpty()) {
            // 如果集合不为空，则在数据库主键查询，并且返回没有被删除的视频
            List<Map<String, Object>> mapList = articleService.getArticlesWithDataByIds(Collections.singleton(aidSet), page, quantity);
            responseResult.setData(mapList);
        }
        return responseResult;
    }
}
