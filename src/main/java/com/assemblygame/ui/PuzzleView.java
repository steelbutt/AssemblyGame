package com.assemblygame.ui;

import com.assemblygame.core.Assembler;
import com.assemblygame.game.*;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.TerminalSize;

import java.util.*;

/**
 * Main puzzle screen: node editor grid on the left, briefing + test vectors on the right.
 *
 * Layout:
 *   ╔══════════════════════════════════════════════════════════════════╗
 *   ║  ▓▓ DELTA-6 ▓▓  [CHAPTER X: TITLE]     puzzle title            ║
 *   ╠══════[NODE 0]══╦══[NODE 1]══╦═══════════════════════════════════╣
 *   ║ editor lines   ║ editor     ║ briefing text                     ║
 *   ╠════════════════╩════════════╩═══════════════════════════════════╣
 *   ║  [R]un  [S]tep  [Tab] next node  [Q]uit    Cycles: N  STATUS   ║
 *   ╚══════════════════════════════════════════════════════════════════╝
 */
public class PuzzleView {

    private static final int BRIEFING_WIDTH = 30;
    private static final int NODE_PANEL_WIDTH = 22;
    private static final int EDITOR_LINES = 22; // visible lines per node

    private final Screen screen;
    private final Puzzle puzzle;
    private final SaveState saveState;
    private final PuzzleRunner runner;
    private final String chapterTitle;

    // Editor state per node
    private final Map<Integer, List<String>> nodeLines = new LinkedHashMap<>();
    private int activeNodeId;
    private int cursorLine = 0;
    private int scrollOffset = 0;

    // Status
    private String statusMsg = "READY";
    private TextColor statusColor = TextColor.ANSI.GREEN;
    private List<Integer> lastActualOutput = new ArrayList<>();
    private int lastCycles = 0;
    private boolean showHelp = false;

    public PuzzleView(Screen screen, Puzzle puzzle, SaveState saveState,
                      PuzzleRunner runner, String chapterTitle) {
        this.screen = screen;
        this.puzzle = puzzle;
        this.saveState = saveState;
        this.runner = runner;
        this.chapterTitle = chapterTitle;

        // Load saved code for each editable node
        for (Puzzle.NodeDef node : puzzle.nodes()) {
            if (!node.locked()) {
                String saved = saveState.getCode(puzzle.id(), node.id());
                List<String> lines = new ArrayList<>(Arrays.asList(saved.split("\n", -1)));
                if (lines.isEmpty()) lines.add("");
                nodeLines.put(node.id(), lines);
            }
        }

        // Set first editable node as active
        activeNodeId = puzzle.nodes().stream()
            .filter(n -> !n.locked())
            .map(Puzzle.NodeDef::id)
            .findFirst()
            .orElse(-1);
    }

    /** Main event loop. Returns true if puzzle was solved. */
    public boolean run() throws Exception {
        render();

        while (true) {
            KeyStroke key = screen.readInput();
            if (key == null) continue;

            if (key.getKeyType() == KeyType.Character) {
                char c = key.getCharacter();
                if (showHelp) {
                    showHelp = false; // any key dismisses help
                } else {
                    insertChar(c);
                }
            } else {
                if (showHelp) {
                    showHelp = false; // any key dismisses help
                } else {
                    switch (key.getKeyType()) {
                        case F5        -> { if (runPuzzle()) return true; }
                        case F1        -> showHelp = true;
                        case Escape    -> { return false; }
                        case Tab       -> switchNode();
                        case Enter     -> insertNewline();
                        case Backspace -> backspace();
                        case Delete    -> deleteChar();
                        case ArrowUp   -> moveCursor(-1);
                        case ArrowDown -> moveCursor(1);
                        default        -> {}
                    }
                }
            }
            render();
        }
    }

