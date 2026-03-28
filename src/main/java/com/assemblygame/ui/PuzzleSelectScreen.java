package com.assemblygame.ui;

import com.assemblygame.game.Puzzle;
import com.assemblygame.game.PuzzleLibrary;
import com.assemblygame.game.SaveState;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.TerminalSize;

/**
 * Puzzle selection menu showing all chapters and puzzles.
 * Solved puzzles are marked with [✓]. The next unsolved puzzle is highlighted.
 */
public class PuzzleSelectScreen {

    private final Screen screen;
    private final PuzzleLibrary library;
    private final SaveState saveState;

    private int selectedIndex;

    public PuzzleSelectScreen(Screen screen, PuzzleLibrary library, SaveState saveState) {
        this.screen = screen;
        this.library = library;
        this.saveState = saveState;
        this.selectedIndex = saveState.currentPuzzleIndex;
    }

    /** Returns the selected puzzle index, or -1 to quit. */
    public int show() throws Exception {
        render();
        while (true) {
            KeyStroke key = screen.readInput();
            if (key == null) continue;

            if (key.getKeyType() == KeyType.Character) {
                char c = key.getCharacter();
                if (c == 'q' || c == 'Q') return -1;
                if (c == '\n' || c == '\r') return selectedIndex;
            }
            switch (key.getKeyType()) {
                case Enter     -> { return selectedIndex; }
                case ArrowUp   -> selectedIndex = Math.max(0, selectedIndex - 1);
                case ArrowDown -> selectedIndex = Math.min(library.size() - 1, selectedIndex + 1);
                case Escape    -> { return -1; }
                default        -> {}
            }
            render();
        }
    }

    private void render() throws Exception {
        screen.clear();
        TextGraphics g = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();

        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        String[] logoLines = AsciiArt.DELTA6_LOGO.split("\n");
        for (int i = 0; i < logoLines.length; i++) {
            g.putString(2, 1 + i, logoLines[i]);
        }

        int row = 1 + logoLines.length + 2;
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(4, row++, "SELECT MISSION:");
        g.putString(4, row++, AsciiArt.SEPARATOR_LIGHT.substring(0, Math.min(50, size.getColumns() - 8)));

        int puzzleIdx = 0;
        for (PuzzleLibrary.Chapter chapter : library.chapters()) {
            row++;
            g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
            g.putString(4, row++, "  CHAPTER " + (chapter.id() + 1) + ": " + chapter.title());

            for (Puzzle puzzle : chapter.puzzles()) {
                boolean isSelected = (puzzleIdx == selectedIndex);
                boolean isSolved = saveState.isSolved(puzzle.id());

                String marker = isSolved ? "[OK] " : "[ ]  ";
                String prefix = isSelected ? " >> " : "     ";

                g.setForegroundColor(isSelected ? TextColor.ANSI.WHITE :
                    isSolved ? TextColor.ANSI.GREEN : new TextColor.RGB(80, 120, 80));
                g.putString(4, row++, prefix + marker + puzzle.title());
                puzzleIdx++;
            }
        }

        row += 2;
        g.setForegroundColor(TextColor.ANSI.GREEN);
        g.putString(4, row, "[Enter] Select   [↑/↓] Navigate   [Q / Esc] Quit");

        screen.refresh();
    }
}
