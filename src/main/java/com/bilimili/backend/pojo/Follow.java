package com.bilimili.backend.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Follow {
    @TableId(type = IdType.AUTO)
    //private Integer uid;//我的uid
    private Integer uidFollow;//关注的人
    private Integer uidFans;//粉丝
    private Integer visible;//其他人是否可见
}

