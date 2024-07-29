package com.bilimili.backend.controller;

import com.bilimili.backend.mapper.ArticleMapper;
import com.bilimili.backend.mapper.PostMapper;
import com.bilimili.backend.pojo.Article;
import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.pojo.Post;
import com.bilimili.backend.pojo.PostTree;
import com.bilimili.backend.pojo.dto.ArticleUploadDTO;
import com.bilimili.backend.service.article.ArticleUploadService;
import com.bilimili.backend.service.post.PostService;
import com.bilimili.backend.service.utils.CurrentUser;
import com.bilimili.backend.utils.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;

@RestController
public class PostController {


    @Autowired
    private ArticleUploadService articleUploadService;
    @Autowired
    private OssUtil ossUtil;


    @Autowired
    private PostMapper postMapper;
    @Autowired
    private CurrentUser currentUser;
    @Autowired
    private ArticleMapper articleMapper;
    @Autowired
    private PostService postService;
    /**
     * 添加动态
     * @param content 动态内容
     * @return  响应对象
     */
    @PostMapping("/post/add")
    public CustomResponse addAllArticle(
            @RequestParam("uid") Integer uid,
            @RequestParam("content") String content
            //@RequestParam("cover") MultipartFile cover,
            //@RequestParam("title") String title,
            //@RequestParam("type") Integer type,
            //@RequestParam("auth") Integer auth,
            //@RequestParam("duration") Double duration,
            //@RequestParam("mcid") String mcid,
            //@RequestParam("scid") String scid,
            //@RequestParam("tags") String tags,
            //@RequestParam("descr") String descr
    ) {
        //ArticleUploadDTO articleUploadDTO = new ArticleUploadDTO(null, title, content,type, auth, duration, mcid, scid, tags, descr, null);
        //ArticleUploadDTO articleUploadDTO = new ArticleUploadDTO(null, content);
        try {
            Post post = new Post();
            post.setContent(content);
            post.setLove(0);
            post.setCreateTime(new Date());
            post.setUnlove(0);
            post.setUid(uid);
            System.out.println("uid: " + uid +  "content : "+ content );
            postMapper.insert(post);
                //return articleUploadService.addArticle(articleUploadDTO)
            return new CustomResponse(200,"动态发布成功",post.getPid().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new CustomResponse(500, "动态发布失败", null);
        }
    }

    @GetMapping("/post/get")
    public CustomResponse getOneUserInfo(@RequestParam("uid") Integer uid) {
        System.out.println("被要求传输post,uid:" + uid);
        CustomResponse customResponse = new CustomResponse();
        List<PostTree> postTrees = postService.getPostsByIds(uid);
        customResponse.setData(postTrees);
        for(PostTree p : postTrees)System.out.println(p);
        return customResponse;
    }
}
