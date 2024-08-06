package com.bilimili.buaa13.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ESVideo {
    private Integer vid;
    private Integer uid;
    private String title;
    private String mainClassId;
    private String subClassId;
    private String tags;
    private Integer status;
}
