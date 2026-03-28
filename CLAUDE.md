# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn test              # run all tests
mvn test -Dtest=AssemblerTest   # run a single test class
mvn test -Dtest=PuzzleRunnerTest#tutorialDoubler_correctSolution  # run one test method
mvn compile           # compile without running tests
mvn package -DskipTests  # build fat JAR at target/assemblygame.jar
java -jar target/assemblygame.jar  # run the game
mvn exec:java         # run in-process (less correct terminal handling)
```

> **Note:** The project targets Java 21 but runs on Java 25 (only version installed). Maven 3.9.x handles Java 25 fine.

## Architecture

The game is a TIS-100-style puzzle game where players write simplified 6502 assembly to solve puzzles across a grid of CPU nodes.

### Core VM (`src/main/java/com/assemblygame/core/`)

- **`Opcode.java`** — enum where each constant implements its own `execute(Cpu, Memory, operand, mode)`. All opcode logic lives here.
- **`Cpu.java`** — register state (A, X, Y, SP, PC) and flags (N, Z, C, V). PC is an *instruction index*, not a byte address — no bytecode, just a `List<Instruction>`.
- **`Memory.java`** — 256-byte zero page. Addresses `$F0–$F3` are blocking ports (via `SynchronousQueue`), `$FE` reads from the puzzle input stream, `$FF` writes to the output collector. `$FB` is a scratch address used internally for immediate-mode operands.
- **`Assembler.java`** — single-pass text → `List<Instruction>`. Labels are resolved to instruction indices. The 20-instruction limit is enforced here. Branch/JMP operands become absolute instruction indices.
- **`NodeExecutor.java`** — runs one node on a virtual thread. Supports step mode (pauses after each instruction).

### Game Layer (`src/main/java/com/assemblygame/game/`)

- **`Puzzle.java`** — immutable record loaded from YAML. Contains `NodeDef`, `PortDef`, `TestVector` records.
- **`PuzzleLoader.java`** — YAML → `Puzzle`. Story text is loaded from `/story/<file>.txt` by section markers `[BRIEFING]` and `[LOG]`.
- **`PuzzleRunner.java`** — assembles programs, wires `SynchronousQueue` port pairs between nodes, runs all nodes on virtual threads, watches for expected output count with a timeout, returns `RunResult`.
- **`PuzzleLibrary.java`** — reads `chapters.yaml`, loads all puzzles in chapter order. Call `library.load()` once at startup.
- **`SaveState.java`** — JSON at `~/.assemblygame/save.json` via Gson. Stores player code per (puzzleId, nodeId) and solved status.

### UI Layer (`src/main/java/com/assemblygame/ui/`)

All UI uses Lanterna 3.x for terminal rendering. No Lanterna windows/dialogs — all rendering is done with `screen.newTextGraphics()` and direct coordinate writes.

- **`BootScreen`** — animated boot sequence on launch.
- **`PuzzleSelectScreen`** — chapter/puzzle menu with keyboard navigation.
- **`StoryScreen`** — full-screen chapter title + typewriter story text.
- **`PuzzleView`** — the main puzzle editing screen. Renders node panels (editable + locked), briefing, test vectors, and port map. Key bindings: `R` = run, `Tab` = switch node, `Q` = quit.
- **`AsciiArt.java`** — static string constants (logo, boot lines, separators).

### Content Files (`src/main/resources/`)

```
resources/
  puzzles/
    chapters.yaml          ← ordered list of chapters and puzzle file names
    tutorial_p01_double.yaml
    ch01_p01_filter.yaml
    ...
  story/
    tutorial_p01.txt       ← [BRIEFING] and [LOG] sections
    ch01_p01.txt
    ...
```

**Adding a new puzzle:** create `puzzles/<id>.yaml` + `story/<id>.txt`, then add the filename (without `.yaml`) to `chapters.yaml`. No Java changes needed.

## Puzzle YAML Format

```yaml
id: ch01_p01
chapter: 1
title: "SIGNAL FILTER"
story_file: ch01_p01.txt
grid:
  rows: 1
  cols: 2
nodes:
  - id: 0
    row: 0
    col: 0
    locked: false
  - id: 1
    row: 0
    col: 1
    locked: true
    code: |
      loop: LDA $F0
            BEQ loop
            STA $FF
            JMP loop
ports:
  - from_node: 0
    to_node: 1
    direction: RIGHT        # LEFT | RIGHT | UP | DOWN
test_vectors:
  - input:  [3, -1, 7]
    output: [3, 7]
max_cycles: 500
```

## 6502 Opcode Set

~35 opcodes. Notable design decisions:

- **CLC/SEC included** — without them, `ADC`/`SBC` carry bleeds across loop iterations.
- **ASL accumulator-only** — `ASL A` (or just `ASL`) shifts the accumulator left. No memory ASL.
- **No indexed addressing** — all addresses are zero-page or immediate. No `LDA $00,X` etc.
- **PC is instruction index** — branches and jumps use instruction list indices, not byte offsets. The assembler resolves labels to indices.
- **Division idiom**: `CMP #N; BCC done; SBC #N` — CMP sets C=1 when A≥N, so SBC immediately after always subtracts exactly N (no borrow).
- **Port blocking**: reading from an unconnected port throws `PortNotConnectedException`. Writing to `$FF` adds a signed value to the output list (values >127 become negative).

## Key Constraints (puzzle design)

- Max **20 instruction lines** per node (blank lines, comment-only lines, and label-only lines are free).
- Carry flag is **not guaranteed** to be 0 at node start — use `CLC` explicitly before ADC loops.
- Values constrained to **0–127** for puzzles involving signed arithmetic to avoid byte-overflow issues.
- Sums of N values should stay **<256** (no 16-bit accumulation support).
