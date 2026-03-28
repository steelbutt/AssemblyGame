package com.assemblygame.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.Terminal;

/**
 * Animated boot sequence displayed on game start.
 * Prints lines one at a time with a short delay, then waits for keypress.
 */
public class BootScreen {

    private final Screen screen;

    public BootScreen(Screen screen) {
        this.screen = screen;
    }

    public void show() throws Exception {
        screen.clear();
        TextGraphics g = screen.newTextGraphics();

        // Show logo
        g.setForegroundColor(TextColor.ANSI.GREEN);
        String[] logoLines = AsciiArt.DELTA6_LOGO.split("\n");
        int startRow = 1;
        for (int i = 0; i < logoLines.length; i++) {
            g.putString(2, startRow + i, logoLines[i]);
        }
        screen.refresh();
        Thread.sleep(400);

        // Animate boot lines
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        int row = startRow + logoLines.length + 2;
        for (String line : AsciiArt.BOOT_LINES) {
            g.putString(4, row, line);
            screen.refresh();
            // Pause longer on FAULT lines for drama
            if (line.contains("FAULT")) {
                Thread.sleep(500);
            } else if (line.contains("LOADING KERNEL")) {
                Thread.sleep(600);
            } else {
                Thread.sleep(120);
            }
            row++;
        }

        screen.refresh();

        // Wait for keypress
        while (true) {
            KeyStroke key = screen.readInput();
            if (key != null && key.getKeyType() != KeyType.Unknown) break;
        }
    }
}
