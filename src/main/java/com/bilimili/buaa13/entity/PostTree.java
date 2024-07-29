package com.bilimili.buaa13.entity;

import com.bilimili.buaa13.entity.dto.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostTree {
    private UserDTO user;
    private Integer pid;
    private String content;
    private Date createDate;
}
