package com.assemblygame.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles simplified 6502 assembly text into a list of Instructions.
 *
 * Line format: [label:] OPCODE [operand] [;comment]
 *
 * Operand formats:
 *   #$FF or #123      — immediate (hex or decimal)
 *   $FF or $FFFF      — zero page / absolute address
 *   label             — used for branches and jumps
 *   A                 — accumulator (for ASL)
 *   (nothing)         — implied
 *
 * Only instruction lines (non-blank, non-comment-only, non-label-only) count toward the 20-line limit.
 */
public class Assembler {

    public static final int MAX_INSTRUCTIONS = 20;

    public record Instruction(Opcode opcode, AddressMode mode, int operand) {}

    public record AssemblyResult(List<Instruction> instructions, String error) {
        public boolean hasError() { return error != null; }
    }

    public AssemblyResult assemble(String source) {
        String[] lines = source.split("\n");
        List<String[]> tokens = new ArrayList<>(); // [label, opcode, operand] per line
        Map<String, Integer> labelMap = new HashMap<>();
        List<Integer> instructionLineNums = new ArrayList<>();

        int instrCount = 0;

        // First pass: tokenize, collect labels, count instructions
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // Strip comment
            int commentIdx = line.indexOf(';');
            if (commentIdx >= 0) line = line.substring(0, commentIdx);
            line = line.trim();

            if (line.isEmpty()) continue;

            String label = null;
            String rest = line;

            // Extract label if present (ends with ':')
            int colonIdx = line.indexOf(':');
            if (colonIdx >= 0) {
                // Check it's before any spaces (i.e., it's a label, not something else)
                String possibleLabel = line.substring(0, colonIdx).trim();
                if (!possibleLabel.contains(" ")) {
                    label = possibleLabel.toLowerCase();
                    rest = line.substring(colonIdx + 1).trim();
                }
            }

            if (rest.isEmpty()) {
                // Label-only line: register label but don't count as instruction
                if (label != null) {
                    labelMap.put(label, instrCount); // points to next instruction
                }
                continue;
            }

            // Parse opcode and operand
            String[] parts = rest.split("\\s+", 2);
            String opName = parts[0].toUpperCase();
            String operandStr = parts.length > 1 ? parts[1].trim() : "";

            // Register label before this instruction
            if (label != null) {
                labelMap.put(label, instrCount);
            }

            instrCount++;
            if (instrCount > MAX_INSTRUCTIONS) {
                return new AssemblyResult(null,
                    "Line " + (i + 1) + ": program exceeds " + MAX_INSTRUCTIONS + " instructions (limit enforced)");
            }

            instructionLineNums.add(i + 1);
            tokens.add(new String[]{opName, operandStr});
        }

        // Second pass: resolve operands and labels
        List<Instruction> instructions = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String opName = tokens.get(i)[0];
            String operandStr = tokens.get(i)[1];
            int lineNum = instructionLineNums.get(i);

            Opcode opcode;
            try {
                opcode = Opcode.fromString(opName);
            } catch (IllegalArgumentException e) {
                return new AssemblyResult(null,
                    "Line " + lineNum + ": unknown opcode '" + opName + "'");
            }

            try {
                Instruction instr = parseOperand(opcode, operandStr, labelMap, lineNum);
                instructions.add(instr);
            } catch (AssemblyException e) {
                return new AssemblyResult(null, "Line " + lineNum + ": " + e.getMessage());
            }
        }

        return new AssemblyResult(instructions, null);
    }

    private Instruction parseOperand(Opcode opcode, String operandStr, Map<String, Integer> labels, int lineNum)
            throws AssemblyException {

        // Implied opcodes
        if (isImplied(opcode)) {
            return new Instruction(opcode, AddressMode.IMPLIED, 0);
        }

        // ASL accumulator: operand is "A" or empty
        if (opcode == Opcode.ASL) {
            if (operandStr.isEmpty() || operandStr.equalsIgnoreCase("A")) {
                return new Instruction(opcode, AddressMode.IMPLIED, 0);
            }
        }

        // Branch opcodes — operand must be a label or absolute address
        if (isBranch(opcode) || opcode == Opcode.JMP || opcode == Opcode.JSR) {
            if (operandStr.isEmpty()) {
                throw new AssemblyException("'" + opcode + "' requires a target label or address");
            }
            int target = resolveTarget(operandStr, labels, lineNum);
            return new Instruction(opcode, AddressMode.RELATIVE, target);
        }

        if (operandStr.isEmpty()) {
            throw new AssemblyException("'" + opcode + "' requires an operand");
        }

        // Immediate: #$FF or #123 or #-128 etc.
        if (operandStr.startsWith("#")) {
            int val = parseNumber(operandStr.substring(1), lineNum);
            val &= 0xFF; // truncate to byte
            return new Instruction(opcode, AddressMode.IMMEDIATE, val);
        }

        // Address: $FF or $FFFF or decimal
        int addr = parseNumber(operandStr, lineNum);
        addr &= 0xFF; // treat all addresses as zero-page (our VM only has 256 bytes)
        return new Instruction(opcode, AddressMode.ZERO_PAGE, addr);
    }

    private int resolveTarget(String operandStr, Map<String, Integer> labels, int lineNum)
            throws AssemblyException {
        String lower = operandStr.toLowerCase();
        if (labels.containsKey(lower)) {
            return labels.get(lower);
        }
        // Try as a numeric absolute address (instruction index)
        try {
            return parseNumber(operandStr, lineNum);
        } catch (AssemblyException e) {
            throw new AssemblyException("undefined label '" + operandStr + "'");
        }
    }

    private int parseNumber(String s, int lineNum) throws AssemblyException {
        s = s.trim();
        try {
            if (s.startsWith("$")) {
                return Integer.parseInt(s.substring(1), 16);
            } else if (s.startsWith("-$")) {
                return -Integer.parseInt(s.substring(2), 16);
            } else {
                return Integer.parseInt(s);
            }
        } catch (NumberFormatException e) {
            throw new AssemblyException("invalid number '" + s + "'");
        }
    }

    private boolean isImplied(Opcode op) {
        return switch (op) {
            case TAX, TAY, TXA, TYA, INX, DEX, INY, DEY,
                 PHA, PLA, CLC, SEC, NOP, BRK, RTS -> true;
            default -> false;
        };
    }

    private boolean isBranch(Opcode op) {
        return switch (op) {
            case BEQ, BNE, BPL, BMI, BCC, BCS -> true;
            default -> false;
        };
    }

    public static class AssemblyException extends Exception {
        public AssemblyException(String msg) { super(msg); }
    }
}
