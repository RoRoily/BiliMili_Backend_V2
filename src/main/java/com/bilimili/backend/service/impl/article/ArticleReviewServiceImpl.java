package com.bilimili.backend.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.backend.mapper.ArticleMapper;
import com.bilimili.backend.pojo.Article;
import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.service.article.ArticleReviewService;
import com.bilimili.backend.service.article.ArticleService;
import com.bilimili.backend.service.utils.CurrentUser;
import com.bilimili.backend.service.video.VideoService;
import com.bilimili.backend.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArticleReviewServiceImpl implements ArticleReviewService {
    @Autowired
    private RedisUtil redisUtil;
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
     * @return 包含专栏数量的CustomResponse对象
     */
    @Override
    public CustomResponse getTotalByStatus(Integer status) {
        CustomResponse customResponse = new CustomResponse();
        if (!currentUser.isAdmin()) {
            customResponse.setCode(403);
            customResponse.setMessage("您不是管理员，无权访问");
            return customResponse;
        }
        Long total = (long) articleMapper.getArticleIdsByStatus(status).size();
        customResponse.setData(total);
        return customResponse;
    }

    /**
     * 获取分页对应状态的专栏
     *
     * @param status
     * @param page
     * @param quantity
     * @return CustomResponse对象，包含符合条件的视频列表
     */
    @Override
    public CustomResponse getArticlesByPage(Integer status, Integer page, Integer quantity) {
        CustomResponse customResponse = new CustomResponse();
        if (!currentUser.isAdmin()) {
            customResponse.setCode(403);
            customResponse.setMessage("您不是管理员，无权访问");
            return customResponse;
        }
        // 从 redis 获取待审核的专栏id集合，为了提升效率就不遍历数据库了，前提得保证 Redis 没崩，数据一致性采用定时同步或者中间件来保证
        Set<Object> set = redisUtil.getMembers("article_status:" + status);
        if(set == null || set.isEmpty()){
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", status);
            List<Integer> aids = articleMapper.getArticleIdsByStatus(status);
            set = new HashSet<>(aids);
        }
        if (!set.isEmpty()) {
            // 如果集合不为空，则在数据库主键查询，并且返回没有被删除的视频
            List<Map<String, Object>> mapList = articleService.getArticlesWithDataByIds(set, page, quantity);
            customResponse.setData(mapList);
        }
        return customResponse;
    }
}
