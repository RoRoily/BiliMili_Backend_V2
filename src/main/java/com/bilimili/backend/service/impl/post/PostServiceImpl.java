package com.bilimili.backend.service.impl.post;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.backend.mapper.PostMapper;
import com.bilimili.backend.pojo.Post;
import com.bilimili.backend.pojo.PostTree;
import com.bilimili.backend.service.post.PostService;
import com.bilimili.backend.service.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bilimili.backend.pojo.dto.UserDTO;

import java.util.*;
@Slf4j
@Service
public class PostServiceImpl implements PostService {
    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserService userService;
    @Override
    public List<PostTree> getPostsByIds(Integer uid) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("uid",uid);
        List<Post> postList = postMapper.selectList(queryWrapper);
        List<PostTree> postTreeList = new ArrayList<>();
        for(Post post : postList){
            UserDTO userDTO = userService.getUserById(post.getUid());
            PostTree postTree = new PostTree(userDTO, post.getPid(), post.getContent(), post.getCreateTime());
            postTreeList.add(postTree);
        }
        return postTreeList;
    }
}
