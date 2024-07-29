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
public class UpdateTree {
    private Integer id;
    private Integer vid;
    private Integer rootId;
    private Integer parentId;
    private String content;
    private UserDTO user;
    private UserDTO toUser;
    private Integer love;
    private Integer bad;
    private List<CommentTree> replies;
    private Date createTime;
    private Long count;
}

