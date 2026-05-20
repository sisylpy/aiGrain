package com.nongxinle.ai.harness.replay;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单条断言失败；REST JSON 使用 failedField / failedFields / expectedValue / actualValue 便于审阅。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHarnessMismatch {

    private AiHarnessFailureType type;

    /** 人类可读的语义字段名，如 effectiveIntentCode、startDate */
    @JsonProperty("failedField")
    private String field;

    @JsonProperty("expectedValue")
    private Object expected;

    @JsonProperty("actualValue")
    private Object actual;

    /** 从 1 开始；由 {@link AiHarnessReplayService} 在每轮 compare 后填充。 */
    private Integer roundIndex;

    /**
     * 失败时处于的比较器方法（如 {@code AiHarnessExpectationComparator.assertAnswerPreviewContract}）。
     */
    private String comparatorName;

    /** 与 {@link #field} 一致的单元素列表，便于 jq/脚本批量读 failures。 */
    @JsonProperty("failedFields")
    public List<String> getFailedFields() {
        return field == null || field.isEmpty() ? List.of() : List.of(field);
    }
}
