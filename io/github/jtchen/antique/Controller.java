/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.util.*;

/*
 *	This is an implementation of the controller. Note that it contains all the
 *	information needed to redo or undo editing operations.
 */
class Controller extends AbstractController {

	static final byte COMPOSITE = 0;
	static final byte INSERT = 1;
	static final byte BACK_SPACE = 2;
	static final byte UNDELETE = 3;
	static final byte DELETE = 4;
	static final byte INSERT_STRING = 5;
	static final byte DELETE_STRING = 6;

	private static final int UPDATE_ALL = 0;
	private static final int UPDATE_SINGLE_LINE = 1;

	private int updateMode = UPDATE_ALL;

	private AbstractEditor editor;
	private AbstractModel model;
	private AbstractView view;

	private boolean isEnabled = false;

	private boolean isShiftPressing = false;

	/*
	 *	This flag has been added for cases where mouseDragged() may be
	 *	accidentally called. This happened at least when a new file was opened
	 *	by clicking the filename in FileDialog, when the mouse position was in
	 *	the View after FileDialog was closed.
	 */
	private boolean isMousePressing = false;

	private Clipboard clipboard;

	private Stack undoStack = new Stack();
	private Stack redoStack = new Stack();

	private int magicCursorX = -1; // an impossible value as a flag

	Controller(AbstractEditor editor, AbstractModel model, AbstractView view) {
		this.editor = editor;
		this.model = model;
		this.view = view;

		clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
	}

	void setEnabled(boolean isEnabled) {
		if (isEnabled && (! this.isEnabled)) {
			view.addMouseListener(this);
			view.addMouseMotionListener(this);
			view.addKeyListener(this);
			this.isEnabled = true;
		} else if ((! isEnabled) && this.isEnabled) {
			view.removeMouseListener(this);
			view.removeMouseMotionListener(this);
			view.removeKeyListener(this);
			this.isEnabled = false;
		}
	}

	/* ---- Mouse event listeners ------------------------------------------ */

	public void mousePressed(MouseEvent e) {
		isMousePressing = true;
		int[] pos = view.moveCursorByPoint(e.getX(), e.getY());
		model.setCaret(pos);
		if (isShiftPressing) {
			model.setSelectionEnd(pos[0], pos[1]);
		} else {
			model.clearSelection();
		}
		editor.doSelectionChanged();
		magicCursorX = e.getX();
	}

	public void mouseDragged(MouseEvent e) {
		int[] pos = view.moveCursorByPoint(e.getX(), e.getY());
		model.setCaret(pos);
		if (isMousePressing) {
			model.setSelectionEnd(pos[0], pos[1]);
			editor.doSelectionChanged();
		}
		magicCursorX = e.getX();
	}

	public void mouseReleased(MouseEvent e) {
		isMousePressing = false;
	}

	public void mouseMoved(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}

	/* ---- A helper method for text changes ------------------------------- */

	private void textChanged() {
		if (updateMode == UPDATE_SINGLE_LINE) {
			int row = model.getCaretRow();
			view.updateTextByModel(row, row);
			updateMode = UPDATE_ALL;
		} else {
			view.updateTextByModel();
		}

		magicCursorX = view.getCursorX();
		editor.doTextChanged();
	}

	/* ---- Methods for the clipboard operations --------------------------- */

	String getClipboard() {
		String s = null;
		try {
			s = (String) clipboard.getContents(this)
					.getTransferData(DataFlavor.stringFlavor);
		} catch (Exception e) {}
		return s;
	}

	private void setClipboard(String s) {
		StringSelection ss = new StringSelection(s);
		clipboard.setContents(ss, ss);
	}

	/* ---- Methods for the editor ----------------------------------------- */

	void setModel(AbstractModel model) {
		this.model = model;
	}

	Rectangle getViewport() {
		return view.getViewport();
	}

	Container getViewContainer() {
		return view.getParent();
	}

	void requestViewFocus() {
		view.requestFocus();
	}

	int getCharCount() {
		return model.getCharCount();
	}

	void setLineWrap(boolean isLineWrap) {
		view.setLineWrap(isLineWrap);
	}

	void setInitialText(String text) {
		model.insertString(text);
		model.setCaret(0, 0);
	}

