package com.assemblygame.ui;

import com.assemblygame.game.PuzzleLibrary;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.TerminalSize;

/**
 * Full-screen chapter title card with typewriter-effect story text.
 */
public class StoryScreen {

    private final Screen screen;

    public StoryScreen(Screen screen) {
        this.screen = screen;
    }

    public void show(PuzzleLibrary.Chapter chapter, String storyText) throws Exception {
        screen.clear();
        TextGraphics g = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();
        int width = size.getColumns();

        // Chapter title card
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        String title = "  CHAPTER " + (chapter.id() + 1) + ": " + chapter.title() + "  ";
        int titleRow = 3;
        g.putString(2, titleRow, AsciiArt.CHAPTER_TITLE_TOP);
        g.putString(2, titleRow + 1, "║  " + padRight(title, 36) + "║");
        g.putString(2, titleRow + 2, AsciiArt.CHAPTER_TITLE_BOTTOM);

        screen.refresh();
        Thread.sleep(600);

        // Typewriter story text
        if (storyText != null && !storyText.isBlank()) {
            g.setForegroundColor(TextColor.ANSI.GREEN);
            int row = titleRow + 5;
            for (String line : storyText.split("\n")) {
                if (row >= size.getRows() - 3) break;
                g.putString(4, row, line);
                screen.refresh();
                Thread.sleep(line.isBlank() ? 100 : 60);
                row++;
            }
        }

        // Prompt
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(4, size.getRows() - 2, "[ PRESS ANY KEY TO CONTINUE ]");
        screen.refresh();

        // Wait for keypress
        while (true) {
            KeyStroke key = screen.readInput();
            if (key != null && key.getKeyType() != KeyType.Unknown) break;
        }
    }

    private String padRight(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }
}
