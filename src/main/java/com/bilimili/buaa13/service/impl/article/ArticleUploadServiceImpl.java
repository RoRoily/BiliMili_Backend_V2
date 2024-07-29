package com.bilimili.buaa13.service.impl.article;
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.mapper.VideoStatsMapper;
import com.bilimili.buaa13.entity.CustomResponse;
import com.bilimili.buaa13.entity.dto.ArticleUploadDTO;
import com.bilimili.buaa13.service.article.ArticleUploadService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.OssUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.*;

@Slf4j
@Service
public class ArticleUploadServiceImpl implements ArticleUploadService {
    @Value("${directory.cover}")
    private String COVER_DIRECTORY;   // 投稿封面存储目录
    @Value("${directory.video}")
    private String VIDEO_DIRECTORY;   // 投稿视频存储目录
    @Value("${directory.chunk}")
    private String CHUNK_DIRECTORY;   // 分片存储目录

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoStatsMapper videoStatsMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private ESUtil esUtil;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Override
    public CustomResponse addArticle(/*MultipartFile cover */ArticleUploadDTO articleUploadDTO) throws IOException {
        Integer loginUserId = currentUser.getUserId();
        // 值的判定 虽然前端会判 防止非法请求 不过数据库也写不进去 但会影响封面保存
        /*
        if (articleUploadDTO.getTitle().trim().isEmpty()) {
            return new CustomResponse(500, "标题不能为空", null);
        }
        if (articleUploadDTO.getTitle().length() > 80) {
            return new CustomResponse(500, "标题不能超过80字", null);
        }
        if (articleUploadDTO.getDescr().length() > 50) {
            return new CustomResponse(500, "简介太长啦", null);
        }*/

        // 保存封面到本地
//        try {
//            // 获取当前时间戳
//            long timestamp = System.currentTimeMillis();
//            String fileName = timestamp + videoUploadInfo.getHash() + ".jpg";
//            String path = Paths.get(COVER_DIRECTORY, fileName).toString();
//            File file = new File(path);
////            System.out.println(file.getAbsolutePath());
//            cover.transferTo(file);
//            videoUploadInfo.setCoverUrl(file.getAbsolutePath());
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        // 保存封面到OSS，返回URL
       /* String coverUrl = ossUtil.uploadImage(cover, "articleCover");

        // 将投稿信息封装
        articleUploadDTO.setCoverUrl(coverUrl);*/
        articleUploadDTO.setUid(loginUserId);

//        mergeChunks(videoUploadInfo);   // 这里是串行操作，严重影响性能

        // 发送到消息队列等待监听写库
        // 序列化 videoUploadInfo 对象为 String， 往 rabbitmq 中发送投稿信息，也可以使用多线程异步
//        ObjectMapper objectMapper = new ObjectMapper();
//        String jsonPayload = objectMapper.writeValueAsString(videoUploadInfo);
//        rabbitTemplate.convertAndSend("direct_upload_exchange", "videoUpload", jsonPayload);

        // 使用异步线程最佳，因为监听rabbitmq的始终是单线程，高峰期会堆积阻塞
        CompletableFuture.runAsync(() -> {
            try {
                String url = ossUtil.uploadArticle(articleUploadDTO.getContent());
            } catch (IOException e) {
                log.error("合并视频写库时出错了");
                e.printStackTrace();
            }
        }, taskExecutor);

        return new CustomResponse();
    }
}
