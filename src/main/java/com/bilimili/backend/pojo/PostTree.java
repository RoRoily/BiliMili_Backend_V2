package com.bilimili.backend.pojo;

import com.bilimili.backend.pojo.dto.UserDTO;
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
