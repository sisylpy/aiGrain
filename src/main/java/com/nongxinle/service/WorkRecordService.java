package com.nongxinle.service;

import com.nongxinle.ai.workrecord.dto.WorkRecordCategoryDTO;
import com.nongxinle.ai.workrecord.dto.WorkRecordConversationBootstrapDTO;
import com.nongxinle.ai.workrecord.dto.WorkRecordCreateRequest;
import com.nongxinle.ai.workrecord.dto.WorkRecordFromBusinessCardRequest;
import com.nongxinle.ai.workrecord.dto.WorkRecordDeleteResponse;
import com.nongxinle.ai.workrecord.dto.WorkRecordResponse;
import com.nongxinle.ai.workrecord.dto.WorkRecordSourceCardResponse;
import com.nongxinle.ai.workrecord.dto.WorkRecordUpdateRequest;

import java.util.Date;
import java.util.List;

public interface WorkRecordService {

    WorkRecordConversationBootstrapDTO bootstrapConversation(
            Long userId, Long departmentId, Long distributerId);

    WorkRecordResponse createRecord(WorkRecordCreateRequest request);

    WorkRecordResponse createFromBusinessCard(WorkRecordFromBusinessCardRequest request);

    WorkRecordResponse retryAiProcessing(Long recordId, Long userId);

    WorkRecordSourceCardResponse getSourceCard(Long recordId, Long userId);

    WorkRecordResponse updatePolishedContent(Long recordId, Long userId, WorkRecordUpdateRequest request);

    WorkRecordDeleteResponse deleteRecord(Long recordId, Long userId);

    List<WorkRecordResponse> listRecords(
            Long userId,
            Long departmentId,
            Long distributerId,
            Long categoryId,
            Date startDate,
            Date endDate,
            int page,
            int pageSize);

    List<WorkRecordCategoryDTO> listCategories(Long userId, Long distributerId);
}
