#!/usr/bin/env python3
"""Remove deterministic-renderer blocks from StubAnswerComposerNode (line ranges 1-based inclusive)."""
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    p = root / "src/main/java/com/nongxinle/ai/graph/business/StubAnswerComposerNode.java"
    lines = p.read_text(encoding="utf-8").splitlines(keepends=True)
    ranges = [
        (3738, 3754),
        (3648, 3736),
        (3246, 3301),
        (2791, 2818),
        (2561, 2761),
        (1734, 2421),
        (1425, 1430),
        (1323, 1401),
        (1181, 1187),
        (398, 1162),
        (90, 91),
        (61, 68),
    ]
    drop = set()
    for a, b in ranges:
        for i in range(a, b + 1):
            drop.add(i)
    out = "".join(line for idx, line in enumerate(lines, start=1) if idx not in drop)
    p.write_text(out, encoding="utf-8")


if __name__ == "__main__":
    main()
