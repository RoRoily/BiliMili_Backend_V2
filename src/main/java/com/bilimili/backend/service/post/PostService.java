package com.bilimili.backend.service.post;

import com.bilimili.backend.pojo.PostTree;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PostService {
    public List<PostTree> getPostsByIds(Integer uid);
}
