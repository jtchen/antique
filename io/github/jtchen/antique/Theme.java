/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;

/*
 *	This class handles information about text colors.
 */
class Theme {

	static final Color FOREGROUND_COLOR = Color.black;
	static final Color BACKGROUND_COLOR = Color.white;
	static final Color CONTROL_FOREGROUND_COLOR = SystemColor.controlShadow;
	static final Color CONTROL_BACKGROUND_COLOR = SystemColor.control;
	static final Color CURSOR_COLOR = Color.red;
	static final Color SOFT_HIGHLIGHT_COLOR = Color.yellow;
	static final Color HARD_HIGHLIGHT_COLOR = Color.cyan;

	static final byte COLOR_MASK = 15;

	static final byte MATCH_MASK = 16;
	static final byte MESSY_WHITESPACE_MASK = 32;

	static final byte IMPORTANT = 1;
	static final byte PRIMARY_BLOCK = 2;
	static final byte SECONDARY_BLOCK = 3;
	static final byte PRIMARY_INLINE = 4;
	static final byte SECONDARY_INLINE = 5;

	private static Color[] colors = new Color[6];

	static {
		colors[0] = FOREGROUND_COLOR;
		colors[1] = Color.red;
		colors[2] = Color.blue;
		colors[3] = new Color(0, 128, 128); // teal
		colors[4] = Color.magenta;
		colors[5] = new Color(128, 0, 128); // purple
	}

	static Color getColor(byte colorCode) {
		return colors[(int) (colorCode &= COLOR_MASK)];
	}

}
