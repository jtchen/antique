/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.event.*;

/*
 *	This class defines the controller and serves as an interface to other
 *	classes. The controller receives user input, modifies the model, and calls
 *	the view to update accordingly. The editor mainly interacts with the
 *	controller and should not interact with the view or model.
 */
abstract class AbstractController
		implements MouseListener, MouseMotionListener, KeyListener {

	abstract void setEnabled(boolean isEnabled);

	abstract String getClipboard();

	abstract void setModel(AbstractModel model);
	abstract Rectangle getViewport();
	abstract Container getViewContainer();
	abstract void requestViewFocus();

	abstract int getCharCount();
	abstract void setLineWrap(boolean isLineWrap);
	abstract void setInitialText(String text);
	abstract String getText();
	abstract boolean isSelected();
	abstract String getSelectedText();

	/*
	 *	If at least one match is found, this method returns true.
	 */
	abstract boolean findNext();

	/*
	 *	If at least one match is found, this method returns true.
	 */
	abstract boolean replace();

	/*
	 *	If at least one match is found, this method returns true.
	 */
	abstract boolean replaceAll();

	abstract void disableMatch();
	abstract void goToLine(int lineNumber);

	abstract boolean isUndoable();
	abstract boolean isRedoable();
	abstract void undo();
	abstract void redo();
	abstract void cut();
	abstract void copy();

	/*
	 *	If the string to be pasted is too large, this method returns false.
	 */
	abstract boolean paste();

	abstract void selectAll();

}
