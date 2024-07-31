package com.bilimili.buaa13.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Integer uid;
    private String nickname;
    private String headPortrait_url;
    private String background_url;
    private Integer gender; // 性别，0 女性 1 男性 2无性别，默认2
    private String description;
    private Integer experience;    // 经验值 50/200/1500/4500/10800/28800 分别是0~6级的区间
    private Double coin;    // 硬币数  保留一位小数
    private Integer state;  // 0 正常，1 封禁中
    private Integer videoCount; // 视频投稿数
    private Integer followsCount;   // 关注数
    private Integer fansCount;  // 粉丝数
    private Integer loveCount;  // 获赞数
    private Integer playCount;  // 播放数
}
