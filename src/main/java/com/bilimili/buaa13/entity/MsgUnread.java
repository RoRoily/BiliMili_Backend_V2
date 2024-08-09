package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MsgUnread {
    @TableId
    private Integer uid;    // 用户uid 不自动增长 跟随注册时的uid
    private Integer reply;  // 回复我的
    private Integer atNum;     // @ 我的数量
    private Integer upVote;   // 收到的赞
    private Integer systemMessage; // 系统通知
    private Integer message;    // 我的消息（私聊总数）
    @TableField("`dynamic`")
    private Integer dynamic;    // 动态
}
