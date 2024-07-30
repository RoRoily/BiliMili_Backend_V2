package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.mapper.ArticleMapper;
import com.bilimili.buaa13.mapper.PostMapper;
import com.bilimili.buaa13.entity.Post;
import com.bilimili.buaa13.entity.PostTree;
import com.bilimili.buaa13.service.article.ArticleUploadService;
import com.bilimili.buaa13.service.post.PostService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseResult addAllArticle(
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
            return new ResponseResult(200,"动态发布成功",post.getPid().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult(500, "动态发布失败", null);
        }
    }

    @GetMapping("/post/get")
    public ResponseResult getOneUserInfo(@RequestParam("uid") Integer uid) {
        System.out.println("被要求传输post,uid:" + uid);
        ResponseResult responseResult = new ResponseResult();
        List<PostTree> postTrees = postService.getPostsByIds(uid);
        responseResult.setData(postTrees);
        for(PostTree p : postTrees)System.out.println(p);
        return responseResult;
    }
}
