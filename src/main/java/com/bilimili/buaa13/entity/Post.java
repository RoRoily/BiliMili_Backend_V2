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
public class Post {
    @TableId(type = IdType.AUTO)
    private Integer pid;
    private Integer uid;
    private String content;
    private Integer love;
    private Integer unlove;
    private Date createTime;
}