	String getText() {
		int lc = model.getLineCount();
		Range range = new Range();
		range.setEnd(lc - 1, model.getLine(lc - 1).length);
		return model.getStringByRange(range);
	}

	boolean isSelected() {
		return model.isSelected();
	}

	String getSelectedText() {
		Range sel = model.getSelection();
		if (sel != null) {
			return model.getStringByRange(sel);
		}
		return "";
	}

	private Range getMatchRange() {
		AbstractEditor.MatchConfig mc = editor.getMatchConfig();
		String target = mc.target;
		int cRow = model.getCaretRow();
		int cCol = model.getCaretColumn();

		Range range = new Range();
		range.setBegin(cRow, cCol - target.length());
		range.setEnd(cRow, cCol);
		return range;
	}

	boolean findNext() {
		AbstractEditor.MatchConfig mc = editor.getMatchConfig();
		if (model.countMatch(mc) > 0) {
			model.moveCaretToNextMatch();
			Range r = getMatchRange();
			model.setSelectionBegin(r.beginRow, r.beginColumn);
			model.setSelectionEnd(r.endRow, r.endColumn);
			editor.doSelectionChanged();
			view.updateCursorByModel();
			return true;
		}
		return false;
	}

	boolean replace() {
		return replace(false);
	}

	boolean replaceAll() {
		return replace(true);
	}

	private boolean replace(boolean isReplaceAll) {
		AbstractEditor.MatchConfig mc = editor.getMatchConfig();
		int count = model.countMatch(mc);
		if (count == 0) {
			return false;
		}

		String repl = mc.replacement;

		int cmdCount = (isReplaceAll) ? count : 1;
		Command compo = new Command(COMPOSITE, new Command[2 * cmdCount]);
		for (int i = 0; i < (2 * cmdCount); i += 2) {
			if (((i == 0) && (! model.isCaretAtMatchEnd())) || (i > 0)) {
				model.moveCaretToNextMatch();
			}
			Range match = getMatchRange();
			compo.set(i, (new Command(DELETE_STRING, match)).exec());
			compo.set(i + 1, (new Command(INSERT_STRING, repl)).exec());
		}
		undoStack.push(compo);
		redoStack.setSize(0);

		if ((! isReplaceAll) && ((count - 1) > 0)) {
			model.moveCaretToNextMatch();
			Range r = getMatchRange();
			model.setSelectionBegin(r.beginRow, r.beginColumn);
			model.setSelectionEnd(r.endRow, r.endColumn);
		} else {
			model.clearSelection();
		}
		editor.doSelectionChanged();
		textChanged();

		return true;
	}

	void disableMatch() {
		model.disableMatch();
	}

	void goToLine(int lineNumber) {
		int row = lineNumber - 1;
		row = Math.max(0, row);
		row = Math.min(row, model.getLineCount() - 1);
		model.setCaret(row, 0);
		view.updateCursorByModel();
		magicCursorX = view.getCursorX();
	}

	boolean isUndoable() {
		return (! undoStack.empty());
	}

	boolean isRedoable() {
		return (! redoStack.empty());
	}

	void undo() {
		if (! undoStack.empty()) {
			Command cmd = (Command) undoStack.pop();
			if (cmd.isComposite()) {
				int count = cmd.getCommandCount();
				for (int i = count - 1; i >= 0; i -= 1) {
					cmd.set(i, cmd.get(i).exec());
				}
				redoStack.push(cmd);
			} else {
				redoStack.push(cmd.exec());
			}
			model.clearSelection();
			editor.doSelectionChanged();
			textChanged();
		}
	}

	void redo() {
		if (! redoStack.empty()) {
			Command cmd = (Command) redoStack.pop();
			if (cmd.isComposite()) {
				int count = cmd.getCommandCount();
				for (int i = 0; i < count; i += 1) {
					cmd.set(i, cmd.get(i).exec());
				}
				undoStack.push(cmd);
			} else {
				undoStack.push(cmd.exec());
			}
			model.clearSelection();
			editor.doSelectionChanged();
			textChanged();
		}
	}

	void cut() {
		Range sel = model.getSelection();
		if (sel != null) {
			setClipboard(model.getStringByRange(sel));
			undoStack.push((new Command(DELETE_STRING, sel)).exec());
			model.clearSelection();
			redoStack.setSize(0);
			editor.doSelectionChanged();
			textChanged();
		}
	}

