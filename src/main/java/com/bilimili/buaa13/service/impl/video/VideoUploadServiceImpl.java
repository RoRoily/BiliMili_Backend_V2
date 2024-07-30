package com.bilimili.buaa13.service.impl.video;

import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.mapper.VideoStatsMapper;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.Video;
import com.bilimili.buaa13.entity.VideoStats;
import com.bilimili.buaa13.entity.dto.VideoUploadInfoDTO;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.service.video.VideoUploadService;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.OssUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.*;

@Slf4j
@Service
public class VideoUploadServiceImpl implements VideoUploadService {

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

    /**
     * 获取视频下一个还没上传的分片序号
     * @param hash 视频的hash值
     * @return CustomResponse对象
     */
    @Override
    public ResponseResult askCurrentChunk(String hash) {
        ResponseResult responseResult = new ResponseResult();

        // 查询本地
        // 获取分片文件的存储目录
        File chunkDir = new File(CHUNK_DIRECTORY);
        // 获取存储在目录中的所有分片文件
        File[] chunkFiles = chunkDir.listFiles((dir, name) -> name.startsWith(hash + "-"));
        // 返回还没上传的分片序号
        if (chunkFiles == null) {
            responseResult.setData(0);
        } else {
            responseResult.setData(chunkFiles.length);
        }

        // 查询OSS当前存在的分片数量，即前端要上传的分片序号，建议分布式系统才使用OSS存储分片，单体系统本地存储分片效率更高
//        int counts = ossUploadUtil.countFiles("chunk/", hash + "-");
//        responseResult.setData(counts);

        return responseResult;
    }

    /**
     * 上传单个视频分片，当前上传到阿里云对象存储
     * @param chunk 分片文件
     * @param hash  视频的hash值
     * @param index 当前分片的序号
     * @return  CustomResponse对象
     * @throws IOException
     */
    @Override
    public ResponseResult uploadChunk(MultipartFile chunk, String hash, Integer index) throws IOException {
        ResponseResult responseResult = new ResponseResult();
        // 构建分片文件名
        String chunkFileName = hash + "-" + index;

        // 存储到本地
        // 构建分片文件的完整路径
        String chunkFilePath = Paths.get(CHUNK_DIRECTORY, chunkFileName).toString();
        // 检查是否已经存在相同的分片文件
        File chunkFile = new File(chunkFilePath);
        if (chunkFile.exists()) {
            log.warn("分片 " + chunkFilePath + " 已存在");
            responseResult.setCode(500);
            responseResult.setMessage("已存在分片文件");
            return responseResult;
        }
        // 保存分片文件到指定目录
        chunk.transferTo(chunkFile);

        // 存储到OSS，建议分布式系统才使用OSS存储分片，单体系统本地存储分片效率更高
//        try {
//            boolean flag = ossUploadUtil.uploadChunk(chunk, chunkFileName);
//            if (!flag) {
//                log.warn("分片 " + chunkFileName + " 已存在");
//                responseResult.setCode(500);
//                responseResult.setMessage("已存在分片文件");
//            }
//        } catch (IOException ioe) {
//            log.error("读取分片文件数据流时出错了");
//        }

        // 返回成功响应
        return responseResult;
    }

    /**
     * 取消上传并且删除该视频的分片文件
     * @param hash 视频的hash值
     * @return CustomResponse对象
     */
    @Override
    public ResponseResult cancelUpload(String hash) {

        // 删除本地分片文件
        // 获取分片文件的存储目录
        File chunkDir = new File(CHUNK_DIRECTORY);
        // 获取存储在目录中的所有分片文件
        File[] chunkFiles = chunkDir.listFiles((dir, name) -> name.startsWith(hash + "-"));
//        System.out.println("检索到要删除的文件数: " + chunkFiles.length);
        // 删除全部分片文件
        if (chunkFiles != null && chunkFiles.length > 0) {
            for (File chunkFile : chunkFiles) {
                chunkFile.delete(); // 删除分片文件
            }
        }
//        System.out.println("删除分片完成");

        // 删除OSS上的分片文件，建议分布式系统才使用OSS存储分片，单体系统本地存储分片效率更高
//        ossUploadUtil.deleteFiles("chunk/", hash + "-");

        // 不管删没删成功 返回成功响应
        return new ResponseResult();
    }

