package com.assemblygame.game;

import com.assemblygame.core.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates execution of all nodes for a puzzle.
 * Each node runs on a Java virtual thread and communicates via SynchronousQueues (ports).
 * A watchdog thread kills all nodes if max_cycles is exceeded.
 */
public class PuzzleRunner {

    /**
     * Run a puzzle with the given player programs (indexed by node id).
     * Locked nodes use their built-in code; playerPrograms provides code for editing nodes.
     */
    public RunResult run(Puzzle puzzle, Map<Integer, String> playerPrograms) {
        return run(puzzle, playerPrograms, puzzle.testVectors().get(0));
    }

    /**
     * Run all test vectors and return the first failure, or PASS if all pass.
     */
    public RunResult runAll(Puzzle puzzle, Map<Integer, String> playerPrograms) {
        for (Puzzle.TestVector vector : puzzle.testVectors()) {
            RunResult result = run(puzzle, playerPrograms, vector);
            if (!result.passed()) return result;
        }
        // Return a PASS with the last vector's expected output
        Puzzle.TestVector last = puzzle.testVectors().getLast();
        return RunResult.pass(last.output(), 0);
    }

    private RunResult run(Puzzle puzzle, Map<Integer, String> playerPrograms, Puzzle.TestVector vector) {
        Assembler assembler = new Assembler();

        // Assemble all programs
        Map<Integer, List<Assembler.Instruction>> programs = new HashMap<>();
        for (Puzzle.NodeDef node : puzzle.nodes()) {
            String source = node.locked()
                ? node.lockedCode()
                : playerPrograms.getOrDefault(node.id(), "");
            if (source == null || source.isBlank()) {
                // Empty node — just BRK
                source = "BRK";
            }
            Assembler.AssemblyResult result = assembler.assemble(source);
            if (result.hasError()) {
                return RunResult.error("Node " + node.id() + ": " + result.error());
            }
            programs.put(node.id(), result.instructions());
        }

        // Build port queues: one SynchronousQueue per directed port pair
        Map<String, SynchronousQueue<Integer>> portQueues = new HashMap<>();
        for (Puzzle.PortDef port : puzzle.ports()) {
            String key = port.fromNode() + "->" + port.toNode();
            portQueues.put(key, new SynchronousQueue<>());
        }

        // Build output collector and memories
        List<Integer> outputCollector = Collections.synchronizedList(new ArrayList<>());
        Map<Integer, Memory> memories = new HashMap<>();
        Map<Integer, NodeExecutor> executors = new HashMap<>();

        for (Puzzle.NodeDef nodeDef : puzzle.nodes()) {
            Memory mem = new Memory(outputCollector);
            mem.setInputStream(new ArrayList<>(vector.input()).iterator());

            // Wire up ports
            for (Puzzle.PortDef port : puzzle.ports()) {
                SynchronousQueue<Integer> q = portQueues.get(port.fromNode() + "->" + port.toNode());
                // The "from" node writes to its directional port; "to" node reads from opposite
                if (port.fromNode() == nodeDef.id()) {
                    assignWriteQueue(mem, port.direction(), q);
                }
                if (port.toNode() == nodeDef.id()) {
                    assignReadQueue(mem, opposite(port.direction()), q);
                }
            }

            memories.put(nodeDef.id(), mem);
            executors.put(nodeDef.id(), new NodeExecutor(nodeDef.id(), programs.get(nodeDef.id()), mem));
        }

        // Run all nodes on virtual threads
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();
        for (NodeExecutor ne : executors.values()) {
            futures.add(executor.submit(ne));
        }

        // Watchdog: wait for expected output count OR timeout
        int expectedCount = vector.output().size();
        long deadline = System.currentTimeMillis() + estimateTimeout(puzzle.maxCycles());
        boolean timedOut = false;

        while (outputCollector.size() < expectedCount && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }

        if (outputCollector.size() < expectedCount) {
            timedOut = true;
        }

        // Stop all nodes
        executor.shutdownNow();
        for (NodeExecutor ne : executors.values()) ne.stop();
        memories.values().forEach(Memory::interrupt);

        try { executor.awaitTermination(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        int totalCycles = executors.values().stream().mapToInt(NodeExecutor::getCycleCount).sum();

        // Check for execution errors
        for (NodeExecutor ne : executors.values()) {
            if (ne.getExecutionError() != null) {
                Throwable err = ne.getExecutionError();
                if (!(err instanceof Memory.ExecutionInterruptedException)) {
                    return RunResult.error("Node " + ne.getNodeId() + " error: " + err.getMessage());
                }
            }
        }

        if (timedOut) {
            return RunResult.timeout(new ArrayList<>(outputCollector), vector.output());
        }

        // Compare output
        List<Integer> actual = new ArrayList<>(outputCollector).subList(0, Math.min(outputCollector.size(), expectedCount));
        if (actual.equals(vector.output())) {
            return RunResult.pass(actual, totalCycles);
        } else {
            return RunResult.fail(actual, vector.output(), totalCycles);
        }
    }

    private void assignWriteQueue(Memory mem, Puzzle.PortDirection dir, SynchronousQueue<Integer> q) {
        switch (dir) {
            case LEFT  -> mem.setLeftQueue(q);
            case RIGHT -> mem.setRightQueue(q);
            case UP    -> mem.setUpQueue(q);
            case DOWN  -> mem.setDownQueue(q);
        }
    }

    private void assignReadQueue(Memory mem, Puzzle.PortDirection dir, SynchronousQueue<Integer> q) {
        switch (dir) {
            case LEFT  -> mem.setLeftQueue(q);
            case RIGHT -> mem.setRightQueue(q);
            case UP    -> mem.setUpQueue(q);
            case DOWN  -> mem.setDownQueue(q);
        }
    }

    private Puzzle.PortDirection opposite(Puzzle.PortDirection dir) {
        return switch (dir) {
            case LEFT  -> Puzzle.PortDirection.RIGHT;
            case RIGHT -> Puzzle.PortDirection.LEFT;
            case UP    -> Puzzle.PortDirection.DOWN;
            case DOWN  -> Puzzle.PortDirection.UP;
        };
    }

    private long estimateTimeout(int maxCycles) {
        // ~1ms per cycle + 2 second base; generous for blocking port ops
        return 2000L + maxCycles;
    }
}
