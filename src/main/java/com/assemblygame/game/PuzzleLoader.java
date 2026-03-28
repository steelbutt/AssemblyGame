package com.assemblygame.game;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads Puzzle instances from YAML resource files.
 * Also loads accompanying story text from the story resource directory.
 */
public class PuzzleLoader {

    private final Yaml yaml = new Yaml();

    public Puzzle load(String resourcePath) {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) throw new RuntimeException("Puzzle resource not found: " + resourcePath);

        Map<String, Object> data = yaml.load(is);

        String id = (String) data.get("id");
        int chapter = (Integer) data.get("chapter");
        String title = (String) data.get("title");
        String storyFile = (String) data.get("story_file");
        int maxCycles = data.containsKey("max_cycles") ? (Integer) data.get("max_cycles") : 1000;

        Map<String, Object> grid = (Map<String, Object>) data.get("grid");
        int rows = (Integer) grid.get("rows");
        int cols = (Integer) grid.get("cols");

        List<Puzzle.NodeDef> nodes = parseNodes((List<Map<String, Object>>) data.get("nodes"));
        List<Puzzle.PortDef> ports = parsePorts((List<Map<String, Object>>) data.getOrDefault("ports", List.of()));
        List<Puzzle.TestVector> testVectors = parseVectors((List<Map<String, Object>>) data.get("test_vectors"));

        String[] story = loadStory(storyFile);
        String briefing = story[0];
        String logText = story[1];

        return new Puzzle(id, chapter, title, briefing, logText, rows, cols, nodes, ports, testVectors, maxCycles);
    }

    private List<Puzzle.NodeDef> parseNodes(List<Map<String, Object>> rawNodes) {
        List<Puzzle.NodeDef> result = new ArrayList<>();
        for (Map<String, Object> n : rawNodes) {
            int id = (Integer) n.get("id");
            int row = (Integer) n.get("row");
            int col = (Integer) n.get("col");
            boolean locked = Boolean.TRUE.equals(n.get("locked"));
            String code = locked ? (String) n.getOrDefault("code", "") : null;
            result.add(new Puzzle.NodeDef(id, row, col, locked, code));
        }
        return result;
    }

    private List<Puzzle.PortDef> parsePorts(List<Map<String, Object>> rawPorts) {
        List<Puzzle.PortDef> result = new ArrayList<>();
        for (Map<String, Object> p : rawPorts) {
            int from = (Integer) p.get("from_node");
            int to = (Integer) p.get("to_node");
            Puzzle.PortDirection dir = Puzzle.PortDirection.valueOf((String) p.get("direction"));
            result.add(new Puzzle.PortDef(from, to, dir));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Puzzle.TestVector> parseVectors(List<Map<String, Object>> rawVectors) {
        List<Puzzle.TestVector> result = new ArrayList<>();
        for (Map<String, Object> v : rawVectors) {
            List<Integer> input = toIntList(v.get("input"));
            List<Integer> output = toIntList(v.get("output"));
            result.add(new Puzzle.TestVector(input, output));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> toIntList(Object obj) {
        List<?> raw = (List<?>) obj;
        List<Integer> result = new ArrayList<>();
        for (Object item : raw) {
            result.add(((Number) item).intValue());
        }
        return result;
    }

    /** Returns [briefing, logText] from a story text file. logText may be null. */
    private String[] loadStory(String storyFile) {
        if (storyFile == null) return new String[]{"No briefing available.", null};
        InputStream is = getClass().getResourceAsStream("/story/" + storyFile);
        if (is == null) return new String[]{"No briefing available.", null};

        try {
            String content = new String(is.readAllBytes());
            String briefing = extractSection(content, "BRIEFING");
            String log = extractSection(content, "LOG");
            return new String[]{briefing != null ? briefing.trim() : "No briefing.", log};
        } catch (Exception e) {
            return new String[]{"Error loading briefing.", null};
        }
    }

    private String extractSection(String content, String sectionName) {
        String marker = "[" + sectionName + "]";
        int start = content.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = content.indexOf("[", start);
        return end < 0 ? content.substring(start) : content.substring(start, end);
    }
}
