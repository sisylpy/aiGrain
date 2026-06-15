package com.nongxinle.ai.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkNoteMineListResponseDTO {

    private long total;
    private int page;
    private int pageSize;
    private List<WorkNoteMineListItemDTO> items = new ArrayList<>();
}
