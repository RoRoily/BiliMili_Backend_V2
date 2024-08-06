package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HotSearch {
    @TableId(type = IdType.AUTO)
    private Integer hid;
    private String content; // 内容
    private Double hot;   // 热度
    private Integer type = 0;   // 类型： 0 普通 1 新 2 热
}
