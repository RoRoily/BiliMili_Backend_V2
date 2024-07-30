package com.bilimili.buaa13.service.impl.post;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.buaa13.mapper.PostMapper;
import com.bilimili.buaa13.entity.Post;
import com.bilimili.buaa13.entity.PostTree;
import com.bilimili.buaa13.service.post.PostService;
import com.bilimili.buaa13.service.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.bilimili.buaa13.entity.dto.UserDTO;

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
            UserDTO userDTO = userService.getUserByUId(post.getUid());
            PostTree postTree = new PostTree(userDTO, post.getPid(), post.getContent(), post.getCreateTime());
            postTreeList.add(postTree);
        }
        return postTreeList;
    }
}
