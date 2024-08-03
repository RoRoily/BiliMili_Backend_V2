package com.bilimili.buaa13.service.video;

import com.bilimili.buaa13.entity.VideoStats;

public interface VideoStatsService {
    /**
     * 根据视频ID查询视频常变数据
     * @param vid 视频ID
     * @return 视频数据统计
     */
    VideoStats getStatsByVideoId(Integer vid);

    /**
     * 更新指定字段
     * @param vid   视频ID
     * @param column    对应数据库的列名
     * @param increase  是否增加，true则增加 false则减少
     * @param count 增减数量 一般是1，只有投币可以加2
     */
    void updateVideoStats(Integer vid, String column, boolean increase, Integer count);

    /**
     * 同时更新点赞和点踩
     * @param vid   视频ID
     * @param addGood   是否点赞，true则good+1&down_vote-1，false则good-1&down_vote+1
     */
    void updateGoodAndBad(Integer vid, boolean addGood);
}
