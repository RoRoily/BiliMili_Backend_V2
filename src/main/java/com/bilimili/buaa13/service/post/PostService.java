package com.bilimili.buaa13.service.post;

import com.bilimili.buaa13.entity.PostTree;

import java.util.List;

public interface PostService {
    public List<PostTree> getPostsByIds(Integer uid);
}
