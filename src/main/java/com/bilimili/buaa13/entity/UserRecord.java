package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRecord {
    @TableId
    private Integer uid;
    private List<Integer> play;
    private Integer playNew;//今天的新播放量
    private Integer playOld;//昨天老播放量
    private List<Integer> love;
    private Integer loveNew;//今天新点赞量
    private Integer loveOld;//昨天旧点赞量
    private List<Integer> collect;
    private Integer collectNew;//今天新收藏量
    private Integer collectOld;//昨天旧收藏量
    private List<Integer> fans;
    private Integer fansNew;//今天新关注量
    private Integer fansOld;//昨天旧关注量
}
