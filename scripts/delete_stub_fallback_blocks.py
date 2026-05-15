#!/usr/bin/env python3
"""Remove migrated deterministic fallback blocks from StubAnswerComposerNode.

Deletes ranges from bottom to top so indices stay stable (1-based inclusive).
"""
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    p = root / "src/main/java/com/nongxinle/ai/graph/business/StubAnswerComposerNode.java"
    ranges = [
        (3641, 3734),  # stockReduceQueryDeterministicFallback + toolEnvelope
        (3239, 3294),  # unwrapRevenueToolData + revenueOverviewDeterministicFallback
        (2784, 2811),  # stockSnapshotHasSignal
        (2554, 2755),  # warehouse fallback + helpers .. before extractPurchaseOverviewPayload
        (1727, 2414),  # purchaseCostFallback .. purchaseHasDataRows
        (1316, 1394),  # shortFallbackCost .. capCopy (keep nz, plainNumericHint)
        (1174, 1180),  # extractOverviewNumericHeadlinePreferAnswerPlan
        (391, 1041),  # diagnosis/dish deterministic + shortFallbackDishProfit
    ]
    lines = p.read_text(encoding="utf-8").splitlines(keepends=True)
    for a, b in sorted(ranges, key=lambda x: -x[0]):
        del lines[a - 1 : b]
    p.write_text("".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
