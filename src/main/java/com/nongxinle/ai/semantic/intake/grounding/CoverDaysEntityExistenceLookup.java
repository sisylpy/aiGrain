package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.identity.BusinessEntityExistenceLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @deprecated 存在性探测 SSOT 已迁至 {@link BusinessEntityExistenceLookup}；本类仅作兼容委托。
 */
@Deprecated
@Component
@RequiredArgsConstructor
public class CoverDaysEntityExistenceLookup {

    /** @deprecated 使用 {@link EntityExistence} */
    @Deprecated
    public enum Existence {
        NOT_FOUND,
        UNIQUE,
        AMBIGUOUS
    }

    private final BusinessEntityExistenceLookup businessEntityExistenceLookup;

    public Existence probeDish(int disId, String entityName) {
        return map(businessEntityExistenceLookup.probeDish(disId, entityName));
    }

    public Existence probeGoods(int disId, String entityName) {
        return map(businessEntityExistenceLookup.probeGoods(disId, entityName));
    }

    private static Existence map(EntityExistence existence) {
        if (existence == null) {
            return Existence.NOT_FOUND;
        }
        return switch (existence) {
            case UNIQUE -> Existence.UNIQUE;
            case AMBIGUOUS -> Existence.AMBIGUOUS;
            case NOT_FOUND -> Existence.NOT_FOUND;
        };
    }
}
