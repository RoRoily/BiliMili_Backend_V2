package com.bilimili.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.backend.mapper.ArticleMapper;
import com.bilimili.backend.mapper.VideoMapper;
import com.bilimili.backend.pojo.Article;
import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.pojo.Video;
import com.bilimili.backend.pojo.dto.ArticleUploadDTO;
import com.bilimili.backend.pojo.dto.VideoUploadInfoDTO;
import com.bilimili.backend.service.article.ArticleUploadService;
import com.bilimili.backend.service.utils.CurrentUser;
import com.bilimili.backend.service.video.VideoUploadService;
import com.bilimili.backend.utils.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
public class ArticleUploadController {
    @Autowired
    private ArticleUploadService articleUploadService;
    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private CurrentUser currentUser;
    @Autowired
    private ArticleMapper articleMapper;



    @PostMapping("/image/add")
    @Transactional
    public CustomResponse addImage(
            @RequestParam("image") MultipartFile image

    ){
        try {
            String url = ossUtil.uploadImage(image,"articleArtwork");
            System.out.println(url);
            return new CustomResponse(200,"图片上传成功",url);
        } catch (Exception e) {
            e.printStackTrace();
            return new CustomResponse(500, "封面上传失败", null);
        }

    }

    /**
     * 添加文章投稿
     * @param title 投稿标题
     * @param cover  文章封面
     * @param title 关联的视频内容
     * @param content  文章内容的markdown文件
     * @return  响应对象
     */
    @PostMapping("/article/add/all")
    public CustomResponse addAllArticle(
            @RequestParam("cover") MultipartFile cover,
            //@RequestParam("cover") MultipartFile cover,
            //@RequestParam("title") String title,
            @RequestParam("title") String title,
            @RequestParam("vid") String vid,
            @RequestParam("content") MultipartFile content
            //@RequestParam("type") Integer type,
            //@RequestParam("auth") Integer auth,
            //@RequestParam("duration") Double duration,
            //@RequestParam("mcid") String mcid,
            //@RequestParam("scid") String scid,
            //@RequestParam("tags") String tags,
            //@RequestParam("descr") String descr
    ) {
        //ArticleUploadDTO articleUploadDTO = new ArticleUploadDTO(null, title, content,type, auth, duration, mcid, scid, tags, descr, null);
        ArticleUploadDTO articleUploadDTO = new ArticleUploadDTO(null, content);
        try {
            List<Integer> videoList = new ArrayList<>();
            String[] videos = vid.split(",");
            for (String s : videos) {
                try {
                    videoList.add(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    // 处理可能的转换异常
                    System.err.println("Invalid number format: " + s);
                    return new CustomResponse(200,"关联视频的格式错误",null);
                }
            }
            for(Integer vid1:videoList){
                QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("vid", vid1).ne("status", 3);
                //Viode = articleMapper.selectOne(queryWrapper);
                Video video = videoMapper.selectOne(queryWrapper);
                if(video == null){
                    return new CustomResponse(200,"关联视频列表中包含不存在的视频",null);
                }
            }

            //return articleUploadService.addArticle(articleUploadDTO);
            String url = ossUtil.uploadArticle(content);
            String url2 = ossUtil.uploadImage(cover,"articleCover");
            Article article = new Article();
            article.setTitle(title);
            article.setVid(vid);
            article.setContentUrl(url);
            article.setCoverUrl(url2);
            article.setStatus(0);
            article.setUid(currentUser.getUserId()); // 假设 currentUser 对象可以获取当前用户的 ID
            articleMapper.insert(article);
            return new CustomResponse(200,"文章上传成功",article.getAid().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new CustomResponse(500, "封面上传失败", null);
        }
    }
}
