package com.assemblygame.game;

import java.util.List;

/**
 * Immutable data record representing a loaded puzzle.
 */
public record Puzzle(
    String id,
    int chapter,
    String title,
    String briefing,      // loaded from story file [BRIEFING] section
    String logText,       // loaded from story file [LOG] section (may be null)
    int gridRows,
    int gridCols,
    List<NodeDef> nodes,
    List<PortDef> ports,
    List<TestVector> testVectors,
    int maxCycles
) {

    public record NodeDef(
        int id,
        int row,
        int col,
        boolean locked,
        String lockedCode  // null if not locked
    ) {}

    public record PortDef(
        int fromNode,
        int toNode,
        PortDirection direction
    ) {}

    public enum PortDirection { LEFT, RIGHT, UP, DOWN }

    public record TestVector(
        List<Integer> input,
        List<Integer> output
    ) {}

    public NodeDef getNode(int id) {
        return nodes.stream().filter(n -> n.id() == id).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No node with id " + id));
    }
}
