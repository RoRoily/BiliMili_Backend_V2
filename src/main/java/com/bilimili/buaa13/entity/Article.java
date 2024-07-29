package com.bilimili.buaa13.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//文章图片的处理：
//图片的位置被替换为url路径，通过插件渲染，不需要额外添加图片的存储路径

//文章提出的uid使用redis实现？
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Article {
    @TableId(type = IdType.AUTO)
    private Integer aid;
    private Integer uid;
    private Integer status; //// 0审核中 1通过审核 2打回整改（指投稿信息不符） 3文章违规删除（视频内容违规）
    private String title;
    private String coverUrl;
    private String contentUrl;
    private String vid;
}
