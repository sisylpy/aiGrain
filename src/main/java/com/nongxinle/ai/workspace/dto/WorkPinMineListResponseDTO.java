package com.nongxinle.ai.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkPinMineListResponseDTO {

    private long total;
    private int page;
    private int pageSize;
    private List<WorkPinMineListItemDTO> items = new ArrayList<>();
}
