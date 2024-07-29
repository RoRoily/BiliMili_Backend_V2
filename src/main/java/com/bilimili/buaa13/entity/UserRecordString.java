package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRecordString {//近七天用户数据统计量
    @TableId
    private Integer uid;//用户uid
    private String playJson;//用户近七天播放量，为了方便存储数据库，采用JSON字符串格式
    private Integer playNew;//今天的新播放量
    private Integer playOld;//昨天老播放量
    private String loveJson;//近七天点赞数
    private Integer loveNew;//今天新点赞量
    private Integer loveOld;//昨天旧点赞量
    private String collectJson;//近七天关注数
    private Integer collectNew;//今天新收藏量
    private Integer collectOld;//昨天旧收藏量
    private String fanJson;//近七天粉丝数
    private Integer fansNew;//今天新关注量
    private Integer fansOld;//昨天旧关注量
}
