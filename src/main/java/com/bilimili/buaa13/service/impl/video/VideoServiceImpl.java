package com.bilimili.buaa13.service.impl.video;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.mapper.VideoStatsMapper;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.Video;
import com.bilimili.buaa13.entity.VideoStats;
import com.bilimili.buaa13.service.category.CategoryService;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.service.video.VideoService;
import com.bilimili.buaa13.service.video.VideoStatsService;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.OssUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class VideoServiceImpl implements VideoService {
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

    /**
     * 根据id分页获取视频信息，包括用户和分区信息
     * @param videoList   要查询的视频数组
     * @param index 分页页码 为空默认是1
     * @param quantity  每一页查询的数量 为空默认是10
     * @return  包含用户信息、分区信息、视频信息的map列表
     */
    @Override
    public List<Map<String, Object>> getVideosPageWithDataByVideoList(List<Video> videoList, Integer index, Integer quantity) {
        if (index == null) {
            index = 1;
        }
        if (quantity == null) {
            quantity = 10;
        }
        int startIndex = (index - 1) * quantity;
        int endIndex = startIndex + quantity;
        // 检查数据是否足够满足分页查询
        if (startIndex > videoList.size()) {
            // 如果数据不足以填充当前分页，返回空列表
            return Collections.emptyList();
        }

        // 直接数据库分页查询    （平均耗时 13ms）
        endIndex = Math.min(endIndex, videoList.size());
        List<Video> sublist = videoList.subList(startIndex, endIndex);
        sublist.removeIf(video -> video.getStatus() == 3);
        videoList = sublist;
        if (videoList.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> mapList = new ArrayList<>();
        for(Video video : videoList) {
            Map<String, Object> map = new HashMap<>();
            map.put("video",video);
            // 获取 user 和 stats 信息
            map.put("user", userService.getUserByUId(video.getUid()));
            map.put("stats", videoStatsService.getStatsByVideoId(video.getVid()));
            // 获取 category 信息
            map.put("category", categoryService.getCategoryById(video.getMainClassId(), video.getSubClassId()));

            mapList.add(map);
        }
        return mapList;
    }

    @Override
    public List<Map<String, Object>> getVideosWithDataByIdsOrderByDesc(List<Integer> idList, @Nullable String column, Integer page, Integer quantity) {
        // 使用事务批量操作 减少连接sql的开销
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            List<Map<String, Object>> result;
            if (column == null) {
                // 如果没有指定排序列，就按idList排序
                QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
                queryWrapper.in("vid", idList);
                List<Video> videos = videoMapper.selectList(queryWrapper);
                if (videos.isEmpty()) {
                    sqlSession.commit();
                    return Collections.emptyList();
                }
                result = idList.stream().parallel().flatMap(vid -> {
                    Map<String, Object> map = new HashMap<>();
                    // 找到videos中为vid的视频
                    Video video = videos.stream()
                            .filter(v -> Objects.equals(v.getVid(), vid))
                            .findFirst()
                            .orElse(null);
                    if (video == null) return Stream.empty(); // 跳过该项
                    if (video.getStatus() == 3) {
                        // 视频已删除
                        Video video1 = new Video();
                        video1.setVid(video.getVid());
                        video1.setUid(video.getUid());
                        video1.setStatus(video.getStatus());
                        video1.setDeleteDate(video.getDeleteDate());
                        map.put("video", video1);
                        return Stream.of(map);
                    }
                    map.put("video", video);
                    CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                        map.put("user", userService.getUserByUId(video.getUid()));
                        map.put("stats", videoStatsService.getStatsByVideoId(video.getVid()));
                    }, taskExecutor);
                    CompletableFuture<Void> categoryFuture = CompletableFuture.runAsync(() -> {
                        map.put("category", categoryService.getCategoryById(video.getMainClassId(), video.getSubClassId()));
                    }, taskExecutor);
                    userFuture.join();
                    categoryFuture.join();
                    return Stream.of(map);
                }).collect(Collectors.toList());
            } else if (Objects.equals(column, "upload_date")) {
                // 如果按投稿日期排序，就先查video表
                QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
                queryWrapper.in("vid", idList).orderByDesc(column).last("LIMIT " + quantity + " OFFSET " + (page - 1) * quantity);
                List<Video> list = videoMapper.selectList(queryWrapper);
                if (list.isEmpty()) {
                    sqlSession.commit();
                    return Collections.emptyList();
                }
                result = list.stream().parallel().map(video -> {
                    Map<String, Object> map = new HashMap<>();
                    if (video.getStatus() == 3) {
                        // 视频已删除
                        Video video1 = new Video();
                        video1.setVid(video.getVid());
                        video1.setUid(video.getUid());
                        video1.setStatus(video.getStatus());
                        video1.setDeleteDate(video.getDeleteDate());
                        map.put("video", video1);
                        return map;
                    }
                    map.put("video", video);
                    CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                        map.put("user", userService.getUserByUId(video.getUid()));
                        map.put("stats", videoStatsService.getStatsByVideoId(video.getVid()));
                    }, taskExecutor);
                    CompletableFuture<Void> categoryFuture = CompletableFuture.runAsync(() -> {
                        map.put("category", categoryService.getCategoryById(video.getMainClassId(), video.getSubClassId()));
                    }, taskExecutor);
                    userFuture.join();
                    categoryFuture.join();
                    return map;
                }).collect(Collectors.toList());
            } else {
                // 否则按视频数据排序，就先查数据
                QueryWrapper<VideoStats> queryWrapper = new QueryWrapper<>();
                queryWrapper.in("vid", idList).orderByDesc(column).last("LIMIT " + quantity + " OFFSET " + (page - 1) * quantity);
                List<VideoStats> list = videoStatsMapper.selectList(queryWrapper);
                if (list.isEmpty()) {
                    sqlSession.commit();
                    return Collections.emptyList();
                }
                result = list.stream().parallel().map(videoStats -> {
                    Map<String, Object> map = new HashMap<>();
                    Video video = videoMapper.selectById(videoStats.getVid());
                    if (video.getStatus() == 3) {
                        // 视频已删除
                        Video video1 = new Video();
                        video1.setVid(video.getVid());
                        video1.setUid(video.getUid());
                        video1.setStatus(video.getStatus());
                        video1.setDeleteDate(video.getDeleteDate());
                        map.put("video", video1);
                        return map;
                    }
                    map.put("video", video);
                    map.put("stats", videoStats);
                    CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                        map.put("user", userService.getUserByUId(video.getUid()));
                    }, taskExecutor);
                    CompletableFuture<Void> categoryFuture = CompletableFuture.runAsync(() -> {
                        map.put("category", categoryService.getCategoryById(video.getMainClassId(), video.getSubClassId()));
                    }, taskExecutor);
                    userFuture.join();
                    categoryFuture.join();
                    return map;
                }).collect(Collectors.toList());
            }
            sqlSession.commit();
            return result;
        }
    }

    /**
     * 根据vid查询单个视频信息，包含用户信息和分区信息
     * @param vid 视频ID
     * @return 包含用户信息、分区信息、视频信息的map
     */
    @Override
    public Map<String, Object> getVideoWithDataByVideoId(Integer vid) {
        Map<String, Object> map = new HashMap<>();
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("vid", vid).ne("status", 3);
        Video video = videoMapper.selectOne(queryWrapper);
        //注释Redis
        /*if (video != null) {
            Video finalVideo1 = video;
            CompletableFuture.runAsync(() -> {
                redisUtil.setExObjectValue("video:" + vid, finalVideo1);    // 异步更新到redis
            }, taskExecutor);
        } else  {
            return null;
        }*/
        map.put("video", video);
        map.put("user", userService.getUserByUId(video.getUid()));
        map.put("stats", videoStatsService.getStatsByVideoId(video.getVid()));
        map.put("category", categoryService.getCategoryById(video.getMainClassId(), video.getSubClassId()));
        return map;
    }

    /**
     * 根据有序vid列表查询视频以及相关信息
     * @param list  vid有序列表
     * @return  有序的视频列表
     */
    @Override
    public List<Map<String, Object>> getVideosWithDataByVideoIdList(List<Integer> list) {
        if (list.isEmpty()) return Collections.emptyList();
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("vid", list).ne("status", 3);
        List<Video> videos = videoMapper.selectList(queryWrapper);
        if (videos.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> mapList = new ArrayList<>();
        for(Integer vid:list){
            Map<String, Object> map = new HashMap<>();
            Video video = null;
            for(Video video0 :videos){
                if(video0.getVid().equals(vid)){
                    video = video0;
                    break;
                }
            }
            if(video==null){continue;}
            try{
                map.put("video", video);
                map.put("user", userService.getUserByUId(video.getUid()));
                map.put("stats", videoStatsService.getStatsByVideoId(video.getVid()));
                map.put("category", categoryService.getCategoryById(video.getMainClassId(), video.getSubClassId()));
            }
            catch (Exception e){
                e.printStackTrace();
                continue;
            }
            mapList.add(map);
        }
        return mapList;
    }

    /**
     * 更新视频状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param vid   视频ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回，仅返回响应信息
     */
    @Override
    @Transactional
    public ResponseResult changeVideoStatus(Integer vid, Integer status) throws IOException {
        ResponseResult responseResult = new ResponseResult();
        Integer userId = currentUser.getUserId();
        if (status == 1 || status == 2) {
            if (!currentUser.isAdmin()) {
                responseResult.setCode(403);
                responseResult.setMessage("您不是管理员，无权访问");
                return responseResult;
            }
            QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("vid", vid).ne("status", 3);
            Video video = videoMapper.selectOne(queryWrapper);
            if (video == null) {
                responseResult.setCode(404);
                responseResult.setMessage("视频不见了QAQ");
                return responseResult;
            }
            //注释Redis
            //Integer lastStatus = video.getStatus();
            video.setStatus(1);
            UpdateWrapper<Video> updateWrapper = new UpdateWrapper<>();
            // 更新视频状态审核
            updateWrapper.eq("vid", vid).set("status", 1).set("upload_date", new Date());
            int flag = videoMapper.update(null, updateWrapper);
            if (flag > 0) {
                // 更新成功
                esUtil.updateVideo(video);  // 更新ES视频文档
                //注释Redis
                /*redisUtil.delMember("video_status:" + lastStatus, vid);     // 从旧状态移除
                redisUtil.addMember("video_status:1", vid);     // 加入新状态
                redisUtil.zset("user_video_upload:" + video.getUid(), video.getVid());
                redisUtil.delValue("video:" + vid);     // 删除旧的视频信息*/
                if(status==2){
                    //添加不通过的原因
                    responseResult.setMessage("审核不通过");
                }
                else responseResult.setMessage("审核通过");
                return responseResult;
            } else {
                // 更新失败，处理错误情况
                responseResult.setCode(500);
                responseResult.setMessage("更新状态失败");
                return responseResult;
            }

        } else if (status == 3) {
            QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("vid", vid).ne("status", 3);
            Video video = videoMapper.selectOne(queryWrapper);
            if (video == null) {
                responseResult.setCode(404);
                responseResult.setMessage("视频不见了QAQ");
                return responseResult;
            }
            if (video.getUid().equals(userId) || currentUser.isAdmin()) {
                String videoUrl = video.getVideoUrl();
                String videoName = videoUrl.split("aliyuncs.com/")[1];  // OSS视频文件名
                String coverUrl = video.getCoverUrl();
                String coverName = coverUrl.split("aliyuncs.com/")[1];  // OSS封面文件名
                Integer lastStatus = video.getStatus();
                UpdateWrapper<Video> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("vid", vid).set("status", 3).set("delete_date", new Date());     // 更新视频状态已删除
                int flag = videoMapper.update(null, updateWrapper);
                if (flag > 0) {
                    // 更新成功
                    esUtil.deleteVideo(vid);
                    //注释redis
                    /*redisUtil.delMember("video_status:" + lastStatus, vid);     // 从旧状态移除
                    redisUtil.delValue("video:" + vid);     // 删除旧的视频信息
                    redisUtil.delValue("danmu_idset:" + vid);   // 删除该视频的弹幕
                    redisUtil.zsetDelMember("user_video_upload:" + video.getUid(), video.getVid());*/
                    // 搞个异步线程去删除OSS的源文件
                    //注释异步线程
                    /*CompletableFuture.runAsync(() -> ossUtil.deleteFiles(videoName), taskExecutor);
                    CompletableFuture.runAsync(() -> ossUtil.deleteFiles(coverName), taskExecutor);*/
                    ossUtil.deleteFiles(videoName);
                    ossUtil.deleteFiles(coverName);
                    // 批量删除该视频下的全部评论缓存
                    //注释Redis
                    /*CompletableFuture.runAsync(() -> {
                        Set<Object> set = redisUtil.zReverange("comment_video:" + vid, 0, -1);
                        List<String> list = new ArrayList<>();
                        set.forEach(id -> list.add("comment_reply:" + id));
                        list.add("comment_video:" + vid);
                        redisUtil.delValues(list);
                    }, taskExecutor);*/
                    return responseResult;
                } else {
                    // 更新失败，处理错误情况
                    responseResult.setMessage("更新状态失败");
                    responseResult.setCode(500);
                    return responseResult;
                }
            } else {
                responseResult.setMessage("您没有权限删除视频");
                responseResult.setCode(403);
                return responseResult;
            }
        }
        responseResult.setCode(500);
        responseResult.setMessage("更新状态失败");
        return responseResult;
    }
}
