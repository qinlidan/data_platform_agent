package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 会话 Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Update("UPDATE conversations SET summary = #{summary}, summary_upto_msg_id = #{summaryUptoMsgId} WHERE id = #{conversationId}")
    int updateSummaryFields(@Param("conversationId") Long conversationId,
                           @Param("summary") String summary,
                           @Param("summaryUptoMsgId") Long summaryUptoMsgId);
}
