package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @TableId(type = IdType.AUTO)
    private Integer uid;
    private String account;
    private String password;
    private String nickname;
    private String headPortrait;
    private String background;
    private Integer gender; // 性别，0 女性 1 男性 2无性别，默认2
    private String description;//个人介绍
    private Integer experience;    // 经验值 50/200/1500/4500/10800/28800 分别是0~6级的区间
    private Double coin;    // 硬币数 保留一位小数
    private Integer state;  // 0 正常，1 封禁中，2 已注销
    private Integer role;   // 0 普通用户，1 普通管理员，2 超级管理员
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    private Date createDate;
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    private Date deleteDate;
}
