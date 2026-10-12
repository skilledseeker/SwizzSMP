package com.swizzsmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/** The startup banner printed to the console. */
final class Banner {

    private static final String[] LINES = {
            "#   #  ###  ####  #####     ####  #   #      ####  ###  #   #",
            "## ## #   # #   # #         #   #  # #      #     #   # ## ##",
            "# # # ##### #   # ####      ####    #        ###  ##### # # #",
            "#   # #   # #   # #         #   #   #           # #   # #   #",
            "#   # #   # ####  #####     ####    #       ####  #   # #   #",
            "",
            " ###  #   #  ###       #### #   # ##### #     #     ##### ####  #   #  ####",
            "#   # #  #  #   #     #     #  #    #   #     #     #     #   # ## ## #",
            "##### ###   #####      ###  ###     #   #     #     ####  #   # # # # #",
            "#   # #  #  #   #         # #  #    #   #     #     #     #   # #   # #",
            "#   # #   # #   #     ####  #   # ##### ##### ##### ##### ####  #   #  ####",
            "",
            "#   # ##### ####   #### #####  ###  #   #       #          ###",
            "#   # #     #   # #       #   #   # ##  #      ##         #  ##",
            "#   # ####  ####   ###    #   #   # # # #       #         # # #",
            " # #  #     #  #      #   #   #   # #  ##       #         ##  #",
            "  #   ##### #   # ####  #####  ###  #   #      ###    #    ###"
    };

    private Banner() {
    }

    static void print(ComponentLogger logger) {
        logger.info(Component.empty());
        for (int i = 0; i < LINES.length; i++) {
            // Purple at the top fading to blue at the bottom.
            float t = LINES.length <= 1 ? 0 : (float) i / (LINES.length - 1);
            int r = Math.round(0xB0 + (0x34 - 0xB0) * t);
            int g = Math.round(0x5C + (0x98 - 0x5C) * t);
            int b = Math.round(0xE0 + (0xDB - 0xE0) * t);
            logger.info(Component.text("    " + LINES[i], TextColor.color(r, g, b)));
        }
        logger.info(Component.empty());
    }
}
