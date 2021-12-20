/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.util.*;

/*
 *	This is the implementation of the model. Since this class will be the basis
 *	for other classes, and it will be used to edit plain text, only search
 *	result highlighting and messy whitespace hinting are implemented. In other
 *	cases, the method getColorCodes() will returns Theme.FOREGROUND_COLOR.
 */
class PassiveModel extends AbstractModel {

	private char[] emptyLine = new char[0];

	private Vector lineVector = new Vector(); // of char[]
	private Range selection = new Range();
	private int charCount = 0;

	private AbstractEditor.MatchConfig matchConfig = null;

	private Caret caret = new Caret();

	private static class Caret {
		private int row = 0;
		private int column = 0;
		int getRow() {
			return row;
		}
		int getColumn() {
			return column;
		}
		void set(int row, int column) {
			this.row = row;
			this.column = column;
		}
	}

	PassiveModel() {
		lineVector.insertElementAt(emptyLine, 0);
	}

	void cloneFrom(AbstractModel model) {
		int lc = model.getLineCount();
		Range range = new Range();
		range.setEnd(lc - 1, model.getLine(lc - 1).length);

		insertString(model.getStringByRange(range));
		Range sel = model.getSelection();
		if (sel != null) {
			setSelectionBegin(sel.beginRow, sel.beginColumn);
			setSelectionEnd(sel.endRow, sel.endColumn);
		}
		setCaret(model.getCaretRow(), model.getCaretColumn());
	}

	int getCharCount() {
		return charCount;
	}

	String getStringByRange(Range range) {
		range = range.getForwardRange();
		int beginRow = range.beginRow;
		int beginCol = range.beginColumn;
		int endRow = range.endRow;
		int endCol = range.endColumn;
		StringBuffer sb = new StringBuffer();

		for (int row = beginRow; row <= endRow; row += 1) {
			char[] line = getLine(row);
			int begin = (row == beginRow) ? beginCol : 0;
			int end = (row == endRow) ? endCol : line.length;
			for (int col = begin; col < end; col += 1) {
				sb.append(line[col]);
			}
			if ((row + 1) <= endRow) {
				sb.append('\n');
			}
		}

		return sb.toString();
	}

	/* ---- Convenience methods for lines and the caret -------------------- */

	char[] getLine(int row) {
		return (char[]) lineVector.elementAt(row);
	}

	int getLineCount() {
		return lineVector.size();
	}

	int getCaretRow() {
		return caret.getRow();
	}

	int getCaretColumn() {
		return caret.getColumn();
	}

	void setCaret(int[] pos) {
		setCaret(pos[0], pos[1]);
	}

	void setCaret(int row, int col) {
		caret.set(row, col);
	}

	/* ---- Convenience methods to highlight the syntax -------------------- */

	static boolean isDigit(char c) {
		return ((c >= '0') && (c <= '9')) ? true : false;
	}

	static boolean isWhitespace(char c) {
		return ((c == ' ') || (c == '\t'));
	}

	/* ---- The methods to highlight the syntax ---------------------------- */

	private static void applyMask(
			byte[] colorCodes, int begin, int end, byte mask) {
		for (int col = begin; col < end; col += 1) {
			colorCodes[col] |= mask;
		}
	}

	private byte[] highlightMatchTarget(byte[] colorCodes, int row) {
		char[] line = getLine(row);
		if ((matchConfig != null) && (matchConfig.target.length() > 0)) {
			String target = matchConfig.target;
			int pos = matchConfig.isForwardMatch ? 0 : line.length - 1;
			while (true) {
				int begin = matchConfig.isForwardMatch
						? indexOf(line, target, pos)
						: lastIndexOf(line, target, pos);
				if (begin == -1) { // not matched in the current line
					break;
				}
				int end = begin + target.length();
				applyMask(colorCodes, begin, end, Theme.MATCH_MASK);
				pos = matchConfig.isForwardMatch
						? end : begin - target.length();
			}
		}
		return colorCodes;
	}

