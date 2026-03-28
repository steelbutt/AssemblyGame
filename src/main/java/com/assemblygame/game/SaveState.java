package com.assemblygame.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists player progress and written programs to ~/.assemblygame/save.json.
 */
public class SaveState {

    private static final Path SAVE_DIR  = Path.of(System.getProperty("user.home"), ".assemblygame");
    private static final Path SAVE_FILE = SAVE_DIR.resolve("save.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // currentPuzzleIndex: index into PuzzleLibrary.allPuzzles()
    public int currentPuzzleIndex = 0;

    // playerCode: puzzleId -> nodeId -> source code
    public Map<String, Map<Integer, String>> playerCode = new HashMap<>();

    // solvedPuzzles: set of puzzle ids the player has passed
    public Map<String, Boolean> solvedPuzzles = new HashMap<>();

    public static SaveState load() {
        if (!Files.exists(SAVE_FILE)) return new SaveState();
        try (Reader reader = Files.newBufferedReader(SAVE_FILE)) {
            return GSON.fromJson(reader, SaveState.class);
        } catch (Exception e) {
            System.err.println("Warning: could not load save file, starting fresh. (" + e.getMessage() + ")");
            return new SaveState();
        }
    }

    public void save() {
        try {
            Files.createDirectories(SAVE_DIR);
            try (Writer writer = Files.newBufferedWriter(SAVE_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not save progress. (" + e.getMessage() + ")");
        }
    }

    public String getCode(String puzzleId, int nodeId) {
        Map<Integer, String> byNode = playerCode.get(puzzleId);
        return byNode != null ? byNode.getOrDefault(nodeId, "") : "";
    }

    public void setCode(String puzzleId, int nodeId, String code) {
        playerCode.computeIfAbsent(puzzleId, k -> new HashMap<>()).put(nodeId, code);
    }

    public Map<Integer, String> getAllCode(String puzzleId) {
        return playerCode.getOrDefault(puzzleId, new HashMap<>());
    }

    public boolean isSolved(String puzzleId) {
        return Boolean.TRUE.equals(solvedPuzzles.get(puzzleId));
    }

    public void markSolved(String puzzleId) {
        solvedPuzzles.put(puzzleId, true);
    }
}
