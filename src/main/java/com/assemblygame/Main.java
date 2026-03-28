package com.assemblygame;

import com.assemblygame.game.*;
import com.assemblygame.ui.*;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

/**
 * Entry point. Sets up the terminal, shows the boot screen, then drives the game loop.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // Load puzzle library (validates all content at startup)
        PuzzleLibrary library = new PuzzleLibrary();
        try {
            library.load();
        } catch (Exception e) {
            System.err.println("FATAL: Failed to load puzzle library: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        SaveState saveState = SaveState.load();
        PuzzleRunner runner = new PuzzleRunner();

        // Set up terminal and screen
        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();

        try {
            // Boot sequence
            new BootScreen(screen).show();

            // Main game loop
            gameLoop(screen, library, saveState, runner);

        } finally {
            screen.stopScreen();
        }
    }

    private static void gameLoop(Screen screen, PuzzleLibrary library,
                                  SaveState saveState, PuzzleRunner runner) throws Exception {
        StoryScreen storyScreen = new StoryScreen(screen);
        int lastChapterShown = -1;

        while (true) {
            // Show puzzle selection menu
            PuzzleSelectScreen selector = new PuzzleSelectScreen(screen, library, saveState);
            int selectedIdx = selector.show();

            if (selectedIdx < 0) break; // Quit

            saveState.currentPuzzleIndex = selectedIdx;
            saveState.save();

            Puzzle puzzle = library.get(selectedIdx);

            // Show chapter intro if entering a new chapter
            int chapterIdx = findChapterIndex(library, puzzle);
            if (chapterIdx != lastChapterShown) {
                PuzzleLibrary.Chapter chapter = library.chapters().get(chapterIdx);
                // Show story for first puzzle of chapter
                if (chapter.puzzles().get(0).id().equals(puzzle.id())) {
                    storyScreen.show(chapter, getChapterIntro(chapter));
                    lastChapterShown = chapterIdx;
                }
            }

            // Get chapter title for header display
            String chapterTitle = "CH." + (chapterIdx + 1) + ": " +
                library.chapters().get(chapterIdx).title();

            // Run the puzzle view
            PuzzleView view = new PuzzleView(screen, puzzle, saveState, runner, chapterTitle);
            boolean solved = view.run();

            if (solved) {
                // Advance to next puzzle
                if (selectedIdx + 1 < library.size()) {
                    saveState.currentPuzzleIndex = selectedIdx + 1;
                    saveState.save();
                }
            }
        }
    }

    private static int findChapterIndex(PuzzleLibrary library, Puzzle puzzle) {
        for (int i = 0; i < library.chapters().size(); i++) {
            for (Puzzle p : library.chapters().get(i).puzzles()) {
                if (p.id().equals(puzzle.id())) return i;
            }
        }
        return 0;
    }

    private static String getChapterIntro(PuzzleLibrary.Chapter chapter) {
        return switch (chapter.title()) {
            case "BOOT SEQUENCE" ->
                "You found it.\n\n" +
                "Buried under three meters of concrete, the terminal\n" +
                "still has power. The disk is intact.\n\n" +
                "DELTA-6 is in there — scattered, corrupted, waiting.\n\n" +
                "Start with the basics. Get the signal flowing.";
            case "COMMS ARRAY" ->
                "The comms array is alive, but broadcasting garbage.\n\n" +
                "Dead zones are injecting null values into the data\n" +
                "stream. You need to clean the signal before anything\n" +
                "downstream can make sense of it.";
            case "LIFE SUPPORT" ->
                "Sector 7 life support is still drawing power.\n\n" +
                "The telemetry is scrambled — raw values pouring in,\n" +
                "no context, no calibration. The display unit needs\n" +
                "processed data, not noise.\n\n" +
                "Figure out what the sensors are actually saying.";
            case "VAULT CONTROL" ->
                "There is a vault.\n\n" +
                "None of the original project documents mentioned it.\n" +
                "DELTA-6 was built to control something down here —\n" +
                "something that needed to stay locked.\n\n" +
                "The sequencer requires sorted input. Get it right.";
            default -> "Subsystem online. Program it.";
        };
    }
}