	private byte[] highlightMessyWhitespace(byte[] colorCodes, int row) {
		char[] line = getLine(row);
		if (line.length == 0) {
			return colorCodes;
		}

		int headPos = 0;
		int headSpaceCount = 0;
		for (int col = 0; col < line.length; col += 1) {
			if (Character.isWhitespace(line[col])) {
				headSpaceCount = col + 1;
			} else {
				headPos = col;
				break;
			}
		}
		if (headSpaceCount == line.length) { // this line is empty
			applyMask(colorCodes, 0, line.length, Theme.MESSY_WHITESPACE_MASK);
			return colorCodes;
		}

		int tailPos = (line.length - 1);
		int tailSpaceCount = 0;
		for (int col = (line.length - 1); col >= 0; col -= 1) {
			if (Character.isWhitespace(line[col])) {
				tailSpaceCount = line.length - col;
			} else {
				tailPos = col;
				break;
			}
		}

		if (tailPos > headPos) { // marks a series of spaces
			for (int col = (headPos + 1); col < tailPos; col += 1) {
				if (line[col] == ' ') {
					int end = col + 1;
					while (end < line.length) {
						if (line[end] == ' ') {
							end += 1;
						} else {
							break;
						}
					}
					if ((end - col) > 1) {
						applyMask(colorCodes, col, end,
								Theme.MESSY_WHITESPACE_MASK);
					}
					col = end;
				}
			}
		}

		if (tailSpaceCount > 0) { // marks trailing spaces
			applyMask(colorCodes, tailPos, line.length,
					Theme.MESSY_WHITESPACE_MASK);
		}

		return colorCodes;
	}

	synchronized byte[] getColorCodes(int row) {
		char[] line = getLine(row);
		byte[] colorCodes = new byte[line.length];
		colorCodes = computeColorCodes(colorCodes, row);
		for (int col = 0; col < line.length; col += 1) {
			colorCodes[col] &= Theme.COLOR_MASK;
		}
		colorCodes = highlightMatchTarget(colorCodes, row);
		colorCodes = highlightMessyWhitespace(colorCodes, row);
		return colorCodes;
	}

	byte[] computeColorCodes(byte[] colorCodes, int row) {
		return colorCodes;
	}

	/* ---- Selection methods ---------------------------------------------- */

	boolean isSelected() {
		if ((selection.beginRow == selection.endRow)
				&& (selection.beginColumn == selection.endColumn)) {
			return false;
		}
		return true;
	}

	Range getSelection() {
		return (isSelected()) ? selection.getForwardRange() : null;
	}

	void setSelectionBegin(int row, int col) {
		selection.setBegin(row, col);
	}

	void setSelectionEnd(int row, int col) {
		selection.setEnd(row, col);
	}

	synchronized void clearSelection() {
		int row = getCaretRow();
		int col = getCaretColumn();
		selection.setBegin(row, col);
		selection.setEnd(row, col);
	}

	/* ---- Methods for finding and matching the search results ------------ */

	private boolean isWordPart(char c) {
		return Character.isUnicodeIdentifierPart(c);
	}

	private boolean isMatch(char c1, char c2) {
		if (matchConfig.isCaseSensitiveMatch) {
			return (c1 == c2);
		}
		return (Character.toLowerCase(c1) == Character.toLowerCase(c2));
	}

	private boolean isMatch(char[] line, String target, int fromPos) {
		if (matchConfig.isWholeWordMatch) {
			int end = fromPos + target.length();
			if (((fromPos > 0) && isWordPart(line[fromPos - 1]))
					|| ((end < line.length) && isWordPart(line[end]))) {
				return false;
			}
		}
		if (isMatch(line[fromPos], target.charAt(0))) {
			return (getMatchLength(line, target, fromPos) == target.length());
		}
		return false;
	}

	private int getMatchLength(char[] line, String target, int fromPos) {
		int pos = 1;
		while (pos < target.length()) {
			if (! isMatch(line[fromPos + pos], target.charAt(pos))) {
				break;
			}
			pos += 1;
		}
		return pos;
	}

	/*
	 *	This method will only be called with a target string consisting of at
	 *	least one char.
	 */
	private int indexOf(char[] line, String target, int fromPos) {
		for (int i = fromPos; i <= (line.length - target.length()); i += 1) {
			if (isMatch(line, target, i)) {
				return i;
			}
		}
		return -1;
	}