    private boolean runPuzzle() throws Exception {
        // Save current code
        saveCurrentCode();

        // Assemble and validate first
        Assembler assembler = new Assembler();
        for (Puzzle.NodeDef node : puzzle.nodes()) {
            if (!node.locked()) {
                String code = getCode(node.id());
                Assembler.AssemblyResult result = assembler.assemble(code);
                if (result.hasError()) {
                    statusMsg = result.error();
                    statusColor = TextColor.ANSI.RED;
                    render();
                    return false;
                }
            }
        }

        statusMsg = "RUNNING...";
        statusColor = TextColor.ANSI.YELLOW;
        render();

        // Build programs map
        Map<Integer, String> programs = new HashMap<>();
        for (Puzzle.NodeDef node : puzzle.nodes()) {
            if (!node.locked()) {
                programs.put(node.id(), getCode(node.id()));
            }
        }

        RunResult result = runner.runAll(puzzle, programs);
        lastActualOutput = result.actualOutput();
        lastCycles = result.totalCycles();

        if (result.passed()) {
            statusMsg = "PASS  (" + lastCycles + " cycles)";
            statusColor = TextColor.ANSI.GREEN_BRIGHT;
            saveState.markSolved(puzzle.id());
            saveState.save();
            render();
            // Brief pause before returning
            Thread.sleep(800);
            return true;
        } else if ("TIMEOUT".equals(result.status())) {
            statusMsg = "TIMEOUT — possible deadlock or infinite loop";
            statusColor = TextColor.ANSI.RED;
        } else if ("ERROR".equals(result.status())) {
            statusMsg = "ERROR: " + result.errorMessage();
            statusColor = TextColor.ANSI.RED;
        } else {
            statusMsg = "FAIL — output mismatch";
            statusColor = TextColor.ANSI.RED;
        }
        render();
        return false;
    }

    private void render() throws Exception {
        screen.clear();
        TextGraphics g = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();
        int totalWidth = size.getColumns();
        int totalHeight = size.getRows();

        // Compute node panel widths
        List<Puzzle.NodeDef> editableNodes = puzzle.nodes().stream()
            .filter(n -> !n.locked()).toList();
        List<Puzzle.NodeDef> allNodes = puzzle.nodes();
        int numNodes = allNodes.size();
        int nodesAreaWidth = totalWidth - BRIEFING_WIDTH - 2;
        int nodePanelW = numNodes > 0 ? Math.max(16, nodesAreaWidth / numNodes) : nodesAreaWidth;

        // Header
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        String header = " \u2593\u2593 DELTA-6 \u2593\u2593  [" + chapterTitle + "]";
        String puzzleTitle = puzzle.title();
        drawHBorder(g, 0, totalWidth, '\u2554', '\u2550', '\u2557');
        g.putString(1, 0, "\u2551" + padRight(header + "  " + puzzleTitle, totalWidth - 2) + "\u2551");

        // Draw node panels
        int nodeRow = 1;
        drawHBorder(g, nodeRow, totalWidth, '\u2560', '\u2550', '\u2563');
        // We'll draw content rows below

        int contentStartRow = nodeRow + 1;
        int contentHeight = totalHeight - contentStartRow - 3; // reserve space for status bar

        // Draw node column separators and content
        int x = 0;
        for (int ni = 0; ni < allNodes.size(); ni++) {
            Puzzle.NodeDef node = allNodes.get(ni);
            int panelRight = x + nodePanelW;

            // Column border on left
            for (int r = contentStartRow; r < contentStartRow + contentHeight; r++) {
                g.setForegroundColor(TextColor.ANSI.GREEN);
                g.putString(x, r, "\u2551");
            }

            // Node header
            boolean isActive = (node.id() == activeNodeId);
            g.setForegroundColor(isActive ? TextColor.ANSI.GREEN_BRIGHT : TextColor.ANSI.GREEN);
            String nodeLabel = "[NODE " + node.id() + (node.locked() ? " LOCKED" : isActive ? " EDIT" : "") + "]";
            g.putString(x + 1, contentStartRow, padRight(nodeLabel, nodePanelW - 1));

            // Editor lines
            if (node.locked()) {
                renderLockedNode(g, node, x + 1, contentStartRow + 1, nodePanelW - 1, contentHeight - 2);
            } else {
                renderEditableNode(g, node, x + 1, contentStartRow + 1, nodePanelW - 1, contentHeight - 2, isActive);
            }

            x += nodePanelW;
        }

        // Final node column right border
        for (int r = contentStartRow; r < contentStartRow + contentHeight; r++) {
            g.setForegroundColor(TextColor.ANSI.GREEN);
            g.putString(x, r, "\u2551");
        }

        // Briefing panel
        int bx = x;
        int briefingContentX = bx + 2;
        int briefingContentW = totalWidth - bx - 3;

        for (int r = contentStartRow; r < contentStartRow + contentHeight; r++) {
            g.setForegroundColor(TextColor.ANSI.GREEN);
            g.putString(totalWidth - 1, r, "\u2551");
        }

        renderBriefing(g, briefingContentX, contentStartRow, briefingContentW, contentHeight);

        // Bottom border
        int bottomBorderRow = totalHeight - 3;
        drawHBorder(g, bottomBorderRow, totalWidth, '\u2560', '\u2550', '\u2563');

        // Port visualization row
        renderPorts(g, 1, bottomBorderRow + 1, nodesAreaWidth);
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(totalWidth - 1, bottomBorderRow + 1, "\u2551");

        // Status bar
        drawHBorder(g, bottomBorderRow + 2, totalWidth, '\u2560', '\u2550', '\u2563');
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(1, bottomBorderRow + 3, "\u2551");
        g.setForegroundColor(TextColor.ANSI.GREEN);
        String controls = " [F5] Run  [Tab] Switch node  [F1] Help  [Esc] Back";
        String cyclesStr = lastCycles > 0 ? "  Cycles: " + lastCycles + "  " : "  ";
        g.putString(2, bottomBorderRow + 3, controls + cyclesStr);
        g.setForegroundColor(statusColor);
        g.putString(2 + controls.length() + cyclesStr.length(), bottomBorderRow + 3,
            padRight(statusMsg, totalWidth - 4 - controls.length() - cyclesStr.length()));
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(totalWidth - 1, bottomBorderRow + 3, "\u2551");

        // Final border
        drawHBorder(g, bottomBorderRow + 4, totalWidth, '\u255a', '\u2550', '\u255d');

        if (showHelp) renderHelpOverlay(g, totalWidth, totalHeight);

        screen.refresh();
    }

