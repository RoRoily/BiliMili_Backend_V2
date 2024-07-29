package com.bilimili.backend.service.article;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.pojo.dto.ArticleUploadDTO;
import com.bilimili.backend.pojo.dto.VideoUploadInfoDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ArticleUploadService {
    /**
     * 接收前端提供的视频信息，包括封面文件和稿件的其他信息，保存完封面后将信息发送到消息队列，并返回投稿成功响应
     * @param articleUploadDTO 存放投稿信息的 VideoUploadInfo 对象
     * @return  CustomResponse对象
     * @throws JsonProcessingException
     */
    CustomResponse addArticle(/*MultipartFile cover,*/ ArticleUploadDTO articleUploadDTO) throws IOException;
}
