package com.assemblygame.core;

/**
 * Simplified 6502 CPU state. All fields are package-accessible for Opcode execute methods.
 * The PC is an index into the program's instruction list (not a byte address), which
 * simplifies execution significantly — no need for a real memory map of instructions.
 */
public class Cpu {

    // Registers (0–255)
    public int a;   // Accumulator
    public int x;   // Index X
    public int y;   // Index Y
    public int sp;  // Stack pointer (grows downward from $EF to $D0, i.e., 32 bytes of stack)

    // Program counter — index into the instruction list
    public int pc;

    // Flags
    public boolean negative;   // N
    public boolean zero;       // Z
    public boolean carry;      // C
    public boolean overflow;   // V

    // Execution state
    public boolean halted;

    public Cpu() {
        reset();
    }

    public void reset() {
        a = 0;
        x = 0;
        y = 0;
        sp = 0xEF;  // stack top (grows downward in zero page $D0–$EF)
        pc = 0;
        negative = false;
        zero = false;
        carry = false;
        overflow = false;
        halted = false;
    }

    /** Update N and Z flags based on a result byte. */
    public void updateNZ(int value) {
        value &= 0xFF;
        negative = (value & 0x80) != 0;
        zero = (value == 0);
    }

    /** Push a byte onto the stack (in the zero page stack area $D0–$EF). */
    public void stackPush(Memory mem, int value) {
        if (sp < Memory.STACK_BOTTOM) throw new RuntimeException("Stack overflow");
        mem.writeRaw(sp, value & 0xFF);
        sp--;
    }

    /** Pop a byte from the stack. */
    public int stackPop(Memory mem) {
        if (sp >= Memory.STACK_TOP) throw new RuntimeException("Stack underflow");
        sp++;
        return mem.readRaw(sp) & 0xFF;
    }
}
