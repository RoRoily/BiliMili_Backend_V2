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
public class PostComment {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer pid;
    private Integer uid;
    private Integer rootId;
    private Integer parentId;
    private Integer toUserId;
    private String content;
    private Integer love;
    private Integer bad;
    private Date createTime;
    private Integer isTop;
    private Integer isDeleted;
}