	void copy() {
		Range sel = model.getSelection();
		if (sel != null) {
			setClipboard(model.getStringByRange(sel));
		}
	}

	boolean paste() {
		Range sel = model.getSelection();
		String s = getClipboard();
		if (s == null) {
			return true;
		}
		if (s.length() > AbstractEditor.MAX_PASTABLE_STRING_SIZE) {
			return false;
		}

		if (sel != null) {
			Command compo = new Command(COMPOSITE, new Command[2]);
			compo.set(0, (new Command(DELETE_STRING, sel)).exec());
			compo.set(1, (new Command(INSERT_STRING, s)).exec());
			undoStack.push(compo);
		} else {
			undoStack.push((new Command(INSERT_STRING, s)).exec());
		}
		model.clearSelection();
		redoStack.setSize(0);
		editor.doSelectionChanged();
		textChanged();
		return true;
	}

	void selectAll() {
		model.setSelectionBegin(0, 0);
		int endRow = model.getLineCount() - 1;
		model.setSelectionEnd(endRow, model.getLine(endRow).length);
		editor.doSelectionChanged();
		model.setCaret(endRow, model.getLine(endRow).length);
		view.updateCursorByModel();
	}

	/* ---- Key events handlers -------------------------------------------- */

	public void keyPressed(KeyEvent e) {
		if (e.isControlDown()) {
			doControlKeyCombinations(e);
			// cannot exit method here because of Ctrl-HOME and Ctrl-END
		}

		switch (e.getKeyCode()) {
		case KeyEvent.VK_F3:
			AbstractEditor.MatchConfig mc = editor.getMatchConfig();
			if (! e.isShiftDown()) { // F3 (repeat last find in forward)
				mc.isForwardMatch = true;
			} else { // Shift-F3 (repeat last find in backward)
				mc.isForwardMatch = false;
			}
			editor.findNext();
			break;
		case KeyEvent.VK_LEFT:
		case KeyEvent.VK_RIGHT:
		case KeyEvent.VK_UP:
		case KeyEvent.VK_DOWN:
		case KeyEvent.VK_HOME:
		case KeyEvent.VK_END:
		case KeyEvent.VK_PAGE_UP:
		case KeyEvent.VK_PAGE_DOWN:
			doKeyNavigation(e);
			break;
		case KeyEvent.VK_SHIFT: // Shift is for selection
			if (model.getSelection() == null) {
				model.clearSelection();
				editor.doSelectionChanged();
			}
			isShiftPressing = true; // the selection has a beginning
			break;
		case KeyEvent.VK_DELETE:
			/*
			 *	In some old JVMs, the KEY_TYPED event is not fired when the
			 *	delete key is pressed (bug JDK-4724007). This is a workaround
			 *	for manually generating an alternative KEY_TYPED event, and
			 *	the "real" event will be filtered out in the keyTyped() method.
			 */
			editor.keyType('\u007f');
			break;
		}
	}

	private void doControlKeyCombinations(KeyEvent e) {
		switch (e.getKeyCode()) {
		case KeyEvent.VK_TAB:
			if (! e.isShiftDown()) {
				editor.doNextTab(); // Ctrl-Tab (next tab)
			} else {
				editor.doPreviousTab(); // Ctrl-Shift-Tab (previous tab)
			}
			break;
		case KeyEvent.VK_J: // Ctrl-J (join lines)
			doJoinLines();
			break;
		case KeyEvent.VK_T: // Ctrl-T (new tab)
			editor.newFile();
			break;
		case KeyEvent.VK_U: // Ctrl-U (lowercase), Ctrl-Shift-U (uppercase)
			Range sel = model.getSelection();
			if (sel != null) {
				String s1 = model.getStringByRange(sel);
				String s2;
				if (e.isShiftDown()) {
					s2 = s1.toUpperCase();
				} else {
					s2 = s1.toLowerCase();
				}
				if (! s1.equals(s2)) {
					Command compo = new Command(COMPOSITE, new Command[2]);
					compo.set(0, (new Command(DELETE_STRING, sel)).exec());
					compo.set(1, (new Command(INSERT_STRING, s2)).exec());
					undoStack.push(compo);
					redoStack.setSize(0);
					textChanged();
				}
			}
			break;
		}
	}

