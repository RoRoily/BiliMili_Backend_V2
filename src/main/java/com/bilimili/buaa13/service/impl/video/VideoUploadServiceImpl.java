package com.bilimili.buaa13.service.impl.video;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.Video;
import com.bilimili.buaa13.entity.VideoStats;
import com.bilimili.buaa13.entity.dto.VideoUploadInfoDTO;
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.mapper.VideoStatsMapper;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.service.video.VideoUploadService;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.OssUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class VideoUploadServiceImpl implements VideoUploadService {

    @Value("${directory.chunk}")
    private String Fragment_Directory;   // 分片存储目录

    @Autowired
    private VideoUploadService videoUploadService;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoStatsMapper videoStatsMapper;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private ESUtil esUtil;

    /**
     * 获取视频下一个还没上传的分片序号
     * @param hash 视频的hash值
     * @return 响应对象
     */
    @Override
    public ResponseResult getNextCurrentFragment(String hash) {
        ResponseResult responseResult = new ResponseResult();
        // 查询本地,获取分片文件的存储目录
        File[] fragmentFiles = getFragmentFiles(hash);
        if(fragmentFiles == null || fragmentFiles.length == 0) {responseResult.setData(0); return responseResult;}
        // 返回还没上传的分片序号
        responseResult.setData(Objects.requireNonNull(fragmentFiles).length);
        return responseResult;
    }

    /**
     * 上传单个视频分片，当前上传到阿里云对象存储
     * @param FragmentFile 分片文件
     * @param hash  视频的hash值
     * @param index 当前分片的序号
     * @return  响应对象
     */
    @Override
    public ResponseResult uploadFragment(MultipartFile FragmentFile, String hash, Integer index) {
        ResponseResult responseResult = new ResponseResult();
        // 连接分片文件名，用视频hash+序号
        String fragmentFileName = hash + "-" + index;
        // 存储到本地,构建分片文件的完整路径
        String fragmentFilePath = Paths.get(Fragment_Directory, fragmentFileName).toString();
        // 检查是否存在相同的分片文件，如果已存在需要返回提示信息
        File fragmentFile = new File(fragmentFilePath);
        if (fragmentFile.exists()) {
            VideoUploadServiceImpl.log.warn("视频分片 {} 已经存在，请重新选取分片", fragmentFilePath);
            responseResult.setMessage("已存在分片文件");
            responseResult.setCode(500);
            return responseResult;
        }
        // 保存分片文件到指定路径
        try (BufferedOutputStream stream = new BufferedOutputStream(Files.newOutputStream(fragmentFile.toPath()))) {
            stream.write(FragmentFile.getBytes());
        }catch (Exception e) {
            log.error(e.getMessage());
            responseResult.setCode(500);
            responseResult.setMessage("上传文件失败");
            return responseResult;
        }
        // 返回成功响应
        return responseResult;
    }

    /**
     * 取消上传并且删除该视频的分片文件
     * @param hash 视频的hash值
     * @return 响应对象
     */
    @Override
    public ResponseResult cancelUploadAndDelete(String hash) {
        ResponseResult responseResult = new ResponseResult();
        // 删除本地分片文件
        // 获取分片文件的存储目录
        File[] fragmentFiles = getFragmentFiles(hash);
        if(fragmentFiles == null || fragmentFiles.length == 0) {
            responseResult.setCode(200);
            responseResult.setMessage("文件不存在");
            responseResult.setData(0);
            return responseResult;
        }
        // 删除全部分片文件
        for (File fragemntFile : fragmentFiles) {
            try {
                Files.delete(Paths.get(fragemntFile.toURI()));
                responseResult.setMessage("删除分片文件成功"); // 删除分片文件
            } catch (Exception e) {
                log.error(e.getMessage());
                responseResult.setCode(200);
                responseResult.setMessage("删除该分片失败");
            }
        }
        // 不管删没删成功 返回成功响应,code 200
        return responseResult;
    }

    /**
     * 接收前端提供的视频信息，包括封面文件和稿件的其他信息，保存完封面后将信息发送到消息队列，并返回投稿成功响应
     * @param cover 封面图片文件
     * @param videoUploadInfoDTO 存放投稿信息的 VideoUploadInfo 对象
     * @return  响应对象
     * @throws IOException 抛出IO异常
     */
    @Override
    public ResponseResult setVideoMessage(MultipartFile cover, VideoUploadInfoDTO videoUploadInfoDTO) throws IOException {
        Integer loginUserId = currentUser.getUserId();
        if (videoUploadInfoDTO.getTitle().trim().isEmpty()) {
            return new ResponseResult(500, "标题不能为空", null);
        }
        if (videoUploadInfoDTO.getTitle().length() > 50) {
            return new ResponseResult(500, "标题请不要超过50字", null);
        }
        if (videoUploadInfoDTO.getDescr().length() > 1000) {
            return new ResponseResult(500, "简介太长啦", null);
        }

        // 保存封面到OSS，返回URL
        String coverUrl = ossUtil.uploadImage(cover, "cover");

        // 将投稿信息封装
        videoUploadInfoDTO.setCoverUrl(coverUrl);
        videoUploadInfoDTO.setUid(loginUserId);

        try {
            videoUploadService.mergeFragments(videoUploadInfoDTO);
        } catch (IOException e) {
            VideoUploadServiceImpl.log.error("合并视频写库时出错了");
            log.error(e.getMessage());
        }
        return new ResponseResult();
    }

    /**
     * 合并分片并将投稿信息写入数据库
     * @param videoUploadInfoDTO 存放投稿信息的 VideoUploadInfo 对象
     */
    @Override
    @Transactional
    public void mergeFragments(VideoUploadInfoDTO videoUploadInfoDTO) throws IOException {
        String url = ossUtil.appendUploadVideo(videoUploadInfoDTO.getHash());; // 视频最终的URL
        if (url == null) {
            log.warn("OSS URL 为空，合并操作终止");
            return;
        }
        // 存入数据库
        Video video = getVideoByVideoUploadInfoDTO(videoUploadInfoDTO, url);
        videoMapper.insert(video);
        VideoStats videoStats = new VideoStats(video.getVid(),0,0,0,0,0,0,0,0);
        videoStatsMapper.insert(videoStats);
        esUtil.addVideo(video);
        //注释Redis
        /*CompletableFuture.runAsync(() -> redisUtil.setExObjectValue("video:" + video.getVid(), video), taskExecutor);
        CompletableFuture.runAsync(() -> redisUtil.addMember("video_status:0", video.getVid()), taskExecutor);
        CompletableFuture.runAsync(() -> redisUtil.setExObjectValue("videoStats:" + video.getVid(), videoStats), taskExecutor);*/
    }

    /**
     * 获取视频信息
     * @param videoUploadInfoDTO 视频传输的数据信息
     * @param url 视频url
     * @return Video类
     */
    private static @NotNull Video getVideoByVideoUploadInfoDTO(VideoUploadInfoDTO videoUploadInfoDTO, String url) {
        Date now = new Date();
        return new Video(
                null,
                videoUploadInfoDTO.getUid(),
                videoUploadInfoDTO.getTitle(),
                videoUploadInfoDTO.getType(),
                videoUploadInfoDTO.getAuth(),
                videoUploadInfoDTO.getDuration(),
                videoUploadInfoDTO.getMcId(),
                videoUploadInfoDTO.getScId(),
                videoUploadInfoDTO.getTags(),
                videoUploadInfoDTO.getDescr(),
                videoUploadInfoDTO.getCoverUrl(),
                url,
                0,
                now,
                null
        );
    }

    private File[] getFragmentFiles(String hash) {
        File fragmentDirectory = new File(Fragment_Directory);
        // 获取存储在目录中的所有分片文件
        File[] files = fragmentDirectory.listFiles();
        List<File> listFiles = new ArrayList<>();
        if (files == null || files.length == 0) {return null;}
        else{
            for (File file : files) {
                if(file.getName().startsWith(hash+"-")) listFiles.add(file);
            }
        }
        if(!listFiles.isEmpty()) {
            return null;
        }
        return listFiles.toArray(new File[0]);
    }
}
