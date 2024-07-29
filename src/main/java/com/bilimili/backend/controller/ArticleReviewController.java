package com.bilimili.backend.controller;

import com.bilimili.backend.pojo.CustomResponse;
import com.bilimili.backend.service.article.ArticleReviewService;
import com.bilimili.backend.service.article.ArticleService;
import com.bilimili.backend.service.utils.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ArticleReviewController {
    @Autowired
    private ArticleReviewService articleReviewService;
    @Autowired
    private CurrentUser currentUser;
    @Autowired
    private ArticleService articleService;
    /**
     * 获取所有要审核的专栏数量
     *
     */
    @GetMapping("/review/article/total")
    public CustomResponse getTotalArticle(@RequestParam("astatus") Integer status) {
        return articleReviewService.getTotalByStatus(status);
    }

    /**
     * 审核 分页查询对应状态专栏
     * @param status 状态 0待审核 1通过 2未通过
     * @param page  当前页
     * @param quantity  每页的数量
     * @return
     */
    @GetMapping("/review/article/getpage")
    public CustomResponse getArticles(@RequestParam("astatus") Integer status,
                                    @RequestParam(value = "page", defaultValue = "1") Integer page,
                                    @RequestParam(value = "quantity", defaultValue = "10") Integer quantity) {
        return articleReviewService.getArticlesByPage(status, page, quantity);
    }

    /**
     * 审核 查询单个视频详情
     * @param aid 视频id
     * @return
     */
    @GetMapping("/review/article/getone")
    public CustomResponse getOneArticle(@RequestParam("aid") Integer aid) {
        CustomResponse customResponse = new CustomResponse();
        if (!currentUser.isAdmin()) {
            customResponse.setCode(403);
            customResponse.setMessage("您不是管理员，无权访问");
            return customResponse;
        }
        Map<String, Object> map = articleService.getArticleWithDataById(aid);
        customResponse.setData(map);    // 如果是是空照样返回，前端自动处理
        return customResponse;
    }
}
