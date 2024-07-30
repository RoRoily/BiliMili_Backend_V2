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
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.mapper.VideoStatsMapper;
import com.bilimili.buaa13.service.category.CategoryService;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.service.video.VideoStatsService;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.OssUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;





@Slf4j
@Service
public class ArticleServiceImpl implements ArticleService {



    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoStatsMapper videoStatsMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private VideoStatsService videoStatsService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private ESUtil esUtil;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

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
        CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
            map.put("user", userService.getUserByUId(finalArticle.getUid()));
        }, taskExecutor);
        map.put("article",article);
        // 使用join()等待userFuture和categoryFuture任务完成
        userFuture.join();
        return map;
    }


    /**
     * 根据id分页获取视频信息，包括用户和分区信息
     * @param set   要查询的视频id集合
     * @param index 分页页码 为空默认是1
     * @param quantity  每一页查询的数量 为空默认是10
     * @return  包含用户信息、分区信息、视频信息的map列表
     */
    /**
     * 获取分页对应状态的专栏
     *
     * @param uid
     * @param page
     * @param quantity
     * @return CustomResponse对象，包含符合条件的视频列表
     */
    @Override
    public CustomResponse getArticlesByPage(Integer uid, Integer page, Integer quantity) {
        CustomResponse customResponse = new CustomResponse();
        /*if (!currentUser.isAdmin()) {
            customResponse.setCode(403);
            customResponse.setMessage("您不是管理员，无权访问");
            return customResponse;
        }*/
        // 从 redis 获取待审核的专栏id集合，为了提升效率就不遍历数据库了，前提得保证 Redis 没崩，数据一致性采用定时同步或者中间件来保证
        Set<Object> set = redisUtil.getMembers("article_uid:" + uid);
        if(set == null || set.isEmpty()){
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("uid", uid);
            List<Integer> aids = articleMapper.getArticleIdsByUids(uid);
            set = new HashSet<>(aids);
        }
        Map<String,Object> map = new HashMap<>();
        if (!set.isEmpty()) {
            // 如果集合不为空，则在数据库主键查询，并且返回没有被删除的视频
            List<Map<String, Object>> mapList = getArticlesWithDataByIds(set, page, quantity);
            map.put("count", mapList.size());
            map.put("list", mapList);
        }
        else map.put("count", 0);
        customResponse.setData(map);
        return customResponse;
    }

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
        List<Article> articleList = new CopyOnWriteArrayList<>();   // 使用线程安全的集合类 CopyOnWriteArrayList 保证多线程处理共享List不会出现并发问题

        // 直接数据库分页查询    （平均耗时 13ms）
        List<Object> idList = new ArrayList<>(set);
        endIndex = Math.min(endIndex, idList.size());
        List<Object> sublist = idList.subList(startIndex, endIndex);
        QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("aid", sublist).ne("status", 3);
        articleList = articleMapper.selectList(queryWrapper);
        if (articleList.isEmpty()) return Collections.emptyList();

        // 并行处理每一个视频，提高效率
        // 先将videoList转换为Stream
        Stream<Article> articleStream = articleList.stream();
        List<Map<String, Object>> mapList = articleStream.parallel() // 利用parallel()并行处理
                .map(article -> {
//                    long start = System.currentTimeMillis();
//                    System.out.println("================ 开始查询 " + article.getVid() + " 号视频相关信息 ===============   当前时间 " + start);
                    Map<String, Object> map = new HashMap<>();
                    map.put("article", article);

                    CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                        map.put("user", userService.getUserByUId(article.getUid()));
                        //map.put("stats", articleStatsService.getArticleStatsById(article.getAid()));
                    }, taskExecutor);

//                    CompletableFuture<Void> categoryFuture = CompletableFuture.runAsync(() -> {
//                        map.put("category", categoryService.getCategoryById(article.getMcId(), article.getScId()));
//                    }, taskExecutor);

                    // 使用join()等待全部任务完成
                    userFuture.join();
                    //categoryFuture.join();
//                    long end = System.currentTimeMillis();
//                    System.out.println("================ 结束查询 " + article.getVid() + " 号视频相关信息 ===============   当前时间 " + end + "   耗时 " + (end - start));

                    return map;
                })
                .collect(Collectors.toList());