	private void doJoinLines() {
		Range sel = model.getSelection();
		if (sel == null) {
			int row = model.getCaretRow();
			if (row == (model.getLineCount() - 1)) {
				return;
			}
			model.setCaret(row, model.getLine(row).length);
			undoStack.push((new Command(DELETE, '\n')).exec());
		} else {
			Vector cmdVec = new Vector(); // of Command
			for (int i = sel.beginRow; i < sel.endRow; i += 1) {
				char[] line = model.getLine(sel.beginRow);
				model.setCaret(sel.beginRow, line.length);
				cmdVec.addElement((new Command(DELETE, '\n')).exec());
			}
			Command compo = new Command(COMPOSITE, new Command[cmdVec.size()]);
			for (int i = 0; i < cmdVec.size(); i += 1) {
				compo.set(i, (Command) cmdVec.elementAt(i));
			}
			undoStack.push(compo);
			model.clearSelection();
			editor.doSelectionChanged();
		}
		redoStack.setSize(0);
		textChanged();
	}

	private void doKeyNavigation(KeyEvent e) {
		int row = model.getCaretRow();
		int col = model.getCaretColumn();
		int len = model.getLine(row).length;
		int lineHeight = view.getLineHeight();

		switch (e.getKeyCode()) {
		case KeyEvent.VK_LEFT:
			if ((col == 0) && (row > 0)) {
				len = model.getLine(row - 1).length;
				model.setCaret(row - 1, len);
			} else if (col > 0) {
				model.setCaret(row, col - 1);
			}
			view.updateCursorByModel();
			magicCursorX = view.getCursorX();
			break;
		case KeyEvent.VK_RIGHT:
			if ((col == len) && ((row + 1) < model.getLineCount())) {
				model.setCaret(row + 1, 0);
			} else if (col < len) {
				model.setCaret(row, col + 1);
			}
			view.updateCursorByModel();
			magicCursorX = view.getCursorX();
			break;
		case KeyEvent.VK_UP:
			doUpDownKeyNavigation(- lineHeight);
			break;
		case KeyEvent.VK_DOWN:
			doUpDownKeyNavigation(lineHeight);
			break;
		case KeyEvent.VK_PAGE_UP:
			doUpDownKeyNavigation(- view.getSize().height + lineHeight);
			break;
		case KeyEvent.VK_PAGE_DOWN:
			doUpDownKeyNavigation(view.getSize().height);
			break;
		case KeyEvent.VK_HOME:
			if (e.isControlDown()) {
				model.setCaret(0, 0);
			} else {
				if (view.isLineWrap()) {
					int x = 0;
					int y = view.getCursorY();
					model.setCaret(view.moveCursorByPoint(x, y));
				} else {
					char[] line = model.getLine(row);
					boolean isOnlyWhitespacesBeforeCaret = true;
					while (col > 0) {
						col -= 1;
						if (! Character.isWhitespace(line[col])) {
							isOnlyWhitespacesBeforeCaret = false;
							break;
						}
					}

					model.setCaret(row, 0);
					if (! isOnlyWhitespacesBeforeCaret) {
						col = model.getCaretColumn();
						while ((col < line.length)
								&& Character.isWhitespace(line[col])) {
							col += 1;
						}
						model.setCaret(row, col);
					}
				}
			}
			view.updateCursorByModel();
			magicCursorX = view.getCursorX();
			break;
		case KeyEvent.VK_END:
			if (e.isControlDown()) {
				int endRow = model.getLineCount() - 1;
				model.setCaret(endRow, model.getLine(endRow).length);
				view.updateCursorByModel();
			} else {
				if (view.isLineWrap()) {
					int x = Integer.MAX_VALUE;
					int y = view.getCursorY();
					model.setCaret(view.moveCursorByPoint(x, y));
				} else {
					model.setCaret(row, len);
					view.updateCursorByModel();
				}
			}
			magicCursorX = view.getCursorX();
			break;
		}

		if (isShiftPressing) {
			model.setSelectionEnd(model.getCaretRow(), model.getCaretColumn());
		} else {
			model.clearSelection();
		}
		editor.doSelectionChanged();
	}