    private void renderEditableNode(TextGraphics g, Puzzle.NodeDef node,
                                    int x, int startRow, int width, int height, boolean isActive) {
        List<String> lines = nodeLines.get(node.id());
        int instrCount = countInstructions(lines);

        for (int i = 0; i < height; i++) {
            int lineIdx = scrollOffset + i;
            String lineStr;
            if (lineIdx < lines.size()) {
                lineStr = lines.get(lineIdx);
                // Highlight current cursor line
                boolean isCursorLine = isActive && lineIdx == cursorLine;
                g.setForegroundColor(isCursorLine ? TextColor.ANSI.WHITE : TextColor.ANSI.GREEN_BRIGHT);
                // Line number prefix
                String prefix = String.format("%02d ", lineIdx + 1);
                String display = prefix + padRight(lineStr, width - prefix.length());
                g.putString(x, startRow + i, display.substring(0, Math.min(display.length(), width)));
                if (isCursorLine) {
                    // Draw cursor at end of text
                    int cursorX = x + prefix.length() + lineStr.length();
                    if (cursorX < x + width) {
                        g.putString(cursorX, startRow + i, "_");
                    }
                }
            } else {
                g.setForegroundColor(TextColor.ANSI.GREEN);
                g.putString(x, startRow + i, padRight("", width));
            }
        }

        // Instruction counter at bottom of node panel
        if (height > 0) {
            g.setForegroundColor(instrCount > 20 ? TextColor.ANSI.RED : TextColor.ANSI.GREEN);
            String counter = instrCount + "/20 instr";
            g.putString(x, startRow + height - 1, padRight(counter, width));
        }
    }

    private void renderLockedNode(TextGraphics g, Puzzle.NodeDef node,
                                   int x, int startRow, int width, int height) {
        g.setForegroundColor(new TextColor.RGB(80, 120, 80)); // dim green
        String[] lines = (node.lockedCode() != null ? node.lockedCode() : "").split("\n");
        for (int i = 0; i < height && i < lines.length; i++) {
            String prefix = String.format("%02d ", i + 1);
            String display = prefix + lines[i];
            g.putString(x, startRow + i, padRight(display, width).substring(0, Math.min(display.length(), width)));
        }
    }

