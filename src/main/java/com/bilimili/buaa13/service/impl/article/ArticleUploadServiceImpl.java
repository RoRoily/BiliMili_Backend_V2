package com.bilimili.buaa13.service.impl.article;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.dto.ArticleUploadDTO;
import com.bilimili.buaa13.service.article.ArticleUploadService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.OssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class ArticleUploadServiceImpl implements ArticleUploadService {

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private OssUtil ossUtil;

    @Override
    public ResponseResult addArticle(ArticleUploadDTO articleUploadDTO) {
        Integer loginUserId = currentUser.getUserId();
        articleUploadDTO.setUid(loginUserId);
        try {
            String url = ossUtil.uploadArticle(articleUploadDTO.getContent());
        } catch (IOException e) {
            log.error("合并视频写库时出错了");
            log.error(e.getMessage());
        }
        return new ResponseResult();
    }
}
