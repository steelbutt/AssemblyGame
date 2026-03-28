package com.assemblygame.ui;

/**
 * Static ASCII art constants for the game's visual identity.
 */
public final class AsciiArt {

    private AsciiArt() {}

    public static final String DELTA6_LOGO =
        "  ██████╗ ███████╗██╗  ████████╗ █████╗        ██████╗ \n" +
        "  ██╔══██╗██╔════╝██║  ╚══██╔══╝██╔══██╗      ██╔════╝ \n" +
        "  ██║  ██║█████╗  ██║     ██║   ███████║  ██╗ ███████╗ \n" +
        "  ██║  ██║██╔══╝  ██║     ██║   ██╔══██║  ╚═╝ ██╔══██║ \n" +
        "  ██████╔╝███████╗███████╗██║   ██║  ██║  ██╗ ╚██████╔╝\n" +
        "  ╚═════╝ ╚══════╝╚══════╝╚═╝   ╚═╝  ╚═╝  ╚═╝  ╚═════╝ ";

    public static final String[] BOOT_LINES = {
        "DELTA-6 RECOVERY SYSTEM v0.1",
        "BIOS POST ...................... OK",
        "MEMORY CHECK ... 65536 BYTES ... OK",
        "LOADING KERNEL ... [" + "█".repeat(20) + "] 100%",
        "MOUNTING SUBSYSTEMS ...",
        "  /dev/comms        [FAULT]",
        "  /dev/lifesupport  [FAULT]",
        "  /dev/vault        [FAULT]",
        "",
        "  OPERATOR REQUIRED.",
        "  PRESS ANY KEY TO BEGIN."
    };

    public static final String CHAPTER_TITLE_TOP    = "╔══════════════════════════════════════╗";
    public static final String CHAPTER_TITLE_BOTTOM = "╚══════════════════════════════════════╝";

    public static final String SEPARATOR_HEAVY = "█".repeat(66);
    public static final String SEPARATOR_LIGHT = "─".repeat(66);
}
