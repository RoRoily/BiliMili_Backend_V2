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
public class ChatDetailed {
    @TableId(type = IdType.AUTO)
    private Integer id;         // 消息id
    private Integer postId;     // 发送者uid
    private Integer acceptId;  // 接受者uid
    private String content;     // 消息内容
    private Integer postDel;    // 发送者是否删除
    private Integer acceptDel; // 接受者者是否删除
    private Integer withdraw;   // 消息是否被撤回
    private Date time;          // 发送消息的时间
}