	private void doUpDownKeyNavigation(int deltaY) {
		int x = view.getCursorX();
		int y = view.getCursorY();

		if (magicCursorX != -1) {
			x = magicCursorX;
		}
		y += deltaY;

		model.setCaret(view.moveCursorByPoint(x, y));
	}

	public void keyTyped(KeyEvent e) {
		if (e.isControlDown()) { // all events are handled by keyPressed()
			return;
		}

		/*
		 *	In some cases (e.g. using the Chinese input method on Linux), the
		 *	KeyReleased event of the shift key may not be sent. If there is
		 *	still a range of selection, there will be a problem. Therefore,
		 *	this flag is forcibly turned off here.
		 */
		isShiftPressing = false;

		Range sel = model.getSelection();
		char keyChar = e.getKeyChar();

		/*
		 *	This is for a bug that the delete key will not trigger a KEY_TYPED
		 *	event. See the comment in the keyPressed() method.
		 */
		if ((keyChar == KeyEvent.VK_DELETE)
				&& (e.getSource() instanceof View)) {
			return;
		}

		/*
		 *	The escape is handled separately because no text will be changed.
		 */
		if (keyChar == KeyEvent.VK_ESCAPE) {
			if (editor.closeDialogs() == 0) {
				editor.disableMatch();
			}
			return;
		}

		switch (keyChar) {
		case KeyEvent.VK_DELETE:
			if (sel != null) {
				undoStack.push((new Command(DELETE_STRING, sel)).exec());
			} else {
				int row = model.getCaretRow();
				int col = model.getCaretColumn();
				if (col != model.getLine(row).length) {
					char c = model.getLine(row)[col];
					undoStack.push((new Command(DELETE, c)).exec());
					updateMode = UPDATE_SINGLE_LINE;
				} else { // col == line.length
					if (row != (model.getLineCount() - 1)) {
						undoStack.push((new Command(DELETE, '\n')).exec());
					} else {
						break; // does nothing and returns
					}
				}
			}
			model.clearSelection();
			break;
		case KeyEvent.VK_BACK_SPACE:
			if (sel != null) {
				undoStack.push((new Command(DELETE_STRING, sel)).exec());
			} else {
				int row = model.getCaretRow();
				int col = model.getCaretColumn();
				if (col > 0) {
					char c = model.getLine(row)[col - 1];
					undoStack.push((new Command(BACK_SPACE, c)).exec());
					updateMode = UPDATE_SINGLE_LINE;
				} else if (row > 0) { // col == 0
					undoStack.push((new Command(BACK_SPACE, '\n')).exec());
				} else {
					break; // does nothing and returns
				}
			}
			model.clearSelection();
			break;
		case KeyEvent.VK_ENTER:
		case '\r':
			if (sel != null) {
				Command compo = new Command(COMPOSITE, new Command[2]);
				compo.set(0, (new Command(DELETE_STRING, sel)).exec());
				compo.set(1, (new Command(INSERT, '\n')).exec());
				undoStack.push(compo);
			} else {
				undoStack.push((new Command(INSERT, '\n')).exec());
			}
			model.clearSelection();
			break;
		default:
			if ((keyChar == KeyEvent.VK_TAB) && (! view.isLineWrap())
					&& (sel != null) && (sel.beginRow != sel.endRow)) {
				doTabIndentOutdent(e, sel); // will reset the selection
			} else {
				if (sel != null) {
					Command compo = new Command(COMPOSITE, new Command[2]);
					compo.set(0, (new Command(DELETE_STRING, sel)).exec());
					compo.set(1, (new Command(INSERT, keyChar)).exec());
					undoStack.push(compo);
				} else { // inserts a single char
					undoStack.push((new Command(INSERT, keyChar)).exec());
					updateMode = UPDATE_SINGLE_LINE;
				}
				model.clearSelection();
			}
			break;
		}

		redoStack.setSize(0);
		editor.doSelectionChanged();
		textChanged();
	}