    /**
     * 接收前端提供的视频信息，包括封面文件和稿件的其他信息，保存完封面后将信息发送到消息队列，并返回投稿成功响应
     * @param cover 封面图片文件
     * @param videoUploadInfoDTO 存放投稿信息的 VideoUploadInfo 对象
     * @return  CustomResponse对象
     * @throws IOException
     */
    @Override
    public ResponseResult addVideo(MultipartFile cover, VideoUploadInfoDTO videoUploadInfoDTO) throws IOException {
        Integer loginUserId = currentUser.getUserId();
        // 值的判定 虽然前端会判 防止非法请求 不过数据库也写不进去 但会影响封面保存
        if (videoUploadInfoDTO.getTitle().trim().length() == 0) {
            return new ResponseResult(500, "标题不能为空", null);
        }
        if (videoUploadInfoDTO.getTitle().length() > 80) {
            return new ResponseResult(500, "标题不能超过80字", null);
        }
        if (videoUploadInfoDTO.getDescr().length() > 2000) {
            return new ResponseResult(500, "简介太长啦", null);
        }

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
        String coverUrl = ossUtil.uploadImage(cover, "cover");

        // 将投稿信息封装
        videoUploadInfoDTO.setCoverUrl(coverUrl);
        videoUploadInfoDTO.setUid(loginUserId);

//        mergeChunks(videoUploadInfo);   // 这里是串行操作，严重影响性能

        // 发送到消息队列等待监听写库
        // 序列化 videoUploadInfo 对象为 String， 往 rabbitmq 中发送投稿信息，也可以使用多线程异步
//        ObjectMapper objectMapper = new ObjectMapper();
//        String jsonPayload = objectMapper.writeValueAsString(videoUploadInfo);
//        rabbitTemplate.convertAndSend("direct_upload_exchange", "videoUpload", jsonPayload);

        // 使用异步线程最佳，因为监听rabbitmq的始终是单线程，高峰期会堆积阻塞
        CompletableFuture.runAsync(() -> {
            try {
                mergeChunks(videoUploadInfoDTO);
            } catch (IOException e) {
                log.error("合并视频写库时出错了");
                e.printStackTrace();
            }
        }, taskExecutor);

        return new ResponseResult();
    }

    /**
     * 合并分片并将投稿信息写入数据库
     * @param vui 存放投稿信息的 VideoUploadInfo 对象
     */
    @Transactional
    public void mergeChunks(VideoUploadInfoDTO vui) throws IOException {
        String url; // 视频最终的URL
        // 合并到OSS，并返回URL地址
        url = ossUtil.appendUploadVideo(vui.getHash());
        if (url == null) {
            return;
        }

        // 存入数据库
        Date now = new Date();
        Video video = new Video(
                null,
                vui.getUid(),
                vui.getTitle(),
                vui.getType(),
                vui.getAuth(),
                vui.getDuration(),
                vui.getMcId(),
                vui.getScId(),
                vui.getTags(),
                vui.getDescr(),
                vui.getCoverUrl(),
                url,
                0,
                now,
                null
        );
        videoMapper.insert(video);
        VideoStats videoStats = new VideoStats(video.getVid(),0,0,0,0,0,0,0,0);
        videoStatsMapper.insert(videoStats);
        esUtil.addVideo(video);
        CompletableFuture.runAsync(() -> redisUtil.setExObjectValue("video:" + video.getVid(), video), taskExecutor);
        CompletableFuture.runAsync(() -> redisUtil.addMember("video_status:0", video.getVid()), taskExecutor);
        CompletableFuture.runAsync(() -> redisUtil.setExObjectValue("videoStats:" + video.getVid(), videoStats), taskExecutor);

        // 其他逻辑 （发送消息通知写库成功）
    }
}
