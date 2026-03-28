package com.assemblygame.core;

public enum AddressMode {
    IMMEDIATE,   // LDA #$05
    ZERO_PAGE,   // LDA $05  or  LDA $0500
    ABSOLUTE,    // same as ZERO_PAGE in our VM (no distinction needed)
    IMPLIED,     // NOP, BRK, TAX, etc.
    RELATIVE     // branches — operand is absolute target address (resolved by Assembler)
}
