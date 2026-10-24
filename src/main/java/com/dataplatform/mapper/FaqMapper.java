package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.Faq;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FaqMapper extends BaseMapper<Faq> {

    @Select("<script>" +
            "SELECT * FROM faq " +
            "WHERE (question LIKE CONCAT('%', #{keyword}, '%') OR answer LIKE CONCAT('%', #{keyword}, '%')) " +
            "<if test='category != null'>" +
            "  AND category = #{category} " +
            "</if>" +
            "LIMIT 5" +
            "</script>")
    List<Faq> searchFaq(@Param("keyword") String keyword, @Param("category") String category);
}
