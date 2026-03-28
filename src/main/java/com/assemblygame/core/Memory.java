package com.assemblygame.core;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * 256-byte zero page memory for one node, plus port-mapped I/O.
 *
 * Special addresses:
 *   $F0 = LEFT port
 *   $F1 = RIGHT port
 *   $F2 = UP port
 *   $F3 = DOWN port
 *   $FE = puzzle INPUT stream
 *   $FF = puzzle OUTPUT stream
 *   $FB = immediate-value scratch (internal VM use only)
 *
 * Stack lives at $D0–$EF (32 bytes).
 * General zero page: $00–$CF.
 */
public class Memory {

    public static final int LEFT_PORT  = 0xF0;
    public static final int RIGHT_PORT = 0xF1;
    public static final int UP_PORT    = 0xF2;
    public static final int DOWN_PORT  = 0xF3;
    public static final int INPUT_ADDR  = 0xFE;
    public static final int OUTPUT_ADDR = 0xFF;
    public static final int IMMEDIATE_SCRATCH = 0xFB;  // internal scratch for immediate mode

    public static final int STACK_TOP    = 0xF0; // exclusive upper bound of stack area
    public static final int STACK_BOTTOM = 0xD0; // lowest valid stack address

    private final int[] ram = new int[256];

    // Port queues — set externally by PuzzleRunner
    private SynchronousQueue<Integer> leftQueue;
    private SynchronousQueue<Integer> rightQueue;
    private SynchronousQueue<Integer> upQueue;
    private SynchronousQueue<Integer> downQueue;

    // Input/output streams — set externally by PuzzleRunner
    private java.util.Iterator<Integer> inputStream;
    private final java.util.List<Integer> outputCollector;
    private volatile boolean interrupted = false;

    public Memory(java.util.List<Integer> outputCollector) {
        this.outputCollector = outputCollector;
    }

    public void setLeftQueue(SynchronousQueue<Integer> q)  { this.leftQueue  = q; }
    public void setRightQueue(SynchronousQueue<Integer> q) { this.rightQueue = q; }
    public void setUpQueue(SynchronousQueue<Integer> q)    { this.upQueue    = q; }
    public void setDownQueue(SynchronousQueue<Integer> q)  { this.downQueue  = q; }
    public void setInputStream(java.util.Iterator<Integer> it) { this.inputStream = it; }
    public void interrupt() { this.interrupted = true; }

    /** Read a byte from an address, handling I/O addresses via blocking. */
    public int read(int addr) {
        addr &= 0xFF;
        return switch (addr) {
            case LEFT_PORT  -> blockingRead(leftQueue,  "LEFT");
            case RIGHT_PORT -> blockingRead(rightQueue, "RIGHT");
            case UP_PORT    -> blockingRead(upQueue,    "UP");
            case DOWN_PORT  -> blockingRead(downQueue,  "DOWN");
            case INPUT_ADDR -> {
                if (inputStream != null && inputStream.hasNext()) {
                    int val = inputStream.next();
                    // Store as unsigned byte
                    yield val & 0xFF;
                }
                yield 0; // no more input
            }
            default -> ram[addr] & 0xFF;
        };
    }

    /** Write a byte to an address, handling I/O addresses via blocking. */
    public void write(int addr, int value) {
        addr &= 0xFF;
        value &= 0xFF;
        switch (addr) {
            case LEFT_PORT  -> blockingWrite(leftQueue,  value, "LEFT");
            case RIGHT_PORT -> blockingWrite(rightQueue, value, "RIGHT");
            case UP_PORT    -> blockingWrite(upQueue,    value, "UP");
            case DOWN_PORT  -> blockingWrite(downQueue,  value, "DOWN");
            case OUTPUT_ADDR -> outputCollector.add(value > 127 ? value - 256 : value);
            default -> ram[addr] = value & 0xFF;
        }
    }

    /** Direct RAM read (bypasses I/O mapping — used for stack and scratch). */
    public int readRaw(int addr) {
        return ram[addr & 0xFF] & 0xFF;
    }

    /** Direct RAM write (bypasses I/O mapping). */
    public void writeRaw(int addr, int value) {
        ram[addr & 0xFF] = value & 0xFF;
    }

    private int blockingRead(SynchronousQueue<Integer> queue, String portName) {
        if (queue == null) throw new PortNotConnectedException(portName);
        try {
            while (!interrupted) {
                Integer val = queue.poll(50, TimeUnit.MILLISECONDS);
                if (val != null) return val & 0xFF;
            }
            throw new ExecutionInterruptedException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionInterruptedException();
        }
    }

    private void blockingWrite(SynchronousQueue<Integer> queue, int value, String portName) {
        if (queue == null) throw new PortNotConnectedException(portName);
        try {
            while (!interrupted) {
                if (queue.offer(value & 0xFF, 50, TimeUnit.MILLISECONDS)) return;
            }
            throw new ExecutionInterruptedException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionInterruptedException();
        }
    }

    public static class PortNotConnectedException extends RuntimeException {
        public PortNotConnectedException(String port) {
            super("Port " + port + " is not connected to any neighbor node");
        }
    }

    public static class ExecutionInterruptedException extends RuntimeException {
        public ExecutionInterruptedException() {
            super("Node execution was interrupted");
        }
    }
}
