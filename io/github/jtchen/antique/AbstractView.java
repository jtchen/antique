/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.event.*;

/*
 *	This class defines the view and serves as an interface to other classes.
 *	The view is the presenter of the model, but it should not have any
 *	knowledge of the controller.
 *
 *	There are 3 types of update methods:
 *
 *	1.	moveCursorByPoint: This method is called through mouse events or some
 *		navigation keys (UP, DOWN, PAGE_UP, PAGE_DOWN, HOME, END) and returns
 *		the caret position for the controller to set the model.
 *
 *	2.	updateCursorByModel: This method is called by certain navigation keys
 *		(LEFT, RIGHT, HOME, END), or the caret is moved when finding,
 *		replacing, or going to a specific line.
 *
 *	3.	updateTextByModel: This method will be called after changing the text.
 *		A range of rows can be provided to reset the line metrics, otherwise
 *		this method will reset all rows. If no optional viewport is provided,
 *		this method moves the viewport to contain the cursor.
 */
abstract class AbstractView extends AbstractEditor.AdaptiveCanvas
		implements ComponentListener {

	abstract void setModel(AbstractModel model);
	abstract Rectangle getViewport();

	abstract boolean isLineWrap();
	abstract void setLineWrap(boolean isLineWrap);

	abstract int getLineHeight();
	abstract int getCursorX();
	abstract int getCursorY();

	abstract int[] moveCursorByPoint(int x, int y);
	abstract void updateCursorByModel();
	abstract void updateTextByModel(int beginRow, int endRow);
	abstract void updateTextByModel();
	abstract void updateTextByModel(Point viewportOrigin);

}
