package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Video {
    @TableId(type = IdType.AUTO)
    private Integer vid;
    private Integer uid;
    private String title;
    private Integer type;
    private Integer author;
    private Double videoTime;
    private String mainClassId;
    private String subClassId;
    private String tags;
    private String description;
    private String coverUrl;
    private String videoUrl;
    private Integer status;     // 0审核中 1通过审核 2打回整改（指投稿信息不符） 3视频违规删除（视频内容违规）
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    private Date uploadDate;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm", timezone = "Asia/Shanghai")
    private Date deleteDate;
}
