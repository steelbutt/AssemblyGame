package com.assemblygame.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PuzzleRunnerTest {

    private PuzzleLibrary library;
    private PuzzleRunner runner;

    @BeforeEach
    void setUp() {
        library = new PuzzleLibrary();
        library.load();
        runner = new PuzzleRunner();
    }

    // ---- Tutorial: Signal Doubler ----

    @Test
    void tutorialDoubler_correctSolution() {
        Puzzle puzzle = library.getById("tutorial_p01");
        String solution = "loop: CLC\n      LDA $FE\n      ASL A\n      STA $FF\n      JMP loop";
        RunResult result = runner.runAll(puzzle, Map.of(0, solution));
        assertTrue(result.passed(), "Expected PASS but got: " + result.status() + " — " + result.errorMessage());
    }

    @Test
    void tutorialDoubler_wrongSolution() {
        Puzzle puzzle = library.getById("tutorial_p01");
        String solution = "loop: LDA $FE\n      STA $FF\n      JMP loop"; // just copies, no doubling
        RunResult result = runner.runAll(puzzle, Map.of(0, solution));
        assertFalse(result.passed());
        assertEquals("FAIL", result.status());
    }

    @Test
    void tutorialDoubler_infiniteLoopTimeout() {
        Puzzle puzzle = library.getById("tutorial_p01");
        String solution = "loop: JMP loop"; // infinite loop, no output
        RunResult result = runner.runAll(puzzle, Map.of(0, solution));
        assertFalse(result.passed());
        assertEquals("TIMEOUT", result.status());
    }

    @Test
    void tutorialDoubler_emptyProgram() {
        Puzzle puzzle = library.getById("tutorial_p01");
        RunResult result = runner.runAll(puzzle, Map.of(0, "BRK"));
        assertFalse(result.passed());
    }

    // ---- Ch1-P1: Filter ----

    @Test
    void ch1Filter_correctRelay() {
        Puzzle puzzle = library.getById("ch01_p01");
        // Node 0 just forwards; Node 1 (locked) does the filtering
        String relay = "loop: LDA $FE\n      STA $F1\n      JMP loop";
        RunResult result = runner.runAll(puzzle, Map.of(0, relay));
        assertTrue(result.passed(), "Expected PASS but got: " + result.status() +
            " actual=" + result.actualOutput() + " expected=" + result.expectedOutput());
    }

    @Test
    void ch1Filter_wrongRelay() {
        Puzzle puzzle = library.getById("ch01_p01");
        // Node 0 just BRKs immediately — no data flows to Node 1
        RunResult result = runner.runAll(puzzle, Map.of(0, "BRK"));
        assertFalse(result.passed());
    }

    // ---- Ch1-P2: Sum ----

    @Test
    void ch1Sum_correctRelay() {
        Puzzle puzzle = library.getById("ch01_p02");
        String relay = "loop: LDA $FE\n      STA $F1\n      JMP loop";
        RunResult result = runner.runAll(puzzle, Map.of(0, relay));
        assertTrue(result.passed(), "Expected PASS but got: " + result.status() +
            " actual=" + result.actualOutput() + " expected=" + result.expectedOutput());
    }

    // ---- Ch2-P1: Average ----

    @Test
    void ch2Average_correctRelay() {
        Puzzle puzzle = library.getById("ch02_p01");
        String relay = "loop: LDA $FE\n      STA $F1\n      JMP loop";
        RunResult result = runner.runAll(puzzle, Map.of(0, relay));
        assertTrue(result.passed(), "Expected PASS but got: " + result.status() +
            " actual=" + result.actualOutput() + " expected=" + result.expectedOutput());
    }

    // ---- Ch3-P1: Compare and Swap ----

    @Test
    void ch3Swap_correctSolution() {
        Puzzle puzzle = library.getById("ch03_p01");
        String solution =
            "loop: LDA $FE\n" +
            "      STA $00\n" +
            "      LDA $FE\n" +
            "      STA $01\n" +
            "      CMP $00\n" +
            "      BCC swap\n" +
            "      LDA $00\n" +
            "      STA $FF\n" +
            "      LDA $01\n" +
            "      STA $FF\n" +
            "      JMP loop\n" +
            "swap: LDA $01\n" +
            "      STA $FF\n" +
            "      LDA $00\n" +
            "      STA $FF\n" +
            "      JMP loop";
        RunResult result = runner.runAll(puzzle, Map.of(0, solution));
        assertTrue(result.passed(), "Expected PASS but got: " + result.status() +
            " actual=" + result.actualOutput() + " expected=" + result.expectedOutput());
    }

    // ---- Assembly error propagation ----

    @Test
    void assemblyErrorReturnsErrorResult() {
        Puzzle puzzle = library.getById("tutorial_p01");
        RunResult result = runner.runAll(puzzle, Map.of(0, "BADOPCODE #5"));
        assertFalse(result.passed());
        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("unknown opcode"));
    }

    @Test
    void overLimitProgramReturnsError() {
        Puzzle puzzle = library.getById("tutorial_p01");
        StringBuilder code = new StringBuilder();
        for (int i = 0; i <= 20; i++) code.append("NOP\n");
        RunResult result = runner.runAll(puzzle, Map.of(0, code.toString()));
        assertFalse(result.passed());
        assertEquals("ERROR", result.status());
    }
}
