package com.assemblygame.game;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads chapters.yaml and loads all puzzles in order.
 * Validates all puzzles at startup and reports errors immediately.
 */
public class PuzzleLibrary {

    private final List<Puzzle> puzzles = new ArrayList<>();
    private final Map<String, Integer> idToIndex = new LinkedHashMap<>();
    private final List<Chapter> chapters = new ArrayList<>();

    public record Chapter(int id, String title, List<Puzzle> puzzles) {}

    public void load() {
        InputStream is = getClass().getResourceAsStream("/puzzles/chapters.yaml");
        if (is == null) throw new RuntimeException("chapters.yaml not found in resources/puzzles/");

        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(is);

        PuzzleLoader loader = new PuzzleLoader();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawChapters = (List<Map<String, Object>>) data.get("chapters");

        for (Map<String, Object> rawChapter : rawChapters) {
            int chapterId = (Integer) rawChapter.get("id");
            String chapterTitle = (String) rawChapter.get("title");

            @SuppressWarnings("unchecked")
            List<String> puzzleIds = (List<String>) rawChapter.get("puzzles");

            List<Puzzle> chapterPuzzles = new ArrayList<>();
            for (String puzzleFile : puzzleIds) {
                String resourcePath = "/puzzles/" + puzzleFile + ".yaml";
                Puzzle puzzle = loader.load(resourcePath);
                puzzles.add(puzzle);
                idToIndex.put(puzzle.id(), puzzles.size() - 1);
                chapterPuzzles.add(puzzle);
            }
            chapters.add(new Chapter(chapterId, chapterTitle, chapterPuzzles));
        }
    }

    public List<Puzzle> allPuzzles()  { return List.copyOf(puzzles); }
    public List<Chapter> chapters()   { return List.copyOf(chapters); }
    public int size()                  { return puzzles.size(); }

    public Puzzle get(int index) {
        return puzzles.get(index);
    }

    public Puzzle getById(String id) {
        Integer index = idToIndex.get(id);
        if (index == null) throw new IllegalArgumentException("Unknown puzzle id: " + id);
        return puzzles.get(index);
    }

    public int indexOf(Puzzle puzzle) {
        return idToIndex.getOrDefault(puzzle.id(), -1);
    }
}
