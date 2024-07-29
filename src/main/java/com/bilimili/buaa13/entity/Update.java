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
public class Update {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer vid;  //关联的视频，如果没有关联视频，则置0
    private Integer uid; //
    private Integer rootId;
    private Integer parentId;
    private Integer toUserId;
    private String content;
    private Integer love;
    private Integer bad;
    private Date createTime;
    // private Integer isTop; //是否需要设置为置顶
     private Integer isDeleted;
}
