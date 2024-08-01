package com.bilimili.buaa13.service.impl.article;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.buaa13.mapper.ArticleMapper;
import com.bilimili.buaa13.entity.*;
import com.bilimili.buaa13.service.article.ArticleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.OssUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private UserService userService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;


    @Override
    public Map<String, Object> getArticleWithDataById(Integer aid) {
        Map<String, Object> map = new HashMap<>();
        // 先查询 redis
        Article article = redisUtil.getObject("article:" + aid, Article.class);
        if (article == null) {
            // redis 查不到再查数据库
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("aid", aid).ne("status", 3); //未被删除
            article = articleMapper.selectOne(queryWrapper);
            if (article != null) {
                Article article1 = article;
                CompletableFuture.runAsync(() -> {
                    redisUtil.setExObjectValue("article" + aid, article1);    // 异步更新到redis
                }, taskExecutor);
            } else  {
                return null;
            }
        }
        // 多线程异步并行查询用户信息和分区信息并封装
        Article finalArticle = article;
        Map<String, Object> finalMap = map;
        CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
            finalMap.put("user", userService.getUserByUId(finalArticle.getUid()));
        }, taskExecutor);
        map.put("article",article);
        // 使用join()等待userFuture和categoryFuture任务完成
        userFuture.join();
        map = getArticleById(aid);
        return map;
    }



    /**
     * 获取分页对应状态的专栏
     *
     * @param uid 用户id
     * @param page 分页
     * @param quantity 每页条数
     * @return 响应对象，包含符合条件的专栏列表
     */
    @Override
    public ResponseResult getArticlesByPage(Integer uid, Integer page, Integer quantity) {
        ResponseResult responseResult = new ResponseResult();
        Set<Object> set = redisUtil.getMembers("article_uid:" + uid);
        if(set == null || set.isEmpty()){
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("uid", uid);
            List<Integer> aids = articleMapper.getArticleIdsByUids(uid);
            set = new HashSet<>(aids);
        }
        Map<String,Object> map = new HashMap<>();
        if (!set.isEmpty()) {
            // 如果集合不为空，则在数据库主键查询，并且返回没有被删除的专栏
            List<Map<String, Object>> mapList = getArticlesWithDataByIds(set, page, quantity);
            map.put("count", mapList.size());
            map.put("list", mapList);
        }
        else map.put("count", 0);
        responseResult.setData(map);
        return responseResult;
    }

    /**
     * 根据id分页获取专栏信息
     * @param set   要查询的专栏id集合
     * @param index 分页页码 为空默认是1
     * @param quantity  每一页查询的数量 为空默认是10
     * @return  包含用户信息、分区信息、专栏信息的map列表
     */
    @Override
    public List<Map<String, Object>> getArticlesWithDataByIds(Set<Object> set, Integer index, Integer quantity) {
        if (index == null) {
            index = 1;
        }
        if (quantity == null) {
            quantity = 10;
        }
        int startIndex = (index - 1) * quantity;
        int endIndex = startIndex + quantity;
        // 检查数据是否足够满足分页查询
        if (startIndex > set.size()) {
            // 如果数据不足以填充当前分页，返回空列表
            return Collections.emptyList();
        }
        List<Article> articleList = new ArrayList<>();

        // 直接数据库分页查询    （平均耗时 13ms）
        List<Object> idList = new ArrayList<>(set);
        endIndex = Math.min(endIndex, idList.size());
        List<Object> sublist = idList.subList(startIndex, endIndex);
        QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("aid", sublist).ne("status", 3);
        articleList = articleMapper.selectList(queryWrapper);
        if (articleList.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> mapList = new ArrayList<>();
        for(Article article : articleList){
            mapList.add(getArticleMap(article));
        }
        return mapList;
    }

    /**
     * 更新专栏状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param aid   专栏ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回，仅返回响应信息
     */

    @Override
    public ResponseResult updateArticleStatus(Integer aid, Integer status) throws IOException {
        ResponseResult responseResult = new ResponseResult();
        Integer userId = currentUser.getUserId();
        if (status == 1 || status == 2) {
            if (!currentUser.isAdmin()) {
                responseResult.setCode(403);
                responseResult.setMessage("您不是管理员，无权访问");
                return responseResult;
            }
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("aid", aid).ne("status", 3);
            Article article   = articleMapper.selectOne(queryWrapper);
            if (article == null) {
                responseResult.setCode(404);
                responseResult.setMessage("文章不见了");
                return responseResult;
            }
            Integer lastStatus = article.getStatus();
            article.setStatus(1);
            UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("aid", aid).set("status", 1);     // 更新专栏状态审核通过
            int flag = articleMapper.update(null, updateWrapper);
            if (flag > 0) {
                // 更新成功
                redisUtil.delMember("article_status:" + lastStatus, aid);     // 从旧状态移除
                redisUtil.addMember("article_status:1", aid);     // 加入新状态
                redisUtil.zset("user_article_upload:" + article.getUid(), article.getAid());
                redisUtil.delValue("article:" + aid);     // 删除旧的专栏信息
                return responseResult;
            } else {
                // 更新失败，处理错误情况
                responseResult.setCode(500);
                responseResult.setMessage("更新状态失败");
                return responseResult;
            }
        } else if (status == 3) {
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("aid", aid).ne("status", 3);
            Article article = articleMapper.selectOne(queryWrapper);
            if (article == null) {
                responseResult.setCode(404);
                responseResult.setMessage("专栏不见了QAQ");
                return responseResult;
            }
            if (userId.equals(article.getUid()) || currentUser.isAdmin()) {
                String contentUrl = article.getContentUrl();
                String articleName = contentUrl.split("aliyuncs.com/")[1];  // OSS专栏文件名
                String coverUrl = article.getCoverUrl();
                String coverName = coverUrl.split("aliyuncs.com/")[1];  // OSS封面文件名
                Integer lastStatus = article.getStatus();
                UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("aid", aid).set("status", 3).set("delete_date", new Date());     // 更新专栏状态已删除
                int flag = articleMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    redisUtil.delMember("article_status:" + lastStatus, aid);     // 从旧状态移除
                    redisUtil.delValue("article:" + aid);     // 删除旧的专栏信息
                    redisUtil.zsetDelMember("user_article_upload:" + article.getUid(), article.getVid());
                    // 搞个异步线程去删除OSS的源文件
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(articleName), taskExecutor);
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(coverName), taskExecutor);
                    // 批量删除该专栏下的全部评论缓存
                    CompletableFuture.runAsync(() -> {
                        Set<Object> set = redisUtil.zReverange("comment_article:" + aid, 0, -1);
                        List<String> list = new ArrayList<>();
                        set.forEach(id -> list.add("comment_reply:" + id));
                        list.add("comment_article:" + aid);
                        redisUtil.delValues(list);
                    }, taskExecutor);
                    return responseResult;
                } else {
                    // 更新失败，处理错误情况
                    responseResult.setCode(500);
                    responseResult.setMessage("更新状态失败");
                    return responseResult;
                }
            } else {
                responseResult.setCode(403);
                responseResult.setMessage("您没有权限删除专栏");
                return responseResult;
            }
        }
        responseResult.setCode(500);
        responseResult.setMessage("更新状态失败");
        return responseResult;
    }

    @Override
    public Map<String, Object> getArticleById(Integer aid) {
        QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("aid", aid).ne("status", 3); //未被删除
        Article article = articleMapper.selectOne(queryWrapper);
        return getArticleMap(article);
    }

    private Map<String,Object> getArticleMap(Article article){
        Map<String, Object> map = new HashMap<>();
        map.put("aid", article.getAid());
        map.put("status", article.getStatus());
        map.put("title", article.getTitle());
        map.put("user", userService.getUserByUId(article.getUid()));
        map.put("cover_url", article.getCoverUrl());
        map.put("content_url", article.getContentUrl());
        map.put("article", article);
        return map;
    }
}
