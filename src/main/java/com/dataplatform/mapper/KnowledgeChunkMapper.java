package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库切块Mapper
 * Task 2 - 实体类与MyBatis Mapper
 *
 * 提供：CRUD操作、批量插入、状态更新、批量查询
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /**
     * 批量插入知识块
     * @param entities 知识块列表
     */
    default void insertBatch(List<KnowledgeChunk> entities) {
        entities.forEach(this::insert);
    }

    /**
     * 批量更新向量化状态
     * @param ids chunk ID列表
     * @param status 新状态：pending_vectorization | vectorized
     */
    @Update("<script>" +
            "UPDATE knowledge_chunks SET vectorize_status = #{status} " +
            "WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    void updateVectorizeStatus(@Param("ids") List<Long> ids, @Param("status") String status);
}
