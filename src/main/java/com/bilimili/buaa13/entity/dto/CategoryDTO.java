package com.bilimili.buaa13.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {
    private String mcId;
    private String mcName;
    private List<Map<String, Object>> scList;
}
