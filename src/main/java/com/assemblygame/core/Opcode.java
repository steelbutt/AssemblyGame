package com.assemblygame.core;

/**
 * Simplified 6502 opcode set. Each enum value handles its own execution logic
 * via the execute() method, given the current CPU and Memory state.
 */
public enum Opcode {

    // Load/Store
    LDA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            cpu.updateNZ(cpu.a);
        }
    },
    LDX {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.x = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            cpu.updateNZ(cpu.x);
        }
    },
    LDY {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.y = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            cpu.updateNZ(cpu.y);
        }
    },
    STA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            mem.write(resolveAddr(cpu, mem, operand, mode), cpu.a);
        }
    },
    STX {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            mem.write(resolveAddr(cpu, mem, operand, mode), cpu.x);
        }
    },
    STY {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            mem.write(resolveAddr(cpu, mem, operand, mode), cpu.y);
        }
    },

    // Transfer
    TAX {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.x = cpu.a;
            cpu.updateNZ(cpu.x);
        }
    },
    TAY {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.y = cpu.a;
            cpu.updateNZ(cpu.y);
        }
    },
    TXA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = cpu.x;
            cpu.updateNZ(cpu.a);
        }
    },
    TYA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = cpu.y;
            cpu.updateNZ(cpu.a);
        }
    },

    // Stack
    PHA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.stackPush(mem, cpu.a);
        }
    },
    PLA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = cpu.stackPop(mem);
            cpu.updateNZ(cpu.a);
        }
    },

    // Arithmetic
    ADC {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            int val = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            int result = cpu.a + val + (cpu.carry ? 1 : 0);
            cpu.overflow = ((~(cpu.a ^ val) & (cpu.a ^ result)) & 0x80) != 0;
            cpu.carry = result > 0xFF;
            cpu.a = result & 0xFF;
            cpu.updateNZ(cpu.a);
        }
    },
    SBC {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            int val = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            int result = cpu.a - val - (cpu.carry ? 0 : 1);
            cpu.overflow = (((cpu.a ^ val) & (cpu.a ^ result)) & 0x80) != 0;
            cpu.carry = result >= 0;
            cpu.a = result & 0xFF;
            cpu.updateNZ(cpu.a);
        }
    },
    INX {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.x = (cpu.x + 1) & 0xFF;
            cpu.updateNZ(cpu.x);
        }
    },
    DEX {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.x = (cpu.x - 1) & 0xFF;
            cpu.updateNZ(cpu.x);
        }
    },
    INY {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.y = (cpu.y + 1) & 0xFF;
            cpu.updateNZ(cpu.y);
        }
    },
    DEY {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.y = (cpu.y - 1) & 0xFF;
            cpu.updateNZ(cpu.y);
        }
    },

    // Shift
    ASL {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            // Only accumulator mode supported
            cpu.carry = (cpu.a & 0x80) != 0;
            cpu.a = (cpu.a << 1) & 0xFF;
            cpu.updateNZ(cpu.a);
        }
    },

    // Logic
    AND {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = cpu.a & (mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF);
            cpu.updateNZ(cpu.a);
        }
    },
    ORA {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = cpu.a | (mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF);
            cpu.updateNZ(cpu.a);
        }
    },
    EOR {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.a = cpu.a ^ (mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF);
            cpu.updateNZ(cpu.a);
        }
    },

    // Compare
    CMP {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            int val = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            int result = cpu.a - val;
            cpu.carry = cpu.a >= val;
            cpu.updateNZ(result & 0xFF);
        }
    },
    CPX {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            int val = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            cpu.carry = cpu.x >= val;
            cpu.updateNZ((cpu.x - val) & 0xFF);
        }
    },
    CPY {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            int val = mem.read(resolveAddr(cpu, mem, operand, mode)) & 0xFF;
            cpu.carry = cpu.y >= val;
            cpu.updateNZ((cpu.y - val) & 0xFF);
        }
    },

    // Carry control
    CLC {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.carry = false;
        }
    },
    SEC {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.carry = true;
        }
    },

    // Branches — operand is the resolved absolute target address (set by Assembler)
    BEQ {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            if (cpu.zero) cpu.pc = operand;
        }
    },
    BNE {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            if (!cpu.zero) cpu.pc = operand;
        }
    },
    BPL {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            if (!cpu.negative) cpu.pc = operand;
        }
    },
    BMI {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            if (cpu.negative) cpu.pc = operand;
        }
    },
    BCC {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            if (!cpu.carry) cpu.pc = operand;
        }
    },
    BCS {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            if (cpu.carry) cpu.pc = operand;
        }
    },

    // Jump/Call
    JMP {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.pc = operand;
        }
    },
    JSR {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            // Push return address (pc - 1, pointing to last byte of JSR instruction)
            int ret = cpu.pc - 1;
            cpu.stackPush(mem, (ret >> 8) & 0xFF);
            cpu.stackPush(mem, ret & 0xFF);
            cpu.pc = operand;
        }
    },
    RTS {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            int lo = cpu.stackPop(mem);
            int hi = cpu.stackPop(mem);
            cpu.pc = ((hi << 8) | lo) + 1;
        }
    },

    // Misc
    NOP {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            // no-op
        }
    },
    BRK {
        @Override public void execute(Cpu cpu, Memory mem, int operand, AddressMode mode) {
            cpu.halted = true;
        }
    };

    public abstract void execute(Cpu cpu, Memory mem, int operand, AddressMode mode);

    /**
     * Resolve an operand to a memory address based on addressing mode.
     * For IMMEDIATE mode, this returns a synthetic address where the value is pre-stored.
     */
    protected static int resolveAddr(Cpu cpu, Memory mem, int operand, AddressMode mode) {
        return switch (mode) {
            case IMMEDIATE -> {
                // Assembler stores immediate value in a scratch location; we use operand directly
                // by writing to a fixed scratch address $00FE (not a real 6502 trick, but works for our VM)
                // Actually, for simplicity: immediate operand IS the value — return it via a temp write
                mem.writeRaw(Memory.IMMEDIATE_SCRATCH, operand & 0xFF);
                yield Memory.IMMEDIATE_SCRATCH;
            }
            case ZERO_PAGE, ABSOLUTE -> operand;
            case IMPLIED -> 0; // not used for memory ops
            case RELATIVE -> operand; // branches use absolute target
        };
    }

    public static Opcode fromString(String name) {
        return valueOf(name.toUpperCase());
    }
}
