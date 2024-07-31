package com.bilimili.buaa13.service.video;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.Video;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface VideoService {
    /**
     * 根据id分页获取视频信息，包括用户和分区信息
     * @param videoList   要查询的视频数组
     * @param page 分页页码 为空默认是1
     * @param quantity  每一页查询的数量 为空默认是10
     * @return  包含用户信息、分区信息、视频信息的map列表
     */
    List<Map<String, Object>> getVideosDataWithPageByVideoList(List<Video>videoList, Integer page, Integer quantity);

    /**
     * 排序获取视频信息
     * @param vidList   要查询的视频id列表，column为null时按该列表顺序排序
     * @param column    要排序的列   可选：null/"upload_date"/"play"/"good"/...
     * @param page  分页页码 从1开始
     * @param quantity  每一页查询的数量
     * @return  map包含用户信息、分区信息、视频信息
     */
    List<Map<String, Object>> getVideosDataWithPageBySort(List<Integer> vidList, @Nullable String column, Integer page, Integer quantity);

    /**
     * 根据vid查询单个视频信息，包含用户信息和分区信息
     * @param vid 视频ID
     * @return 包含用户信息、分区信息、视频信息的map
     */

    Map<String, Object> getVideoWithDataByVideoId(Integer vid);

    /**
     * 根据有序vid列表查询视频以及相关信息
     * @param list  vid有序列表
     * @return  有序的视频列表
     */
    List<Map<String, Object>> getVideosWithDataByVideoIdList(List<Integer> list);

    /**
     * 更新视频状态，包括过审、不通过、删除，其中审核相关需要管理员权限，删除可以是管理员或者投稿用户
     * @param vid   视频ID
     * @param status 要修改的状态，1通过 2不通过 3删除
     * @return 无data返回，仅返回响应信息
     */
    ResponseResult changeVideoStatus(Integer vid, Integer status) throws IOException;
}