	/*
	 *	This method will only be called with a target string consisting of at
	 *	least one char.
	 */
	private int lastIndexOf(char[] line, String target, int fromPos) {
		int pos = Math.min(fromPos, line.length - target.length());
		for (int i = pos; i >= 0; i -= 1) {
			if (isMatch(line, target, i)) {
				return i;
			}
		}
		return -1;
	}

	/*
	 *	This method will only be called when mc.target.length() > 0.
	 */
	int countMatch(AbstractEditor.MatchConfig mc) {
		matchConfig = mc;

		String target = matchConfig.target;
		int count = 0;
		int lastPos = 0;
		for (int row = 0; row < getLineCount(); row += 1) {
			char[] line = getLine(row);
			while (true) {
				int pos = indexOf(line, target, lastPos);
				if (pos == -1) { // not matched in the current line
					lastPos = 0;
					break;
				}
				lastPos = pos + target.length();
				count += 1;
			}
		}
		return count;
	}

	/*
	 *	This method will only be called after there is at least one match.
	 */
	boolean isCaretAtMatchEnd() {
		String target = matchConfig.target;
		char[] line = getLine(getCaretRow());
		int begin = matchConfig.isForwardMatch
				? getCaretColumn() - target.length() : getCaretColumn();
		if (begin < 0) {
			return false;
		}
		return (indexOf(line, target, begin) == begin);
	}

	void moveCaretToNextMatch() {
		int cRow = getCaretRow();
		if (matchConfig.isForwardMatch) {
			for (int row = cRow; row < getLineCount(); row += 1) {
				if (moveCaretToNextMatch(row, false)) {
					return;
				}
			}
			for (int row = 0; row <= cRow; row += 1) {
				if (moveCaretToNextMatch(row, true)) {
					return;
				}
			}
		} else {
			for (int row = cRow; row >= 0; row -= 1) {
				if (moveCaretToNextMatch(row, false)) {
					return;
				}
			}
			for (int row = (getLineCount() - 1); row >= cRow; row -= 1) {
				if (moveCaretToNextMatch(row, true)) {
					return;
				}
			}
		}
	}

	/*
	 *	This method will only be called after there is at least one match.
	 */
	private boolean moveCaretToNextMatch(int row, boolean isWrapped) {
		String target = matchConfig.target;
		int cRow = getCaretRow();
		int cCol = getCaretColumn();
		char[] line = getLine(row);
		int pos = matchConfig.isForwardMatch ? 0 : line.length - 1;
		while (true) {
			int begin = matchConfig.isForwardMatch
					? indexOf(line, target, pos)
					: lastIndexOf(line, target, pos);
			if (begin == -1) { // not matched in the current line
				break;
			}
			int end = begin + target.length();
			boolean isNext;
			if (matchConfig.isForwardMatch) {
				pos = end;
				isNext = (row > cRow) || (end > cCol)
						|| ((cCol < end) && (begin <= cCol));
			} else {
				pos = begin - target.length();
				isNext = (row < cRow) || (end < cCol);
			}
			if (((! isWrapped) && isNext) || (isWrapped)) {
				setCaret(row, end);
				return true;
			}
		}
		return false;
	}

	void disableMatch() {
		if (matchConfig != null) {
			matchConfig.target = "";
		}
	}

	/* ---- Three basic operations of the lineVector ----------------------- */

	void setLine(char[] line, int row) {
		lineVector.setElementAt(line, row);
	}

	void insertLine(char[] line, int row) {
		lineVector.insertElementAt(line, row);
	}

	void removeLine(int row) {
		lineVector.removeElementAt(row);
	}

	/* ---- Basic operations for editing the model ------------------------- */

	/*
	 *	This method is intended to be overwritten for syntax highlighting.
	 */
	void modified() {}

