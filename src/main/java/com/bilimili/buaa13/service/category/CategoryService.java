package com.bilimili.buaa13.service.category;

import com.bilimili.buaa13.entity.Category;
import com.bilimili.buaa13.entity.ResponseResult;

public interface CategoryService {
    /**
     * 获取全部分区数据
     * @return CustomResponse对象
     */
    ResponseResult getAll();

    /**
     * 根据id查询对应分区信息
     * @param mcId 主分区ID
     * @param scId 子分区ID
     * @return Category类信息
     */
    Category getCategoryById(String mcId, String scId);
}
