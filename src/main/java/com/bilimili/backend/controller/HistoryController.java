package com.bilimili.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.backend.mapper.FavoriteVideoMapper;
import com.bilimili.backend.mapper.VideoMapper;
import com.bilimili.backend.mapper.VideoStatsMapper;
import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.pojo.FavoriteVideo;
import com.bilimili.backend.pojo.Video;
import com.bilimili.backend.pojo.VideoStats;
import com.bilimili.backend.service.utils.CurrentUser;
import com.bilimili.backend.service.video.FavoriteService;
import com.bilimili.backend.service.video.FavoriteVideoService;
import com.bilimili.backend.service.video.UserVideoService;
import com.bilimili.backend.service.video.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class HistoryController {
    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteVideoService favoriteVideoService;

    @Autowired
    private UserVideoService userVideoService;
    @Autowired
    private FavoriteVideoMapper favoriteVideoMapper;
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private VideoStatsMapper videoStatsMapper;

    /**
     * 获取历史记录
     * @param uid   用户uid
     */
    @GetMapping("/Record/Video")
    public CustomResponse getRecordVideoByUid(@RequestParam("uid") Integer uid) {
        CustomResponse customResponse = new CustomResponse();
        int fid = 5000+uid;
        List<Integer> vids = favoriteVideoMapper.getVidByFid(fid);
        List<Date> times = favoriteVideoMapper.getTimeByFid(fid);
        List<String> titles = new ArrayList<>();
        List<Double> durations = new ArrayList<>();
        List<Integer> playCounts = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for(Integer vid: vids){
            QueryWrapper<Video> videoQueryWrapper = new QueryWrapper<>();
            videoQueryWrapper.eq("vid", vid);
            QueryWrapper<VideoStats> videoStatsQueryWrapper = new QueryWrapper<>();
            videoStatsQueryWrapper.eq("vid", vid);
            Video video = videoMapper.selectOne(videoQueryWrapper);
            VideoStats videoStats = videoStatsMapper.selectOne(videoStatsQueryWrapper);
            titles.add(video.getTitle());
            durations.add(video.getDuration());
            urls.add(video.getCoverUrl());
            playCounts.add(videoStats.getPlay());
        }
        Map<String,Object> dataMap = new HashMap<>();
        dataMap.put("vid",vids);
        dataMap.put("title",titles);
        dataMap.put("duration",durations);
        dataMap.put("url",urls);
        dataMap.put("view",playCounts);
        dataMap.put("time",times);
        customResponse.setData(dataMap);
        return customResponse;
    }
}