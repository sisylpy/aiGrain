package com.nongxinle.ai.history.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationListResponseDTO {

    private long total;

    private int page;

    private int pageSize;

    private List<AiConversationListItemDTO> items = new ArrayList<>();
}
