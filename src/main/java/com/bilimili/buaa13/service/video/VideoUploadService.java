package com.bilimili.buaa13.service.video;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.dto.VideoUploadInfoDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface VideoUploadService {
    /**
     * 获取视频下一个还没上传的分片序号
     * @param hash 视频的hash值
     * @return 响应对象
     */
    ResponseResult getNextCurrentFragment(String hash);

    /**
     * 上传单个视频分片，当前上传到阿里云对象存储
     * @param fragmentFile 分片文件
     * @param hash  视频的hash值
     * @param index 当前分片的序号
     * @return  响应对象
     * @throws IOException 抛出IO异常
     */
    ResponseResult uploadFragment(MultipartFile fragmentFile, String hash, Integer index) throws IOException;

    /**
     * 取消上传并且删除该视频的分片文件
     * @param hash 视频的hash值
     * @return 响应对象
     */
    ResponseResult cancelUploadAndDelete(String hash);

    /**
     * 接收前端提供的视频信息，包括封面文件和稿件的其他信息，保存完封面后将信息发送到消息队列，并返回投稿成功响应
     * @param cover 封面图片文件
     * @param videoUploadInfoDTO 存放投稿信息的 VideoUploadInfo 对象
     * @return  响应对象
     * @throws JsonProcessingException 抛出Json异常
     */
    ResponseResult setVideoMessage(MultipartFile cover, VideoUploadInfoDTO videoUploadInfoDTO) throws IOException;

    /**
     * 合并分片并将投稿信息写入数据库
     * @param videoUploadInfoDTO 存放投稿信息的 VideoUploadInfo 对象
     */
    void mergeFragments(VideoUploadInfoDTO videoUploadInfoDTO) throws IOException;
}