	private void doTabIndentOutdent(KeyEvent e, Range sel) {
		int beginRow;
		if (sel.beginColumn == model.getLine(sel.beginRow).length) {
			if (sel.beginRow < sel.endRow) {
				beginRow = sel.beginRow + 1;
			} else {
				return;
			}
		} else {
			beginRow = sel.beginRow;
		}

		int endRow;
		if (sel.endColumn == 0) {
			if (sel.beginRow < sel.endRow) {
				endRow = sel.endRow - 1;
			} else {
				return;
			}
		} else {
			endRow = sel.endRow;
		}

		Vector cmdVec = new Vector(); // of Command
		if (e.isShiftDown()) {
			Range range = new Range();
			for (int row = beginRow; row <= endRow; row += 1) {
				char[] line = model.getLine(row);
				if (line.length == 0) {
					continue;
				}

				int pos = 0;
				while (pos < editor.getTabSize()) {
					char c = line[pos];
					if (c == ' ') {
						pos += 1;
					} else if (c == '\t') {
						pos += 1;
						break;
					} else {
						break;
					}
				}
				if (pos == 0) {
					continue;
				}

				range.setBegin(row, 0);
				range.setEnd(row, pos);
				model.setCaret(row, 0);
				cmdVec.addElement((new Command(DELETE_STRING, range)).exec());
			}
		} else {
			for (int row = beginRow; row <= endRow; row += 1) {
				model.setCaret(row, 0);
				if (model.getLine(row).length != 0) {
					cmdVec.addElement((new Command(INSERT, '\t')).exec());
				}
			}
		}

		Command compo = new Command(COMPOSITE, new Command[cmdVec.size()]);
		for (int i = 0; i < cmdVec.size(); i += 1) {
			compo.set(i, (Command) cmdVec.elementAt(i));
		}
		undoStack.push(compo);

		model.setSelectionBegin(beginRow, 0);
		model.setSelectionEnd(endRow, model.getLine(endRow).length);
		model.setCaret(endRow, model.getLine(endRow).length);
		// editor.doSelectionChanged() will be called in the keyTyped() method
	}

	public void keyReleased(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
			isShiftPressing = false;
		}
	}

	/* ---- The inner class for undo/redo commands ------------------------- */

	private class Command {

		private byte type;
		private char c;
		private Object object;

		private int caretRow;
		private int caretColumn;

		Command(byte type, char c) {
			this.type = type;
			this.c = c;
			caretRow = model.getCaretRow();
			caretColumn = model.getCaretColumn();
		}

		Command(byte type, Object object) {
			this.type = type;
			this.object = object;
			caretRow = model.getCaretRow();
			caretColumn = model.getCaretColumn();
		}

		boolean isComposite() {
			return (type == COMPOSITE) ? true : false;
		}

		int getCommandCount() {
			return ((Command[]) object).length;
		}

		void set(int index, Command cmd) {
			((Command[]) object)[index] = cmd;
		}

		Command get(int index) {
			return ((Command[]) object)[index];
		}

		/*
		 *	This method executes the command and generates a reverse version of
		 *	the command, which can be used in undo.
		 */
		Command exec() {
			model.setCaret(caretRow, caretColumn);
			Command reverseCmd = null;

			switch (type) {
			case INSERT:
				model.insert(c);
				reverseCmd = new Command(BACK_SPACE, c);
				break;
			case BACK_SPACE:
				model.backSpace();
				reverseCmd = new Command(INSERT, c);
				break;
			case DELETE:
				if (caretColumn == model.getLine(caretRow).length) {
					model.setCaret(caretRow + 1, 0);
				} else {
					model.setCaret(caretRow, caretColumn + 1);
				}
				model.backSpace();
				reverseCmd = new Command(UNDELETE, c);
				break;
			case UNDELETE:
				model.insert(c);
				model.setCaret(caretRow, caretColumn);
				reverseCmd = new Command(DELETE, c);
				break;
			case INSERT_STRING:
				Range range = new Range();
				range.setBegin(caretRow, caretColumn);
				model.insertString((String) object);
				range.setEnd(model.getCaretRow(), model.getCaretColumn());
				reverseCmd = new Command(DELETE_STRING, range);
				break;
			case DELETE_STRING:
				range = (Range) object;
				String s = model.getStringByRange(range);
				model.deleteStringByRange(range);
				reverseCmd = new Command(INSERT_STRING, s);
				break;
			}

			return reverseCmd;
		}

	}

}
