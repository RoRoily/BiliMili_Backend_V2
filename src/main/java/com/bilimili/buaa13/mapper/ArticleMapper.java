package com.bilimili.buaa13.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.buaa13.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @Select("select aid from article where status = #{status}")
    List<Integer> getArticleIdsByStatus(Integer status);
    @Select("select aid from article where uid = #{uids}")
    List<Integer> getArticleIdsByUids(Integer uids);

}
