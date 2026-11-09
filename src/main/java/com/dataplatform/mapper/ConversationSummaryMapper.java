package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.ConversationSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ConversationSummaryMapper extends BaseMapper<ConversationSummary> {

    @Select("SELECT * FROM conversation_summaries WHERE conversation_id = #{conversationId} ORDER BY seq ASC")
    List<ConversationSummary> selectByConversationId(Long conversationId);
}
