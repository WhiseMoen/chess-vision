package ru.chessvision.core;

import java.util.List;
import java.util.Map;

public record Analysis(
        Map<Square, PieceInsight> pieces,
        List<Pattern> patterns,
        List<VisualCue> visualCues,
        int whiteInfluence,
        int blackInfluence
) {}
