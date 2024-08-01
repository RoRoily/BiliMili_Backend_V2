package com.bilimili.buaa13.service.impl.video;

import com.bilimili.buaa13.service.video.VideoUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bilimili.buaa13.entity.dto.VideoUploadInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;


/**
 * 使用多线程代替处理
 */
@Slf4j
@Service
public class DirectVideoUploadConsumer {

    @Autowired
    private VideoUploadService videoUploadService;

    /**
     * 监听消息队列，获取投稿信息，合并分片文件并把信息写入数据库
     * @param jsonPayload 从 rabbitmq 获取的序列化后的投稿信息
     * @throws IOException 抛出IO异常
     */
    @RabbitHandler
    public void handleMergeChunks(String jsonPayload) throws IOException {
        // 使用Jackson库将JSON字符串解析为VideoUploadInfo对象
        ObjectMapper objectMapper = new ObjectMapper();
        VideoUploadInfoDTO videoUploadInfoDTO = objectMapper.readValue(jsonPayload, VideoUploadInfoDTO.class);
        videoUploadService.mergeFragments(videoUploadInfoDTO);
    }
}
