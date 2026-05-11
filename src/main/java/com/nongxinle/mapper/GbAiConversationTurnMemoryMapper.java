package com.nongxinle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nongxinle.entity.GbAiConversationTurnMemoryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GbAiConversationTurnMemoryMapper extends BaseMapper<GbAiConversationTurnMemoryEntity> {

    GbAiConversationTurnMemoryEntity selectLatestByConversationAndUser(@Param("conversationId") Long conversationId,
                                                                      @Param("userId") Long userId);
}
