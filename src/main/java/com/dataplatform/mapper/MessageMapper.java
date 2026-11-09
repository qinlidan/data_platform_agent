package com.dataplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息表 Mapper
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 按消息ID范围查询
     * @param conversationId 会话ID
     * @param fromMsgId 起始消息ID（不含）
     * @param uptoMsgId 结束消息ID（含）
     * @return 消息列表
     */
    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} " +
            "AND id > #{fromMsgId} AND id <= #{uptoMsgId} ORDER BY id ASC")
    List<Message> selectByIdRange(Long conversationId, Long fromMsgId, Long uptoMsgId);

    /**
     * 从指定消息ID开始查询
     * @param conversationId 会话ID
     * @param fromMsgId 起始消息ID（含）
     * @return 消息列表
     */
    @Select("SELECT * FROM messages WHERE conversation_id = #{conversationId} " +
            "AND id >= #{fromMsgId} ORDER BY id ASC")
    List<Message> selectFromId(Long conversationId, Long fromMsgId);
}
