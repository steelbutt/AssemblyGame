package com.assemblygame.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CpuTest {

    private Cpu cpu;
    private Memory mem;

    @BeforeEach
    void setUp() {
        cpu = new Cpu();
        mem = new Memory(new ArrayList<>());
    }

    private void exec(Opcode op, AddressMode mode, int operand) {
        op.execute(cpu, mem, operand, mode);
    }

    private void execImm(Opcode op, int value) {
        exec(op, AddressMode.IMMEDIATE, value);
    }

    private void execZP(Opcode op, int addr) {
        exec(op, AddressMode.ZERO_PAGE, addr);
    }

    @Test
    void ldaImmediate() {
        execImm(Opcode.LDA, 42);
        assertEquals(42, cpu.a);
        assertFalse(cpu.zero);
        assertFalse(cpu.negative);
    }

    @Test
    void ldaZero() {
        execImm(Opcode.LDA, 0);
        assertEquals(0, cpu.a);
        assertTrue(cpu.zero);
        assertFalse(cpu.negative);
    }

    @Test
    void ldaNegative() {
        execImm(Opcode.LDA, 0x80); // 128 = negative in signed
        assertEquals(0x80, cpu.a);
        assertFalse(cpu.zero);
        assertTrue(cpu.negative);
    }

    @Test
    void staAndLda() {
        execImm(Opcode.LDA, 99);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x10);
        cpu.a = 0;
        exec(Opcode.LDA, AddressMode.ZERO_PAGE, 0x10);
        assertEquals(99, cpu.a);
    }

    @Test
    void adcNoCarry() {
        execImm(Opcode.LDA, 10);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x00);
        execImm(Opcode.LDA, 5);
        execZP(Opcode.ADC, 0x00); // A = 5 + 10 + 0 carry = 15... wait, need value at $00
        // Actually ADC reads from memory at address $00 which we wrote 10 to
        assertEquals(15, cpu.a);
        assertFalse(cpu.carry);
    }

    @Test
    void adcSelfDoubling() {
        execImm(Opcode.LDA, 7);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x00);
        // CLC first
        exec(Opcode.CLC, AddressMode.IMPLIED, 0);
        // ADC $00 (A=7, memory[0]=7, carry=0) -> A=14
        execZP(Opcode.ADC, 0x00);
        assertEquals(14, cpu.a);
        assertFalse(cpu.carry);
    }

    @Test
    void aslDoubles() {
        execImm(Opcode.LDA, 6);
        exec(Opcode.ASL, AddressMode.IMPLIED, 0);
        assertEquals(12, cpu.a);
        assertFalse(cpu.carry);
    }

    @Test
    void aslSetsCarryOnOverflow() {
        execImm(Opcode.LDA, 0x80); // 128 -> shift left -> 0, carry set
        exec(Opcode.ASL, AddressMode.IMPLIED, 0);
        assertEquals(0, cpu.a);
        assertTrue(cpu.carry);
        assertTrue(cpu.zero);
    }

    @Test
    void cmpSetsFlagsEqual() {
        execImm(Opcode.LDA, 5);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x00);
        execZP(Opcode.CMP, 0x00); // compare 5 with 5
        assertTrue(cpu.zero);
        assertTrue(cpu.carry);  // A >= operand
        assertFalse(cpu.negative);
    }

    @Test
    void cmpGreater() {
        execImm(Opcode.LDA, 10);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x00);
        execImm(Opcode.LDA, 5);
        execZP(Opcode.CMP, 0x00); // compare 5 with memory[0]=10: 5 < 10
        assertFalse(cpu.zero);
        assertFalse(cpu.carry); // A < operand
        assertTrue(cpu.negative); // result = 5-10 = negative
    }

    @Test
    void sbcWithCarrySet() {
        exec(Opcode.SEC, AddressMode.IMPLIED, 0);
        execImm(Opcode.LDA, 10);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x00);
        execImm(Opcode.LDA, 15);
        execZP(Opcode.SBC, 0x00); // 15 - 10 - (1-1) = 5
        assertEquals(5, cpu.a);
        assertTrue(cpu.carry); // no borrow
    }

    @Test
    void inxWraps() {
        cpu.x = 0xFF;
        exec(Opcode.INX, AddressMode.IMPLIED, 0);
        assertEquals(0, cpu.x);
        assertTrue(cpu.zero);
    }

    @Test
    void dexWraps() {
        cpu.x = 0;
        exec(Opcode.DEX, AddressMode.IMPLIED, 0);
        assertEquals(0xFF, cpu.x);
        assertTrue(cpu.negative);
    }

    @Test
    void transferOps() {
        execImm(Opcode.LDA, 42);
        exec(Opcode.TAX, AddressMode.IMPLIED, 0);
        assertEquals(42, cpu.x);
        exec(Opcode.TAY, AddressMode.IMPLIED, 0);
        assertEquals(42, cpu.y);
        execImm(Opcode.LDA, 0);
        exec(Opcode.TXA, AddressMode.IMPLIED, 0);
        assertEquals(42, cpu.a);
    }

    @Test
    void stackPushPop() {
        execImm(Opcode.LDA, 77);
        exec(Opcode.PHA, AddressMode.IMPLIED, 0);
        execImm(Opcode.LDA, 0);
        exec(Opcode.PLA, AddressMode.IMPLIED, 0);
        assertEquals(77, cpu.a);
    }

    @Test
    void brkHalts() {
        assertFalse(cpu.halted);
        exec(Opcode.BRK, AddressMode.IMPLIED, 0);
        assertTrue(cpu.halted);
    }

    @Test
    void clcAndSec() {
        cpu.carry = true;
        exec(Opcode.CLC, AddressMode.IMPLIED, 0);
        assertFalse(cpu.carry);
        exec(Opcode.SEC, AddressMode.IMPLIED, 0);
        assertTrue(cpu.carry);
    }

    @Test
    void andLogic() {
        execImm(Opcode.LDA, 0xFF);
        exec(Opcode.STA, AddressMode.ZERO_PAGE, 0x05);
        execImm(Opcode.LDA, 0x0F);
        execZP(Opcode.AND, 0x05); // 0x0F & 0xFF = 0x0F
        assertEquals(0x0F, cpu.a);
    }
}
