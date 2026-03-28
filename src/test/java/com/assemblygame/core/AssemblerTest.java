package com.assemblygame.core;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AssemblerTest {

    private final Assembler asm = new Assembler();

    @Test
    void simpleImmediate() {
        var result = asm.assemble("LDA #5");
        assertFalse(result.hasError(), result.error());
        assertEquals(1, result.instructions().size());
        Assembler.Instruction instr = result.instructions().get(0);
        assertEquals(Opcode.LDA, instr.opcode());
        assertEquals(AddressMode.IMMEDIATE, instr.mode());
        assertEquals(5, instr.operand());
    }

    @Test
    void hexImmediate() {
        var result = asm.assemble("LDA #$FF");
        assertFalse(result.hasError(), result.error());
        assertEquals(0xFF, result.instructions().get(0).operand());
    }

    @Test
    void zeroPageAddress() {
        var result = asm.assemble("STA $00");
        assertFalse(result.hasError());
        assertEquals(Opcode.STA, result.instructions().get(0).opcode());
        assertEquals(AddressMode.ZERO_PAGE, result.instructions().get(0).mode());
        assertEquals(0, result.instructions().get(0).operand());
    }

    @Test
    void labelResolution() {
        String code = "loop:\n  LDA $FE\n  JMP loop";
        var result = asm.assemble(code);
        assertFalse(result.hasError(), result.error());
        // 2 instructions: LDA and JMP
        assertEquals(2, result.instructions().size());
        // JMP target should be 0 (index of the first instruction)
        Assembler.Instruction jmp = result.instructions().get(1);
        assertEquals(Opcode.JMP, jmp.opcode());
        assertEquals(0, jmp.operand()); // 'loop' maps to instruction index 0
    }

    @Test
    void labelsDoNotCountAsInstructions() {
        String code = "start:\n  LDA $FE\n  ; comment\n\n  STA $FF\n  JMP start";
        var result = asm.assemble(code);
        assertFalse(result.hasError(), result.error());
        assertEquals(3, result.instructions().size());
    }

    @Test
    void commentsStripped() {
        var result = asm.assemble("LDA #5 ; load five");
        assertFalse(result.hasError());
        assertEquals(1, result.instructions().size());
    }

    @Test
    void unknownOpcodeReturnsError() {
        var result = asm.assemble("FOO #5");
        assertTrue(result.hasError());
        assertTrue(result.error().contains("unknown opcode"));
    }

    @Test
    void instructionLimitEnforced() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i <= Assembler.MAX_INSTRUCTIONS; i++) {
            code.append("NOP\n");
        }
        var result = asm.assemble(code.toString());
        assertTrue(result.hasError());
        assertTrue(result.error().contains("exceeds " + Assembler.MAX_INSTRUCTIONS));
    }

    @Test
    void exactlyTwentyInstructionsAllowed() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < Assembler.MAX_INSTRUCTIONS; i++) {
            code.append("NOP\n");
        }
        var result = asm.assemble(code.toString());
        assertFalse(result.hasError(), result.error());
        assertEquals(20, result.instructions().size());
    }

    @Test
    void impliedOpcodes() {
        var result = asm.assemble("CLC\nINX\nDEX\nBRK");
        assertFalse(result.hasError(), result.error());
        assertEquals(4, result.instructions().size());
        assertEquals(Opcode.CLC, result.instructions().get(0).opcode());
        assertEquals(AddressMode.IMPLIED, result.instructions().get(0).mode());
    }

    @Test
    void aslAccumulator() {
        var result = asm.assemble("ASL A");
        assertFalse(result.hasError(), result.error());
        assertEquals(Opcode.ASL, result.instructions().get(0).opcode());
    }

    @Test
    void branchOpcodeToLabel() {
        String code = "loop:  LDA $FE\n       BEQ loop";
        var result = asm.assemble(code);
        assertFalse(result.hasError(), result.error());
        assertEquals(2, result.instructions().size());
        assertEquals(Opcode.BEQ, result.instructions().get(1).opcode());
        assertEquals(0, result.instructions().get(1).operand()); // targets instruction 0
    }

    @Test
    void tutorialDoublerSolution() {
        String code = "loop: CLC\n      LDA $FE\n      ASL A\n      STA $FF\n      JMP loop";
        var result = asm.assemble(code);
        assertFalse(result.hasError(), result.error());
        assertEquals(5, result.instructions().size());
    }
}
