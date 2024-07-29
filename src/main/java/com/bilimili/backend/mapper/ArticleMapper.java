package com.bilimili.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.backend.pojo.Article;
import com.bilimili.backend.pojo.Category;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bilimili.backend.pojo.Comment;
import com.bilimili.backend.pojo.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @Select("select aid from article where status = #{status}")
    List<Integer> getArticleIdsByStatus(Integer status);
    @Select("select aid from article where uid = #{uids}")
    List<Integer> getArticleIdsByUids(Integer uids);

}
