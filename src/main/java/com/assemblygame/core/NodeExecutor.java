package com.assemblygame.core;

import java.util.List;

/**
 * Executes a program (list of Instructions) on a Cpu+Memory pair.
 * Designed to run on a virtual thread; blocks on port reads/writes.
 * Implements Runnable for use with ExecutorService.
 */
public class NodeExecutor implements Runnable {

    private final int nodeId;
    private final List<Assembler.Instruction> program;
    private final Cpu cpu;
    private final Memory memory;

    private volatile boolean running = false;
    private volatile Throwable executionError = null;
    private volatile int cycleCount = 0;

    // Step-mode support: when stepMode is true, execution pauses after each instruction
    private volatile boolean stepMode = false;
    private volatile boolean stepReady = false; // true = proceed one step
    private final Object stepLock = new Object();

    // Listener for UI updates during step mode
    private Runnable onInstructionExecuted;
    private volatile int currentInstructionIndex = -1;

    public NodeExecutor(int nodeId, List<Assembler.Instruction> program, Memory memory) {
        this.nodeId = nodeId;
        this.program = program;
        this.cpu = new Cpu();
        this.memory = memory;
    }

    @Override
    public void run() {
        running = true;
        try {
            while (!cpu.halted && running) {
                if (cpu.pc < 0 || cpu.pc >= program.size()) {
                    // PC out of bounds — treat as halt
                    cpu.halted = true;
                    break;
                }

                if (stepMode) {
                    waitForStep();
                    if (!running) break;
                }

                Assembler.Instruction instr = program.get(cpu.pc);
                currentInstructionIndex = cpu.pc;
                cpu.pc++; // advance before execute (branches will overwrite)

                instr.opcode().execute(cpu, memory, instr.operand(), instr.mode());
                cycleCount++;

                if (onInstructionExecuted != null) {
                    onInstructionExecuted.run();
                }
            }
        } catch (Memory.ExecutionInterruptedException e) {
            // Normal shutdown signal
        } catch (Throwable t) {
            executionError = t;
        } finally {
            running = false;
        }
    }

    public void stop() {
        running = false;
        memory.interrupt();
        // Unblock any waiting step
        synchronized (stepLock) {
            stepReady = true;
            stepLock.notifyAll();
        }
    }

    /** In step mode, advance exactly one instruction. */
    public void step() {
        synchronized (stepLock) {
            stepReady = true;
            stepLock.notifyAll();
        }
    }

    public void setStepMode(boolean stepMode) {
        this.stepMode = stepMode;
    }

    public void setOnInstructionExecuted(Runnable listener) {
        this.onInstructionExecuted = listener;
    }

    private void waitForStep() {
        synchronized (stepLock) {
            while (!stepReady && running) {
                try {
                    stepLock.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            stepReady = false;
        }
    }

    public int getNodeId()                 { return nodeId; }
    public Cpu getCpu()                    { return cpu; }
    public int getCycleCount()             { return cycleCount; }
    public boolean isRunning()             { return running; }
    public boolean isHalted()              { return cpu.halted; }
    public int getCurrentInstructionIndex(){ return currentInstructionIndex; }
    public Throwable getExecutionError()   { return executionError; }
    public List<Assembler.Instruction> getProgram() { return program; }
}