//        end = System.currentTimeMillis();
//        System.out.println("封装耗时：" + (end - start));
        return mapList;
    }

    /**
     * 更新专栏状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param aid   专栏ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回，仅返回响应信息
     */

    @Override
    public CustomResponse updateArticleStatus(Integer aid, Integer status) throws IOException {
        CustomResponse customResponse = new CustomResponse();
        Integer userId = currentUser.getUserId();
        if (status == 1 || status == 2) {
            if (!currentUser.isAdmin()) {
                customResponse.setCode(403);
                customResponse.setMessage("您不是管理员，无权访问");
                return customResponse;
            }
            if (status == 1) {
                QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("aid", aid).ne("status", 3);
                Article article   = articleMapper.selectOne(queryWrapper);
                if (article == null) {
                    customResponse.setCode(404);
                    customResponse.setMessage("文章不见了");
                    return customResponse;
                }
                Integer lastStatus = article.getStatus();
                article.setStatus(1);
                UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("aid", aid).set("status", 1);     // 更新视频状态审核通过
                int flag = articleMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    //  esUtil.updateArticle(article);  // 更新ES视频文档
                    redisUtil.delMember("article_status:" + lastStatus, aid);     // 从旧状态移除
                    redisUtil.addMember("article_status:1", aid);     // 加入新状态
                    redisUtil.zset("user_article_upload:" + article.getUid(), article.getAid());
                    redisUtil.delValue("article:" + aid);     // 删除旧的视频信息
                    return customResponse;
                } else {
                    // 更新失败，处理错误情况
                    customResponse.setCode(500);
                    customResponse.setMessage("更新状态失败");
                    return customResponse;
                }
            }
            else {
                // 目前逻辑跟上面一样的，但是可能以后要做一些如 记录不通过原因 等操作，所以就分开写了
                QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("aid", aid).ne("status", 3);
                Article article = articleMapper.selectOne(queryWrapper);
                if (article == null) {
                    customResponse.setCode(404);
                    customResponse.setMessage("视频不见了QAQ");
                    return customResponse;
                }
                Integer lastStatus = article.getStatus();
                article.setStatus(2);
                UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("aid", aid).set("status", 2);     // 更新视频状态审核不通过
                int flag = articleMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    //  esUtil.updateArticle(article);  // 更新ES视频文档
                    redisUtil.delMember("article_status:" + lastStatus, aid);     // 从旧状态移除
                    redisUtil.addMember("article_status:2", aid);     // 加入新状态
                    redisUtil.zsetDelMember("user_article_upload:" + article.getUid(), article.getAid());
                    redisUtil.delValue("article:" + aid);     // 删除旧的视频信息
                    return customResponse;
                } else {
                    // 更新失败，处理错误情况
                    customResponse.setCode(500);
                    customResponse.setMessage("更新状态失败");
                    return customResponse;
                }
            }
        } else if (status == 3) {
            QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("aid", aid).ne("status", 3);
            Article article = articleMapper.selectOne(queryWrapper);
            if (article == null) {
                customResponse.setCode(404);
                customResponse.setMessage("视频不见了QAQ");
                return customResponse;
            }
            if (Objects.equals(userId, article.getUid()) || currentUser.isAdmin()) {
                String contentUrl = article.getContentUrl();
                String articlePrefix = contentUrl.split("aliyuncs.com/")[1];  // OSS视频文件名
                String coverUrl = article.getCoverUrl();
                String coverPrefix = coverUrl.split("aliyuncs.com/")[1];  // OSS封面文件名
                Integer lastStatus = article.getStatus();
                UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("aid", aid).set("status", 3).set("delete_date", new Date());     // 更新视频状态已删除
                int flag = articleMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    //    esUtil.deleteArticle(aid);
                    redisUtil.delMember("article_status:" + lastStatus, aid);     // 从旧状态移除
                    redisUtil.delValue("article:" + aid);     // 删除旧的视频信息
                    //redisUtil.delValue("danmu_idset:" + aid);   // 删除该视频的弹幕
                    redisUtil.zsetDelMember("user_article_upload:" + article.getUid(), article.getVid());
                    // 搞个异步线程去删除OSS的源文件
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(articlePrefix), taskExecutor);
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(coverPrefix), taskExecutor);
                    // 批量删除该视频下的全部评论缓存
                    CompletableFuture.runAsync(() -> {
                        Set<Object> set = redisUtil.zReverange("comment_article:" + aid, 0, -1);
                        List<String> list = new ArrayList<>();
                        set.forEach(id -> list.add("comment_reply:" + id));
                        list.add("comment_article:" + aid);
                        redisUtil.delValues(list);
                    }, taskExecutor);
                    return customResponse;
                } else {
                    // 更新失败，处理错误情况
                    customResponse.setCode(500);
                    customResponse.setMessage("更新状态失败");
                    return customResponse;
                }
            } else {
                customResponse.setCode(403);
                customResponse.setMessage("您没有权限删除视频");
                return customResponse;
            }
        }
        customResponse.setCode(500);
        customResponse.setMessage("更新状态失败");
        return customResponse;
    }



    /*
    @Override
    public List<Map<String, Object>> getArticlesWithDataByIds(Set<Object> set, Integer index, Integer quantity) {
        if (index == null) {
            index = 1;
        }
        if (quantity == null) {
            quantity = 30;
        }
        int startIndex = (index - 1) * quantity;
        int endIndex = startIndex + quantity;
        // 检查数据是否足够满足分页查询
        if (startIndex > set.size()) {
            // 如果数据不足以填充当前分页，返回空列表
            return Collections.emptyList();
        }
        List<Article> articleList = new CopyOnWriteArrayList<>();   // 使用线程安全的集合类 CopyOnWriteArrayList 保证多线程处理共享List不会出现并发问题

        // 直接数据库分页查询    （平均耗时 13ms）
        List<Object> idList = new ArrayList<>(set);
        endIndex = Math.min(endIndex, idList.size());
        List<Object> sublist = idList.subList(startIndex, endIndex);
        QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("aid", sublist).ne("status", 3);
        articleList = articleMapper.selectList(queryWrapper);
        if (articleList.isEmpty()) return Collections.emptyList();

        // 并行处理每一个视频，提高效率
        // 先将videoList转换为Stream
        Stream<Article> articleStream = articleList.stream();
        List<Map<String, Object>> mapList = videoStream.parallel() // 利用parallel()并行处理
                .map(video -> {
//                    long start = System.currentTimeMillis();
//                    System.out.println("================ 开始查询 " + video.getVid() + " 号视频相关信息 ===============   当前时间 " + start);
                    Map<String, Object> map = new HashMap<>();
                    map.put("video", video);

                    CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                        map.put("user", userService.getUserById(video.getUid()));
                        map.put("stats", videoStatsService.getVideoStatsById(video.getVid()));
                    }, taskExecutor);

                    CompletableFuture<Void> categoryFuture = CompletableFuture.runAsync(() -> {
                        map.put("category", categoryService.getCategoryById(video.getMcId(), video.getScId()));
                    }, taskExecutor);

                    // 使用join()等待全部任务完成
                    userFuture.join();
                    categoryFuture.join();
//                    long end = System.currentTimeMillis();
//                    System.out.println("================ 结束查询 " + video.getVid() + " 号视频相关信息 ===============   当前时间 " + end + "   耗时 " + (end - start));

                    return map;
                })
                .collect(Collectors.toList());

//        end = System.currentTimeMillis();
//        System.out.println("封装耗时：" + (end - start));
        return mapList;
    }
*/
    @Override
    public Map<String, Object> getArticleById(Integer aid) {
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
        CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
            map.put("user", userService.getUserByUId(finalArticle.getUid()));
            //map.put("stats", StatsService.getVideoStatsById(finalVideo.getVid()));
        }, taskExecutor);
        map.put("article",article);
        // 使用join()等待userFuture和categoryFuture任务完成
        userFuture.join();
        return map;
    }



