package com.assemblygame.game;

import java.util.List;

/**
 * Result of running a puzzle with player-supplied programs.
 */
public record RunResult(
    boolean passed,
    String status,           // PASS, FAIL, TIMEOUT, ERROR
    String errorMessage,     // non-null on ERROR
    List<Integer> actualOutput,
    List<Integer> expectedOutput,
    int totalCycles
) {
    public static RunResult pass(List<Integer> actual, int cycles) {
        return new RunResult(true, "PASS", null, actual, actual, cycles);
    }

    public static RunResult fail(List<Integer> actual, List<Integer> expected, int cycles) {
        return new RunResult(false, "FAIL", null, actual, expected, cycles);
    }

    public static RunResult timeout(List<Integer> actual, List<Integer> expected) {
        return new RunResult(false, "TIMEOUT", "Execution exceeded cycle limit (possible deadlock)", actual, expected, -1);
    }

    public static RunResult error(String message) {
        return new RunResult(false, "ERROR", message, List.of(), List.of(), -1);
    }
}