    private void renderBriefing(TextGraphics g, int x, int startRow, int width, int height) {
        int row = startRow;

        // Briefing section header
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(x, row++, padRight("MISSION BRIEFING", width));
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(x, row++, padRight("\u2500".repeat(Math.min(width, 24)), width));

        // Briefing text
        String[] briefingLines = wordWrap(puzzle.briefing(), width);
        for (String line : briefingLines) {
            if (row >= startRow + height - 8) break;
            g.putString(x, row++, padRight(line, width));
        }

        row++;

        // Test vectors
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(x, row++, padRight("\u2500\u2500\u2500\u2500 TEST VECTORS \u2500\u2500\u2500\u2500\u2500", width));

        Puzzle.TestVector vector = puzzle.testVectors().get(0);
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(x, row++, padRight("IN  : " + formatValues(vector.input()), width));

        String gotStr = lastActualOutput.isEmpty() ? "" : formatValues(lastActualOutput);
        String expStr = formatValues(vector.output());
        g.setForegroundColor(lastActualOutput.equals(vector.output()) && !lastActualOutput.isEmpty()
            ? TextColor.ANSI.GREEN_BRIGHT : TextColor.ANSI.GREEN);
        g.putString(x, row++, padRight("GOT : " + gotStr, width));
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(x, row++, padRight("EXP : " + expStr, width));

        // Log section
        if (puzzle.logText() != null) {
            row++;
            g.setForegroundColor(new TextColor.RGB(80, 120, 80));
            g.putString(x, row++, padRight("\u2500\u2500 SYSTEM LOG \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", width));
            for (String line : puzzle.logText().trim().split("\n")) {
                if (row >= startRow + height - 1) break;
                g.putString(x, row++, padRight(line.trim(), width));
            }
        }
    }

    private void renderPorts(TextGraphics g, int x, int row, int width) {
        g.setForegroundColor(TextColor.ANSI.GREEN);
        StringBuilder portInfo = new StringBuilder("\u2551 ");
        for (Puzzle.PortDef port : puzzle.ports()) {
            portInfo.append("N").append(port.fromNode())
                .append(" \u2192 N").append(port.toNode())
                .append(": $F").append(port.direction().ordinal())
                .append("  ");
        }
        if (puzzle.ports().isEmpty()) {
            portInfo.append("(no inter-node ports)");
        }
        g.putString(x, row, padRight(portInfo.toString(), width));
    }

    private void renderHelpOverlay(TextGraphics g, int totalWidth, int totalHeight) {
        int w = 56;
        int h = 28;
        int x = (totalWidth - w) / 2;
        int y = (totalHeight - h) / 2;

        // Shadow
        g.setForegroundColor(TextColor.ANSI.BLACK);
        for (int r = y + 1; r < y + h + 1; r++)
            g.putString(x + 2, r, " ".repeat(w));

        // Box background
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(x, y, "\u2554" + "\u2550".repeat(w - 2) + "\u2557");
        String title = "  DELTA-6 OPERATOR REFERENCE  ";
        g.putString(x, y + 1, "\u2551" + padRight(title, w - 2) + "\u2551");
        g.putString(x, y + 2, "\u2560" + "\u2550".repeat(w - 2) + "\u2563");
        for (int r = y + 3; r < y + h - 1; r++)
            g.putString(x, r, "\u2551" + " ".repeat(w - 2) + "\u2551");
        g.putString(x, y + h - 1, "\u255a" + "\u2550".repeat(w - 2) + "\u255d");

        // Content
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        int cx = x + 2;
        int row = y + 3;
        g.putString(cx, row++, "CONTROLS");
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(cx, row++, "  F5          Run program against test vectors");
        g.putString(cx, row++, "  Tab         Switch to next editable node");
        g.putString(cx, row++, "  Esc         Return to mission select");
        g.putString(cx, row++, "  F1          Toggle this help screen");
        g.putString(cx, row++, "  Arrow keys  Navigate editor lines");
        g.putString(cx, row++, "  Enter       New line");
        g.putString(cx, row++, "  Backspace   Delete character");
        row++;
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(cx, row++, "SPECIAL ADDRESSES");
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(cx, row++, "  $FE         Read next value from puzzle input");
        g.putString(cx, row++, "  $FF         Write value to puzzle output");
        g.putString(cx, row++, "  $F0         LEFT port  (blocks until neighbor writes)");
        g.putString(cx, row++, "  $F1         RIGHT port");
        g.putString(cx, row++, "  $F2         UP port");
        g.putString(cx, row++, "  $F3         DOWN port");
        row++;
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(cx, row++, "USEFUL PATTERNS");
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(cx, row++, "  loop: LDA $FE / ... / JMP loop   process stream");
        g.putString(cx, row++, "  CLC / LDA x / ASL A              double a value");
        g.putString(cx, row++, "  CMP #N / BCC done / SBC #N       divide by N");
        g.putString(cx, row++, "  BEQ / BMI / BPL / BNE            conditional flow");
        row++;
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(cx, row++, "CONSTRAINTS");
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(cx, row++, "  Max 20 instructions per node  |  Carry = 0 at start");

        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(x + 2, y + h - 2, "[ PRESS ANY KEY TO CLOSE ]");
    }