/*
    /**
     * 更新视频状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param vid   视频ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回，仅返回响应信息
     */
    /*
    @Override
    @Transactional
    public CustomResponse updateArticleStatus(Integer aid, Integer status) throws IOException {
        CustomResponse customResponse = new CustomResponse();
        Integer userId = currentUser.getUserId();
        if (status == 1 || status == 2) {
            if (!currentUser.isAdmin()) {
                customResponse.setCode(403);
                customResponse.setMessage("您不是管理员，无权访问");
                return customResponse;
            }
            if (status == 1) {
                QueryWrapper<Article> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("aid", aid).ne("status", 3);
                Article article   = articleMapper.selectOne(queryWrapper);
                if (article == null) {
                    customResponse.setCode(404);
                    customResponse.setMessage("文章不见了");
                    return customResponse;
                }
                Integer lastStatus = article.getStatus();
                article.setStatus(1);
                UpdateWrapper<Article> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("aid", aid).set("status", 1);     // 更新视频状态审核通过
                int flag = articleMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    esUtil.updateVideo(video);  // 更新ES视频文档
                    redisUtil.delMember("video_status:" + lastStatus, vid);     // 从旧状态移除
                    redisUtil.addMember("video_status:1", vid);     // 加入新状态
                    redisUtil.zset("user_video_upload:" + video.getUid(), video.getVid());
                    redisUtil.delValue("video:" + vid);     // 删除旧的视频信息
                    return customResponse;
                } else {
                    // 更新失败，处理错误情况
                    customResponse.setCode(500);
                    customResponse.setMessage("更新状态失败");
                    return customResponse;
                }
            }
            else {
                // 目前逻辑跟上面一样的，但是可能以后要做一些如 记录不通过原因 等操作，所以就分开写了
                QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("vid", vid).ne("status", 3);
                Video video = videoMapper.selectOne(queryWrapper);
                if (video == null) {
                    customResponse.setCode(404);
                    customResponse.setMessage("视频不见了QAQ");
                    return customResponse;
                }
                Integer lastStatus = video.getStatus();
                video.setStatus(2);
                UpdateWrapper<Video> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("vid", vid).set("status", 2);     // 更新视频状态审核不通过
                int flag = videoMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    esUtil.updateVideo(video);  // 更新ES视频文档
                    redisUtil.delMember("video_status:" + lastStatus, vid);     // 从旧状态移除
                    redisUtil.addMember("video_status:2", vid);     // 加入新状态
                    redisUtil.zsetDelMember("user_video_upload:" + video.getUid(), video.getVid());
                    redisUtil.delValue("video:" + vid);     // 删除旧的视频信息
                    return customResponse;
                } else {
                    // 更新失败，处理错误情况
                    customResponse.setCode(500);
                    customResponse.setMessage("更新状态失败");
                    return customResponse;
                }
            }
        } else if (status == 3) {
            QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("vid", vid).ne("status", 3);
            Video video = videoMapper.selectOne(queryWrapper);
            if (video == null) {
                customResponse.setCode(404);
                customResponse.setMessage("视频不见了QAQ");
                return customResponse;
            }
            if (Objects.equals(userId, video.getUid()) || currentUser.isAdmin()) {
                String videoUrl = video.getVideoUrl();
                String videoPrefix = videoUrl.split("aliyuncs.com/")[1];  // OSS视频文件名
                String coverUrl = video.getCoverUrl();
                String coverPrefix = coverUrl.split("aliyuncs.com/")[1];  // OSS封面文件名
                Integer lastStatus = video.getStatus();
                UpdateWrapper<Video> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("vid", vid).set("status", 3).set("delete_date", new Date());     // 更新视频状态已删除
                int flag = videoMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    esUtil.deleteVideo(vid);
                    redisUtil.delMember("video_status:" + lastStatus, vid);     // 从旧状态移除
                    redisUtil.delValue("video:" + vid);     // 删除旧的视频信息
                    redisUtil.delValue("danmu_idset:" + vid);   // 删除该视频的弹幕
                    redisUtil.zsetDelMember("user_video_upload:" + video.getUid(), video.getVid());
                    // 搞个异步线程去删除OSS的源文件
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(videoPrefix), taskExecutor);
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(coverPrefix), taskExecutor);
                    // 批量删除该视频下的全部评论缓存
                    CompletableFuture.runAsync(() -> {
                        Set<Object> set = redisUtil.zReverange("comment_video:" + vid, 0, -1);
                        List<String> list = new ArrayList<>();
                        set.forEach(id -> list.add("comment_reply:" + id));
                        list.add("comment_video:" + vid);
                        redisUtil.delValues(list);
                    }, taskExecutor);
                    return customResponse;
                } else {
                    // 更新失败，处理错误情况
                    customResponse.setCode(500);
                    customResponse.setMessage("更新状态失败");
                    return customResponse;
                }
            } else {
                customResponse.setCode(403);
                customResponse.setMessage("您没有权限删除视频");
                return customResponse;
            }
        }
        customResponse.setCode(500);
        customResponse.setMessage("更新状态失败");
        return customResponse;
    }*/

}
