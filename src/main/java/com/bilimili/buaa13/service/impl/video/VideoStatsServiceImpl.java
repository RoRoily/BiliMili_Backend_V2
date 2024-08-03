package com.bilimili.buaa13.service.impl.video;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.entity.VideoStats;
import com.bilimili.buaa13.mapper.VideoStatsMapper;
import com.bilimili.buaa13.service.video.VideoStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VideoStatsServiceImpl implements VideoStatsService {
    @Autowired
    private VideoStatsMapper videoStatsMapper;

    /**
     * 根据视频ID查询视频常变数据
     * @param vid 视频ID
     * @return 视频数据统计
     */
    @Override
    public VideoStats getStatsByVideoId(Integer vid) {
        //注释Redis
        /*VideoStats videoStats = redisUtil.getObject("videoStats:" + vid, VideoStats.class);
        if (videoStats == null) {
            videoStats = videoStatsMapper.selectById(vid);
            if (videoStats != null) {
                VideoStats finalVideoStats = videoStats;
                CompletableFuture.runAsync(() -> {
                    redisUtil.setExObjectValue("videoStats:" + vid, finalVideoStats);    // 异步更新到redis
                }, taskExecutor);
            } else {
                return null;
            }
        }*/
        return videoStatsMapper.selectById(vid);
    }

    /**
     * 更新指定字段
     * @param vid   视频ID
     * @param column    对应数据库的列名
     * @param increase  是否增加，true则增加 false则减少
     * @param count 增减数量 一般是1，只有投币可以加2
     */
    @Override
    public void updateVideoStats(Integer vid, String column, boolean increase, Integer count) {
        UpdateWrapper<VideoStats> updateWrapper = new UpdateWrapper<>();
        if (increase) {
            updateWrapper.eq("vid", vid);
            updateWrapper.setSql(column + " = " + column + " + " + count);
        } else {
            // 更新后的字段不能小于0
            updateWrapper.eq("vid", vid);
            updateWrapper.setSql(column + " = CASE WHEN " + column + " - " + count + " < 0 THEN 0 ELSE " + column + " - " + count + " END");
        }
        videoStatsMapper.update(null, updateWrapper);
        //注释Redis
        //redisUtil.delValue("videoStats:" + vid);
    }

    /**
     * 同时更新点赞和点踩
     * @param vid   视频ID
     * @param addGood   是否点赞，true则good+1&down_vote-1，false则good-1&down_vote+1
     */
    @Override
    public void updateGoodAndBad(Integer vid, boolean addGood) {
        UpdateWrapper<VideoStats> updateWrapper = new UpdateWrapper<>();
        if (addGood) {
            updateWrapper.eq("vid", vid);
            updateWrapper.setSql("good = good + 1");
            updateWrapper.setSql("down_vote = CASE WHEN down_vote - 1 < 0 THEN 0 ELSE down_vote - 1 END");
        } else {
            updateWrapper.eq("vid", vid);
            updateWrapper.setSql("down_vote = down_vote + 1");
            updateWrapper.setSql("good = CASE WHEN good - 1 < 0 THEN 0 ELSE good - 1 END");
        }
        videoStatsMapper.update(null, updateWrapper);
        //注释Redis
        //redisUtil.delValue("videoStats:" + vid);
    }
}
