package com.bilimili.buaa13.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.mapper.FavoriteVideoMapper;
import com.bilimili.buaa13.entity.FavoriteVideo;
import com.bilimili.buaa13.entity.Video;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.service.video.VideoService;
import com.bilimili.buaa13.utils.RedisUtil;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class VideoController {
    @Autowired
    private VideoService videoService;

    @Autowired
    private FavoriteVideoMapper favoriteVideoMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    /**
     * 更新视频状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param vid 视频ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回 仅返回响应
     */
    @PostMapping("/video/change/status")
    public ResponseResult updateStatus(@RequestParam("vid") Integer vid,
                                       @RequestParam("status") Integer status) {
        try {
            return videoService.updateVideoStatus(vid, status);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult(500, "操作失败", null);
        }
    }

    /**
     * 游客访问时的feed流随机推荐
     * @return  返回11条随机推荐视频
     */
    @GetMapping("/video/random/visitor")
    public ResponseResult randomVideosForVisitor() {
        ResponseResult responseResult = new ResponseResult();
        int count = 11;
        Set<Object> idSet = redisUtil.srandmember("video_status:1", count);
        List<Map<String, Object>> videoList = new ArrayList<>();
        if (idSet != null && !idSet.isEmpty()) {
            videoList = videoService.getVideosWithDataByIds(idSet, 1, count);
            // 随机打乱列表顺序
            Collections.shuffle(videoList);
        }

        System.out.println("videoList Size :" + videoList.size());
        responseResult.setData(videoList);
        return responseResult;
    }

    /**
     * 累加获取更多视频
     * @param vids  曾经查询过的视频id列表，用于去重
     * @return  每次返回新的10条视频，以及其id列表，并标注是否还有更多视频可以获取
     */
    @GetMapping("/video/cumulative/visitor")
    public ResponseResult cumulativeVideosForVisitor(@RequestParam("vids") String vids) {
        System.out.println("这个函数被调用了,vids的值 : " + vids);
        ResponseResult responseResult = new ResponseResult();
        Map<String, Object> map = new HashMap<>();
        List<Integer> vidsList = new ArrayList<>();
        if (vids.trim().length() > 0) {
            vidsList = Arrays.stream(vids.split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());  // 从字符串切分出id列表
        }
        Set<Object> set = redisUtil.getMembers("video_status:1");
        if (set == null) {
            map.put("videos", new ArrayList<>());
            map.put("vids", new ArrayList<>());
            map.put("more", false);
            responseResult.setData(map);
            return responseResult;
        }
        vidsList.forEach(set::remove);  // 去除已获取的元素
        Set<Object> idSet = new HashSet<>();    // 存放将要返回的id集合
        Random random = new Random();
        // 随机获取10个vid
        for (int i = 0; i < 10 && !set.isEmpty(); i++) {
            Object[] arr = set.toArray();
            int randomIndex = random.nextInt(set.size());
            idSet.add(arr[randomIndex]);
            set.remove(arr[randomIndex]);   // 查过的元素移除
        }
        List<Map<String, Object>> videoList = new ArrayList<>();
        if (!idSet.isEmpty()) {
            videoList = videoService.getVideosWithDataByIds(idSet, 1, 10);
            Collections.shuffle(videoList);     // 随机打乱列表顺序
        }
        System.out.println("videoList again:" + videoList.size());
        map.put("videos", videoList);
        map.put("vids", idSet);
        System.out.println("vids + : " + idSet);
        if (!set.isEmpty()) {
            map.put("more", true);
        } else {
            map.put("more", false);
        }
        responseResult.setData(map);
        return responseResult;
    }

    /**
     * 获取单条视频的信息
     * @param vid   视频vid
     * @return  视频信息
     */
    @GetMapping("/video/getone")
    public ResponseResult getOneVideo(@RequestParam("vid") Integer vid) {
        System.out.println(vid);
        ResponseResult responseResult = new ResponseResult();
        Map<String, Object> map = videoService.getVideoWithDataById(vid);
        if (map == null) {
            System.out.println("map == null");
            responseResult.setCode(404);
            responseResult.setMessage("ERROR");
            return responseResult;
        }
        Video video = (Video) map.get("video");
        if (video.getStatus() != 1) {
            System.out.println("video.getStatus()!=1  " + video.getStatus() + vid);
            responseResult.setCode(404);
            responseResult.setMessage("ERROR");
            return responseResult;
        }
        responseResult.setData(map);
        return responseResult;
    }

    @GetMapping("/video/user-works-count")
    public ResponseResult getUserWorksCount(@RequestParam("uid") Integer uid) {
        return new ResponseResult(200, "OK", redisUtil.zCard("user_video_upload:" + uid));
    }

    /**
     * 获取用户视频投稿
     * @param uid   用户id
     * @param rule  排序方式 1 投稿日期 2 播放量 3 点赞数
     * @param page  分页 从1开始
     * @param quantity  每页查询数量
     * @return  视频信息列表
     */
    @GetMapping("/video/user-works")
    public ResponseResult getUserWorks(@RequestParam("uid") Integer uid,
                                       @RequestParam("rule") Integer rule,
                                       @RequestParam("page") Integer page,
                                       @RequestParam("quantity") Integer quantity) {
        ResponseResult responseResult = new ResponseResult();
        Map<String, Object> map = new HashMap<>();
        Set<Object> set = redisUtil.zReverange("user_video_upload:" + uid, 0, -1);
        if (set == null || set.isEmpty()) {
            map.put("count", 0);
            map.put("list", Collections.emptyList());
            responseResult.setData(map);
            return responseResult;
        }
        List<Integer> list = new ArrayList<>();
        set.forEach(vid -> {
            list.add((Integer) vid);
        });
        map.put("count", set.size());
        switch (rule) {
            case 1:
                map.put("list", videoService.getVideosWithDataByIdsOrderByDesc(list, "upload_date", page, quantity));
                break;
            case 2:
                map.put("list", videoService.getVideosWithDataByIdsOrderByDesc(list, "play", page, quantity));
                break;
            case 3:
                map.put("list", videoService.getVideosWithDataByIdsOrderByDesc(list, "good", page, quantity));
                break;
            default:
                map.put("list", videoService.getVideosWithDataByIdsOrderByDesc(list, "upload_date", page, quantity));
        }
        responseResult.setData(map);
        return responseResult;
    }

    /**
     * 获取用户最近点赞视频列表
     * @param uid   用户uid
     * @param offset    偏移量，即当前已查询到多少条视频
     * @param quantity  查询数量
     * @return  视频信息列表
     */
    @GetMapping("/video/user-love")
    public ResponseResult getUserLoveMovies(@RequestParam("uid") Integer uid,
                                            @RequestParam("offset") Integer offset,
                                            @RequestParam("quantity") Integer quantity) {
        ResponseResult responseResult = new ResponseResult();
        Set<Object> set = redisUtil.zReverange("love_video:" + uid, (long) offset, (long) offset + quantity - 1);
        if (set == null || set.isEmpty()) {
            responseResult.setData(Collections.emptyList());
            return responseResult;
        }
        List<Integer> list = new ArrayList<>();
        set.forEach(vid -> {
            list.add((Integer) vid);
        });
        responseResult.setData(videoService.getVideosWithDataByIdsOrderByDesc(list, null, 1, list.size()));
        return responseResult;
    }

    /**
     * 获取当前登录用户最近播放视频列表
     * @param offset    偏移量，即当前已查询到多少条视频
     * @param quantity  查询数量
     * @return  视频信息列表
     */
    @GetMapping("/video/user-play")
    public ResponseResult getUserPlayMovies(@RequestParam("offset") Integer offset,
                                            @RequestParam("quantity") Integer quantity) {
        Integer uid = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        Set<Object> set = redisUtil.zReverange("user_video_history:" + uid, (long) offset, (long) offset + quantity - 1);
        if (set == null || set.isEmpty()) {
            responseResult.setData(Collections.emptyList());
            return responseResult;
        }
        List<Integer> list = new ArrayList<>();
        set.forEach(vid -> {
            list.add((Integer) vid);
        });
        responseResult.setData(videoService.getVideosWithDataByIdsOrderByDesc(list, null, 1, list.size()));
        return responseResult;
    }

    /**
     * 获取某个收藏夹的视频
     * @param fid   收藏夹ID
     * @param rule  排序规则 1 最近收藏 2 最多播放 3 最新投稿
     * @param page  分页  从1开始
     * @param quantity  每页查询数量
     * @return  视频信息列表
     */
    @GetMapping("/video/user-collect")
    public ResponseResult getUserCollectVideos(@RequestParam("fid") Integer fid,
                                               @RequestParam("rule") Integer rule,
                                               @RequestParam("page") Integer page,
                                               @RequestParam("quantity") Integer quantity) {
        ResponseResult responseResult = new ResponseResult();
        Set<Object> set;
        if (rule == 1) {
            set = redisUtil.zReverange("favorite_video:" + fid, (long) (page - 1) * quantity, (long) page * quantity);
        } else {
            set = redisUtil.zReverange("favorite_video:" + fid, 0, -1);
        }
        if (set == null || set.isEmpty()) {
            responseResult.setData(Collections.emptyList());
            return responseResult;
        }
        List<Integer> list = new ArrayList<>();
        set.forEach(vid -> {
            list.add((Integer) vid);
        });
        List<Map<String, Object>> result;
        switch (rule) {
            case 1:
                result = videoService.getVideosWithDataByIdsOrderByDesc(list, null, page, quantity);
                break;
            case 2:
                result = videoService.getVideosWithDataByIdsOrderByDesc(list, "play", page, quantity);
                break;
            case 3:
                result = videoService.getVideosWithDataByIdsOrderByDesc(list, "upload_date", page, quantity);
                break;
            default:
                result = videoService.getVideosWithDataByIdsOrderByDesc(list, null, page, quantity);
        }
        if (result.size() == 0) {
            responseResult.setData(result);
            return responseResult;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
            result.stream().parallel().forEach(map -> {
                Video video = (Video) map.get("video");
                QueryWrapper<FavoriteVideo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("vid", video.getVid()).eq("fid", fid);
                map.put("info", favoriteVideoMapper.selectOne(queryWrapper));
            });
            sqlSession.commit();
        }
        responseResult.setData(result);
        return responseResult;
    }
}
