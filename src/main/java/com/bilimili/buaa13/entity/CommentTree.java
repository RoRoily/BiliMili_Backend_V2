package com.bilimili.buaa13.entity;

import com.bilimili.buaa13.entity.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentTree {
    private Integer cid;
    private Integer vid;
    private Integer rootId;//根节点ID,如果本身是根节点则设置为0
    private Integer parentId;//父节点ID
    private String content;//评论内容
    private UserDTO user;//谁发的
    private UserDTO toUser;//发给谁
    private Integer upVote;
    private Integer downVote;
    private List<CommentTree> sonNode;//子节点，用于递归
    private Date createTime;
    private Long count;//回复的数量
}