    private void drawHBorder(TextGraphics g, int row, int totalWidth,
                              char left, char fill, char right) {
        g.setForegroundColor(TextColor.ANSI.GREEN);
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 1; i < totalWidth - 1; i++) sb.append(fill);
        sb.append(right);
        g.putString(0, row, sb.toString());
    }

    // ---- Editor operations ----

    private void insertChar(char c) {
        if (activeNodeId < 0) return;
        List<String> lines = nodeLines.get(activeNodeId);
        ensureLine(lines, cursorLine);
        String line = lines.get(cursorLine);
        lines.set(cursorLine, line + c);
        saveCurrentCode();
    }

    private void insertNewline() {
        if (activeNodeId < 0) return;
        List<String> lines = nodeLines.get(activeNodeId);
        ensureLine(lines, cursorLine);
        lines.add(cursorLine + 1, "");
        cursorLine++;
        scrollIfNeeded();
        saveCurrentCode();
    }

    private void backspace() {
        if (activeNodeId < 0) return;
        List<String> lines = nodeLines.get(activeNodeId);
        if (cursorLine >= lines.size()) return;
        String line = lines.get(cursorLine);
        if (!line.isEmpty()) {
            lines.set(cursorLine, line.substring(0, line.length() - 1));
        } else if (cursorLine > 0) {
            lines.remove(cursorLine);
            cursorLine--;
        }
        saveCurrentCode();
    }

    private void deleteChar() {
        backspace(); // simplified: delete = backspace
    }

    private void moveCursor(int delta) {
        if (activeNodeId < 0) return;
        List<String> lines = nodeLines.get(activeNodeId);
        cursorLine = Math.max(0, Math.min(lines.size() - 1, cursorLine + delta));
        scrollIfNeeded();
    }

    private void switchNode() {
        List<Integer> editableIds = puzzle.nodes().stream()
            .filter(n -> !n.locked())
            .map(Puzzle.NodeDef::id)
            .toList();
        if (editableIds.isEmpty()) return;
        int idx = editableIds.indexOf(activeNodeId);
        activeNodeId = editableIds.get((idx + 1) % editableIds.size());
        cursorLine = 0;
        scrollOffset = 0;
    }

    private void scrollIfNeeded() {
        TerminalSize size = screen.getTerminalSize();
        int visibleLines = size.getRows() - 8;
        if (cursorLine < scrollOffset) scrollOffset = cursorLine;
        if (cursorLine >= scrollOffset + visibleLines) scrollOffset = cursorLine - visibleLines + 1;
    }

    private void ensureLine(List<String> lines, int idx) {
        while (lines.size() <= idx) lines.add("");
    }

    private void saveCurrentCode() {
        for (Puzzle.NodeDef node : puzzle.nodes()) {
            if (!node.locked()) {
                List<String> lines = nodeLines.get(node.id());
                saveState.setCode(puzzle.id(), node.id(), String.join("\n", lines));
            }
        }
        saveState.save();
    }

    private String getCode(int nodeId) {
        List<String> lines = nodeLines.get(nodeId);
        return lines != null ? String.join("\n", lines) : "";
    }

    private int countInstructions(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            String stripped = line.trim();
            if (stripped.isEmpty() || stripped.startsWith(";")) continue;
            // Strip label prefix
            if (stripped.contains(":")) {
                String afterColon = stripped.substring(stripped.indexOf(':') + 1).trim();
                if (afterColon.isEmpty()) continue; // label-only line
                stripped = afterColon;
            }
            if (!stripped.isEmpty()) count++;
        }
        return count;
    }

    private String formatValues(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append("  ");
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private String[] wordWrap(String text, int width) {
        if (text == null) return new String[0];
        List<String> result = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            if (paragraph.isBlank()) { result.add(""); continue; }
            String[] words = paragraph.trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                if (line.length() + word.length() + 1 > width) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    if (!line.isEmpty()) line.append(' ');
                    line.append(word);
                }
            }
            if (!line.isEmpty()) result.add(line.toString());
        }
        return result.toArray(new String[0]);
    }

    private String padRight(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }
}