	void insert(char c) {
		charCount += 1;

		int row = getCaretRow();
		int col = getCaretColumn();
		char[] line = getLine(row);

		if (c == '\n') {
			char[] pre = new char[col];
			char[] post = new char[line.length - col];
			System.arraycopy(line, 0, pre, 0, col);
			System.arraycopy(line, col, post, 0, line.length - col);
			setLine(pre, row);
			insertLine(post, row + 1);
			setCaret(row + 1, 0);
		} else {
			char[] temp = new char[line.length + 1];
			System.arraycopy(line, 0, temp, 0, col);
			temp[col] = c;
			System.arraycopy(line, col, temp, col + 1, line.length - col);
			setLine(temp, row);
			setCaret(row, col + 1);
		}
		modified();
	}

	void backSpace() {
		charCount -= 1;

		int row = getCaretRow();
		int col = getCaretColumn();
		char[] line = getLine(row);

		if (col > 0) {
			char[] temp = new char[line.length - 1];
			System.arraycopy(line, 0, temp, 0, col - 1);
			System.arraycopy(line, col, temp, col - 1, line.length - col);
			setLine(temp, row);
			setCaret(row, col - 1);
			modified();
		} else { // col == 0
			if (row > 0) {
				char[] preLine = getLine(row - 1);
				char[] temp = new char[preLine.length + line.length];
				System.arraycopy(preLine, 0, temp, 0, preLine.length);
				System.arraycopy(line, 0, temp, preLine.length, line.length);
				setLine(temp, row - 1);
				removeLine(row);
				setCaret(row - 1, preLine.length);
				modified();
			}
		}
	}

	void insertString(String s) {
		if (s.length() == 0) {
			return;
		}

		charCount += s.length();

		int row = getCaretRow();
		int col = getCaretColumn();
		char[] line = getLine(row);
		char[] pre = new char[col];
		char[] post = new char[line.length - col];
		System.arraycopy(line, 0, pre, 0, col);
		System.arraycopy(line, col, post, 0, line.length - col);

		StringBuffer sb = new StringBuffer();
		sb.append(pre);

		for (int pos = 0; pos < s.length(); pos += 1) {
			char c = s.charAt(pos);
			if (c == '\n') {
				int len = sb.length();
				if (len == 0) {
					insertLine(emptyLine, row);
				} else {
					char[] temp = new char[len];
					sb.getChars(0, len, temp, 0);
					insertLine(temp, row);
				}
				sb.setLength(0);
				row += 1;
			} else {
				sb.append(c);
			}
		}

		int newCol = sb.length();

		sb.append(post);
		int len = sb.length();
		if (len == 0) {
			setLine(emptyLine, row);
		} else {
			char[] temp = new char[len];
			sb.getChars(0, len, temp, 0);
			setLine(temp, row);
		}

		setCaret(row, newCol);
		modified();
	}

	private int getLengthByRange(Range range) {
		range = range.getForwardRange();
		int beginRow = range.beginRow;
		int beginCol = range.beginColumn;
		int endRow = range.endRow;
		int endCol = range.endColumn;
		int len = 0;

		for (int row = beginRow; row <= endRow; row += 1) {
			char[] line = getLine(row);
			int begin = (row == beginRow) ? beginCol : 0;
			int end = (row == endRow) ? endCol : line.length;
			len += (end - begin);
			if ((row + 1) <= endRow) {
				len += 1;
			}
		}

		return len;
	}

	void deleteStringByRange(Range range) {
		charCount -= getLengthByRange(range);

		range = range.getForwardRange();
		int beginRow = range.beginRow;
		int beginCol = range.beginColumn;
		int endRow = range.endRow;
		int endCol = range.endColumn;

		char[] endLine = getLine(endRow);
		int len = beginCol + (endLine.length - endCol);

		if (len == 0) {
			setLine(emptyLine, beginRow);
		} else {
			char[] beginLine = getLine(beginRow);
			char[] temp = new char[len];
			System.arraycopy(beginLine, 0, temp, 0, beginCol);
			System.arraycopy(endLine, endCol, temp, beginCol, len - beginCol);
			setLine(temp, beginRow);
		}

		while (beginRow < endRow) {
			removeLine(beginRow + 1);
			endRow -= 1;
		}

		setCaret(beginRow, beginCol);
		modified();
	}

}
