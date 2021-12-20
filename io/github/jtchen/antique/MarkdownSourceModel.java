/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.util.*;

/*
 *	This is an implementation of a model that supports syntax highlighting of
 *	the Markdown markup language.
 */
class MarkdownSourceModel extends PassiveModel {

	/*
	 *	These are constants of an internal structure, which are used to store
	 *	the parsing results of each line.
	 */

	private static final int BLOCKQUOTE_TYPE = 0;
	private static final int BULLET_LIST_TYPE = 1;
	private static final int ORDERED_LIST_TYPE = 2;
	private static final int POSSIBLE_BULLET_LIST_TYPE = 3;
	private static final int POSSIBLE_ORDERED_LIST_TYPE = 4;

	/*
	 *	This is a default value of both "fixed" and "running" line styles.
	 */

	private static final byte DEFAULT = 0;

	/*
	 *	These are "fixed" line styles, which means that one of them can be
	 *	determined by the line itself without having to refer to other lines.
	 */

	private static final byte CONTINUOUS_EQUALS_SIGN = 1;
	private static final byte CONTINUOUS_MINUS_SIGN = 2;
	private static final byte HAS_SETEXT_HEADING = 3;
	private static final byte HAS_ATX_HEADING = 4;
	private static final byte HAS_THEMATIC_BREAK = 5;
	private static final byte CONTINUOUS_BACKTICK = 6;
	private static final byte CONTINUOUS_TILDE = 7;
	private static final byte REFERENCE_WITH_TITLE = 8;
	private static final byte REFERENCE_WITHOUT_TITLE = 9;
	private static final byte HTML_BLOCK_BOUNDARY = 10;
	private static final byte CODE = 11;
	private static final byte FENCED_CODE = 12;
	private static final byte TITLE = 13;
	private static final byte HTML_BLOCK = 14;

	/*
	 *	These are "running" line styles, which means they will be dynamically
	 *	changed by adjacent lines.
	 */

	private static final byte CODE_SPAN = 15;
	private static final byte EMPHASIS = 16;
	private static final byte AUTOLINK = 17;
	private static final byte LINK = 18;
	private static final byte IMAGE = 19;

	private static final byte COLOR_BLOCK = Theme.IMPORTANT;
	private static final byte COLOR_HEADING = Theme.PRIMARY_BLOCK;
	private static final byte COLOR_HTML_BLOCK = Theme.PRIMARY_BLOCK;
	private static final byte COLOR_THEMATIC_BREAK = Theme.PRIMARY_BLOCK;
	private static final byte COLOR_CODE = Theme.SECONDARY_BLOCK;
	private static final byte COLOR_EMPHASIS = Theme.PRIMARY_INLINE;
	private static final byte COLOR_LINK = Theme.SECONDARY_INLINE;

	/*
	 *	This is the data structure that holds the information of each line.
	 */

	private static class LineData {
		byte fixedStyle = DEFAULT;
		int indent = 0;
		int beginCol = 0;
		byte runningStyle = DEFAULT;
		int codeIndent = -1;
		boolean hasText = false;
		IntVector blocks = new IntVector();
		IntVector inlines = null;
	}

	private Vector lineDataVector = new Vector(); // of LineData

	MarkdownSourceModel() {
		super();
		lineDataVector.insertElementAt(getLineData(new char[0], 0), 0);
	}

	/* ---- Supporting methods for the core method `getLineData()` --------- */

	private static int findWhitespace(char[] line, int begin) {
		int pos = begin;
		while (pos < line.length) {
			if (PassiveModel.isWhitespace(line[pos])) {
				return pos;
			}
			pos += 1;
		}
		return -1;
	}

	private static int findNonWhitespace(char[] line, int begin) {
		int pos = begin;
		while (pos < line.length) {
			if (! PassiveModel.isWhitespace(line[pos])) {
				return pos;
			}
			pos += 1;
		}
		return -1;
	}

	private static int countDigits(char[] line, int begin) {
		int length = 0;
		for (int col = begin; col < line.length; col += 1) {
			if (PassiveModel.isDigit(line[col])) {
				length += 1;
			} else {
				break;
			}
		}
		return length;
	}

	private static int countChars(char[] line, int begin, char c) {
		int len = 1;
		while ((begin + len) < line.length) {
			if (line[begin + len] != c) {
				break;
			}
			len += 1;
		}
		return len;
	}

	private static int findChars(char[] line, int begin, char c, int count) {
		int pos = begin;
		while (pos < line.length) {
			pos = findChar(line, pos, c);
			if (pos < 0) {
				break;
			}
			int num = countChars(line, pos, c);
			if (num == count) {
				return pos + (count - 1);
			}
			pos += num;
		}
		return -1;
	}

	private static int findChar(char[] line, int begin, char c) {
		for (int i = begin; i < line.length; i += 1) {
			if (line[i] == '\\') {
				i += 1;
				continue;
			} else if (line[i] == c) {
				return i;
			}
		}
		return -1;
	}

	private static int checkImage(char[] line, int begin) {
		if (((begin + 1) < line.length) && (line[begin + 1] == '[')) {
			return checkLink(line, begin + 1);
		}
		return -1;
	}

	private static int checkLink(char[] line, int begin) {
		int pos = findChar(line, begin + 1, ']');
		if ((pos != -1) && ((pos + 1) < line.length)) {
			char c = line[pos + 1];
			if (c == ' ') {
				if (((pos + 2) < line.length) && (line[pos + 2] != '[')) {
					pos = -1;
				} else {
					pos = findChar(line, pos + 3, ']');
				}
			} else if (c == '[') {
				pos = findChar(line, pos + 2, ']');
			} else if (c == '(') {
				pos = findChar(line, pos + 2, ')');
			} else {
				pos = -1;
			}
		} else {
			pos = -1;
		}
		return pos;
	}

	/*
	 *	This method updates the internal data structure presenting the inline
	 *	elements of each line.
	 */
	private static IntVector addInlines(
			IntVector inlines, int type, int pos, int posEnd) {
		if (inlines == null) {
			inlines = new IntVector();
		}
		inlines.addElement(type);
		inlines.addElement(pos);
		inlines.addElement(posEnd);
		return inlines;
	}

	private static IntVector parseInline(char[] line, LineData ld, int begin) {
		IntVector inlines = null;
		int pos = begin;
		while (pos < line.length) {
			char c = line[pos];
			if (c == '`') {
				int count = countChars(line, pos, c);
				int posEnd = findChars(line, pos + count, c, count);
				if (posEnd > 0) {
					inlines = addInlines(inlines, CODE_SPAN, pos, posEnd);
					pos = posEnd;
				} else {
					pos += (count - 1);
				}
			} else if ((c == '*') || (c == '_')) {
				int count = countChars(line, pos, c);
				if (count > 2) {
					pos += (count - 1);
				} else {
					int posEnd = findChars(line, pos + count, c, count);
					if (posEnd > 0) {
						inlines = addInlines(inlines, EMPHASIS, pos, posEnd);
						pos = posEnd;
					} else {
						pos += (count - 1);
					}
				}
			} else if (c == '<') {
				int posEnd = findChar(line, (pos + 1), '>');
				if (posEnd > 0) {
					inlines = addInlines(inlines, AUTOLINK, pos, posEnd);
					pos = posEnd;
				}
			} else if (c == '!') {
				int posEnd = checkImage(line, pos);
				if (posEnd > 0) {
					inlines = addInlines(inlines, IMAGE, pos, posEnd);
					pos = posEnd;
				}
			} else if (c == '[') {
				int posEnd = checkLink(line, pos);
				if (posEnd > 0) {
					inlines = addInlines(inlines, LINK, pos, posEnd);
					pos = posEnd;
				}
			}
			pos += 1;
		}
		return inlines;
	}

	private static boolean hasThematicBreak(char[] line, int begin) {
		if (line.length > begin) {
			char c = line[begin];
			if ((c == '*') || (c == '-') || (c == '_')) {
				int count = 1;
				boolean isPossibleThematicBreak = true;
				for (int col = (begin + 1); col < line.length; col += 1) {
					if (line[col] == c) {
						count += 1;
					} else if (! PassiveModel.isWhitespace(line[col])) {
						isPossibleThematicBreak = false;
						break;
					}
				}
				if ((count >= 3) && isPossibleThematicBreak) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasTitle(char[] line, int begin) {
		boolean isQuoteCompleted = false;
		int pos = begin;
		while (pos < line.length) {
			char c = line[pos];
			if (PassiveModel.isWhitespace(c)) {
				pos += 1;
			} else if ((! isQuoteCompleted)
					&& ((c == '"') || (c == '\'') || (c == '('))) {
				int i = findChar(line, pos + 1, ((c == '(') ? ')' : c));
				if (i == -1) {
					return false;
				}
				isQuoteCompleted = true;
				pos = i + 1;
			} else {
				return false;
			}
		}
		return isQuoteCompleted;
	}

	/* ---- The core method `getLineData()` -------------------------------- */

	/*
	 *	This method parses each line (without referencing other lines) to
	 *	determine the line type. The type information is stored in the
	 *	`fixedStyle` field of the LineData class. The beginning part of each
	 *	line, which may represent the Markdown block structure, is interpreted
	 *	and the result is stored in the `blocks` field of the LineData class.
	 */
	private static LineData getLineData(char[] line, int row) {
		LineData ld = new LineData();
		IntVector blocks = ld.blocks;
		int indent = 0;
		int beginCol = 0;

		for (int col = 0; col < line.length; col += 1) {
			char c = line[col];

			if (PassiveModel.isWhitespace(c)) {
				if (c == ' ') {
					indent += 1;
				} else if (c == '\t') {
					int vCol = 0;
					for (int i = 0; i < col; i += 1) {
						if (line[i] == '\t') {
							vCol += (4 - (vCol % 4));
						} else {
							vCol += 1;
						}
					}
					indent += (4 - (vCol % 4));
				}
				beginCol += 1;
				continue;
			}

			int size = blocks.size();
			if (((c == '*') || (c == '+') || (c == '-'))
					&& ((col + 1) < line.length)
					&& PassiveModel.isWhitespace(line[col + 1])) {
				if (findNonWhitespace(line, (col + 1)) != -1) {
					/*
					 *	This is because of spaces are allowed between the
					 *	characters of a thematic break.
					 */
					if (hasThematicBreak(line, beginCol)) {
						ld.fixedStyle = HAS_THEMATIC_BREAK;
						break;
					}
					blocks.addElement(BULLET_LIST_TYPE);
					blocks.addElement(indent);
					indent += 1;
					beginCol += 1;
				}
			} else if (((c == '=') || (c == '-'))
					&& (blocks.size() == 0) && (row > 0)) {
				int len = countChars(line, col, c);
				if (! (findNonWhitespace(line, col + len) != -1)) {
					ld.fixedStyle = (c == '=') ? CONTINUOUS_EQUALS_SIGN
							: CONTINUOUS_MINUS_SIGN;
					break;
				}
			} else if ((c == '`') || (c == '~')) {
				if (countChars(line, beginCol, c) >= 3) {
					ld.fixedStyle = (c == '`')
							? CONTINUOUS_BACKTICK : CONTINUOUS_TILDE;
					break;
				}
			} else if (c == '#') {
				int count = 1;
				boolean isValidHeading = false;
				while ((col + count) < line.length) {
					if (line[col + count] == '#') {
						count += 1;
						continue;
					} else if (PassiveModel.isWhitespace(line[col + count])
							&& (count <= 6)) {
						isValidHeading = true;
					}
					break;
				}
				if (isValidHeading) {
					ld.fixedStyle = HAS_ATX_HEADING;
					break;
				}
			} else if (c == '>') {
				blocks.addElement(BLOCKQUOTE_TYPE);
				blocks.addElement(indent);
				indent += 1;
				beginCol += 1;
			} else if (PassiveModel.isDigit(c)) {
				int len = countDigits(line, col);
				if (((col + len + 1) < line.length) && (line[col + len] == '.')
						&& PassiveModel.isWhitespace(line[col + len + 1])) {
					blocks.addElement(ORDERED_LIST_TYPE);
					blocks.addElement(indent);
					col += len;
					indent += (len + 1);
					beginCol += (len + 1);
				}
			} else if (c == '[') {
				int pos = findChar(line, (col + 1), ']');
				if ((pos > 0) && ((pos + 1) < line.length)
						&& (line[pos + 1] == ':')) {
					boolean hasSpaceAfterUrl = false;
					boolean hasTitle = false;
					if ((pos = findNonWhitespace(line, pos + 2)) != -1) {
						pos = findWhitespace(line, pos);
						if (pos != -1) {
							hasSpaceAfterUrl = true;
							hasTitle = hasTitle(line, pos);
						}

						if (hasTitle) {
							ld.fixedStyle = REFERENCE_WITH_TITLE;
							break;
						} else if (! hasSpaceAfterUrl) {
							ld.fixedStyle = REFERENCE_WITHOUT_TITLE;
							break;
						} else {
							if (! (findNonWhitespace(line, (pos + 1)) != -1)) {
								ld.fixedStyle = REFERENCE_WITHOUT_TITLE;
								break;
							}
						}
					}
				}
			} else if ((c == '<') && (col == 0)) {
				ld.fixedStyle = HTML_BLOCK_BOUNDARY;
				break;
			}
			if (size == blocks.size()) {
				if (line.length > col) {
					ld.hasText = true;
					ld.inlines = parseInline(line, ld, col);
				}
				break;
			}
		}
		ld.indent = indent;
		ld.beginCol = beginCol;

		if ((ld.fixedStyle != HAS_THEMATIC_BREAK)
				&& (ld.fixedStyle != CONTINUOUS_MINUS_SIGN)
				&& hasThematicBreak(line, beginCol)) {
			ld.fixedStyle = HAS_THEMATIC_BREAK;
		}

		return ld;
	}

	/* ---- Override the basic operations of PassiveModel ------------------ */

	void setLine(char[] line, int row) {
		super.setLine(line, row);
		lineDataVector.setElementAt(getLineData(line, row), row);
	}

	void insertLine(char[] line, int row) {
		super.insertLine(line, row);
		lineDataVector.insertElementAt(getLineData(line, row), row);
	}

	void removeLine(int row) {
		super.removeLine(row);
		lineDataVector.removeElementAt(row);
	}

	/* ---- Supporting methods for the core method `modified()` ------------ */

	private static boolean isList(int type) {
		return ((type == BULLET_LIST_TYPE) || (type == ORDERED_LIST_TYPE));
	}

	private static boolean isPossibleList(int type) {
		return ((type == POSSIBLE_BULLET_LIST_TYPE)
				|| (type == POSSIBLE_ORDERED_LIST_TYPE));
	}

	private static boolean isBreakingBlankLine(int indent, LineData ld) {
		return ((indent <= 0) && (! ld.hasText));
	}

	/*
	 *	The parsing information of each line is incomplete, because the
	 *	getLineData() method is designed to not reference other lines. This
	 *	method traverses each line, from the beginning to the end of the entire
	 *	Markdown file, to determine the "real" type of each line. A data
	 *	structure `openVector` represents the status of the current line which
	 *	can be determined by all the lines accessed above.
	 */
	private static void computeOpenVector(IntVector oVec, LineData ld) {
		IntVector blocks = ld.blocks;

		/*
		 *	The first phase of parsing is to close opening blocks.
		 */

		for (int i = (oVec.size() - 2); i >= 0; i -= 2) {
			int oType = oVec.elementAt(i);
			int oTypePos = oVec.elementAt(i + 1);
			int indent = ld.indent - oTypePos;
			if ((oType == BLOCKQUOTE_TYPE)
					&& isBreakingBlankLine(indent, ld)) {
				oVec.setSize(i);
			} else if (isList(oType) && isBreakingBlankLine(indent, ld)) {
				oVec.setSize(i);
				oVec.addElement((oType == BULLET_LIST_TYPE)
						? POSSIBLE_BULLET_LIST_TYPE
						: POSSIBLE_ORDERED_LIST_TYPE);
				oVec.addElement(oTypePos);
			} else if (isPossibleList(oType)) {
				if (ld.hasText) {
					oVec.setSize(i);
					if (indent >= 4) {
						oVec.addElement((oType == POSSIBLE_BULLET_LIST_TYPE)
								? BULLET_LIST_TYPE : ORDERED_LIST_TYPE);
						oVec.addElement(oTypePos);
					}
				}
			} else {
				break;
			}
		}

		/*
		 *	The second phase of parsing is to add new blocks.
		 */

		int index = 0;
		for (int i = 0; i < oVec.size(); i += 2) {
			int oType = oVec.elementAt(i);
			int oTypePos = oVec.elementAt(i + 1);
			if (index < blocks.size()) {
				if ((blocks.elementAt(index) == oType)
						&& (blocks.elementAt(index + 1) == oTypePos)) {
					index += 2;
				}
			}
		}
		for (int i = index; i < blocks.size(); i += 2) {
			oVec.addElement(blocks.elementAt(i));
			oVec.addElement(blocks.elementAt(i + 1));
		}
	}

	/*
	 *	This method calculates the `codeIndent` of each line, which is mainly
	 *	(and can only be) determined by the information in the `openVector`.
	 *	When the current line is not in a code block, this method returns -1.
	 */
	private static int computeCodeIndent(IntVector oVec,
			LineData ld, int codeIndent, int oVecState) {

		int indent = (ld.blocks.size() == 0)
				? ld.indent : ld.blocks.elementAt(1);

		if (codeIndent > 0) { // the code block is still open
			if (indent >= codeIndent) {
				oVec.setSize(oVecState);
				return codeIndent;
			}
			codeIndent = -1; // prepare to find a new value
		}

		if ((oVec.size() == 0) && (indent >= 4)) {
			return indent;
		}
		if ((oVec.size() > 0) && (oVec.elementAt(1) >= 4)) {
			if (indent >= oVec.elementAt(1)) {
				return oVec.elementAt(1);
			}
		}

		for (int i = oVec.size(); i >= 4; i -= 2) {
			int oType = oVec.elementAt(i - 4);
			int offset = oVec.elementAt(i - 1) - oVec.elementAt(i - 3);
			if (isCodeBlockByOffset(oType, offset)) {
				codeIndent = oVec.elementAt(i - 1);
				oVec.setSize(i - 2);
			}
		}
		if (codeIndent >= 0) { // found a new value
			return codeIndent;
		}

		if (oVec.size() >= 2) {
			int oType = oVec.elementAt(oVec.size() - 2);
			int offset = ld.indent - oVec.elementAt(oVec.size() - 1);
			if (isCodeBlockByOffset(oType, offset)) {
				codeIndent = ld.indent;
			}
		}
		if (codeIndent >= 0) { // found a new value
			return codeIndent;
		}

		return -1;

	}

	private static boolean isCodeBlockByOffset(int type, int offset) {
		if ((type == BLOCKQUOTE_TYPE) && (offset >= 6)) {
			return true;
		}
		if (isList(type) && (offset >= 8)) {
			return true;
		}
		return false;
	}

	/* ---- The core method `modified()` ----------------------------------- */

	void modified() {

		IntVector oVec = new IntVector();
		int codeIndent = -1; // a negative value as a flag

		for (int row = 0; row < getLineCount(); row += 1) {
			LineData ld = (LineData) lineDataVector.elementAt(row);
			ld.runningStyle = DEFAULT;

			int oVecState = oVec.size();
			computeOpenVector(oVec, ld); // each line should be evaluated
			codeIndent = computeCodeIndent(oVec, ld, codeIndent, oVecState);

			if (codeIndent >= 0) {
				ld.runningStyle = CODE;
			} else {
				if (ld.fixedStyle == REFERENCE_WITH_TITLE) {
					continue;
				} else if (ld.fixedStyle == REFERENCE_WITHOUT_TITLE) {
					if ((row + 1) < getLineCount()) {
						char[] lineDown = getLine(row + 1);
						if (hasTitle(lineDown, 0)) {
							LineData ldDown = (LineData)
									lineDataVector.elementAt(row + 1);
							ldDown.runningStyle = TITLE;
							ldDown.codeIndent = -1;
							row += 1;
						}
					}
					continue;
				} else if ((ld.fixedStyle == CONTINUOUS_BACKTICK)
						|| (ld.fixedStyle == CONTINUOUS_TILDE)) {
					int indent = ld.indent;
					int lineCount = 1;
					boolean isFencedCodeBlock = false;
					while ((row + lineCount) < getLineCount()) {
						LineData ldEnd = (LineData)
								lineDataVector.elementAt(row + lineCount);
						if ((ldEnd.fixedStyle == ld.fixedStyle)
								&& (ldEnd.indent <= indent)) {
							isFencedCodeBlock = true;
							break;
						}
						ldEnd.runningStyle = FENCED_CODE;
						ldEnd.codeIndent = -1;
						lineCount += 1;
					}
					if (isFencedCodeBlock) {
						row += lineCount;
						continue;
					}
				} else if (ld.fixedStyle == HTML_BLOCK_BOUNDARY) {
					boolean isValidHtmlBlockBegin = false;
					if (row == 0) {
						isValidHtmlBlockBegin = true;
					} else {
						LineData ldUp = (LineData)
								lineDataVector.elementAt(row - 1);
						if ((ldUp.blocks.size() == 0) && (! ldUp.hasText)) {
							isValidHtmlBlockBegin = true;
						}
					}

					if (isValidHtmlBlockBegin) {
						int lineCount = 1;
						boolean isValidHtmlBlock = false;
						while ((row + lineCount) < getLineCount()) {
							int end = row + lineCount;
							LineData ldBoundary = (LineData)
									lineDataVector.elementAt(end - 1);
							LineData ldEnd = (LineData)
									lineDataVector.elementAt(end);
							if ((ldBoundary.fixedStyle == HTML_BLOCK_BOUNDARY)
									&& (ldEnd.blocks.size() == 0)
									&& (! ldEnd.hasText)) {
								isValidHtmlBlock = true;
								break;
							}
							ldEnd.runningStyle = HTML_BLOCK;
							ldEnd.codeIndent = -1;
							lineCount += 1;
						}
						if (isValidHtmlBlock) {
							row += lineCount;
							continue;
						}
						if ((row + lineCount) == getLineCount()) {
							break;
						}
					}
				}
			}

			ld.codeIndent = codeIndent;

			/*
			 *	The Markdown setext heading is determined here because it needs
			 *	the information of the previous line.
			 */
			if (row > 0) {
				LineData ldUp = (LineData) lineDataVector.elementAt(row - 1);
				if (ldUp.fixedStyle == HAS_SETEXT_HEADING) {
					ldUp.fixedStyle = DEFAULT; // reset the fixedStyle here
				}
				if (((ld.fixedStyle == CONTINUOUS_EQUALS_SIGN)
						|| (ld.fixedStyle == CONTINUOUS_MINUS_SIGN))
						&& (ld.runningStyle != CODE)
						&& (ldUp.fixedStyle == DEFAULT)) {
					// the only place to set the HAS_SETEXT_HEADING fixedStyle
					ldUp.fixedStyle = HAS_SETEXT_HEADING;
				}
			}
		}

	}

	/* ---- The methods for assigning the color codes of a line ------------ */

	byte[] computeColorCodes(byte[] colorCodes, int row) {
		char[] line = getLine(row);
		LineData ld = (LineData) lineDataVector.elementAt(row);

		/*
		 *	Assigning colors by the "running style" of the line.
		 */

		if (ld.runningStyle != DEFAULT) {
			if (ld.runningStyle == TITLE) {
				doColor(colorCodes, 0, line.length, COLOR_LINK);
			} else if (ld.runningStyle == FENCED_CODE) {
				doColor(colorCodes, 0, line.length, COLOR_CODE);
			} else if (ld.runningStyle == CODE) {
				int beginCol = 0;
				int codeIndent = 0;
				for (int col = 0; col < line.length; col += 1) {
					if (line[col] == '\t') {
						codeIndent += (4 - (codeIndent % 4));
					} else {
						codeIndent += 1;
					}
					if (codeIndent > ld.codeIndent) {
						beginCol = col;
						break;
					}
				}
				if (ld.blocks.size() > 0) {
					doColor(colorCodes, 0, beginCol, COLOR_BLOCK);
				}
				doColor(colorCodes, beginCol, line.length, COLOR_CODE);
			} else if (ld.runningStyle == HTML_BLOCK) {
				doColor(colorCodes, 0, line.length, COLOR_HTML_BLOCK);
			}
			return colorCodes;
		}

		/*
		 *	Assigning colors of the beginning part of the line, which
		 *	presenting the Markdown block elements.
		 */

		if (ld.blocks.size() > 0) {
			doColor(colorCodes, 0, ld.beginCol, COLOR_BLOCK);
		}

		/*
		 *	Assigning colors of the Markdown inline elements.
		 */

		if ((ld.fixedStyle == DEFAULT) && (ld.inlines != null)) {
			for (int i = 0; i < ld.inlines.size(); i += 3) {
				int type = ld.inlines.elementAt(i);
				int begin = ld.inlines.elementAt(i + 1);
				int end = ld.inlines.elementAt(i + 2) + 1;
				byte color = 0;
				if (type == CODE_SPAN) {
					color = COLOR_CODE;
				} else if (type == EMPHASIS) {
					color = COLOR_EMPHASIS;
				} else if (type == AUTOLINK) {
					color = COLOR_LINK;
				} else if (type == LINK) {
					color = COLOR_LINK;
				} else if (type == IMAGE) {
					color = COLOR_LINK;
				}
				doColor(colorCodes, begin, end, color);
			}
		}

		/*
		 *	Assigning colors by the "fixed style" of the line.
		 */

		byte color = 0;
		if ((ld.fixedStyle == CONTINUOUS_BACKTICK)
				|| (ld.fixedStyle == CONTINUOUS_TILDE)) {
			color = COLOR_CODE;
		} else if ((ld.fixedStyle == CONTINUOUS_EQUALS_SIGN)
				|| (ld.fixedStyle == CONTINUOUS_MINUS_SIGN)) {
			LineData ldUp = (LineData) lineDataVector.elementAt(row - 1);
			if ((ldUp.fixedStyle == HAS_SETEXT_HEADING)
					&& (ldUp.runningStyle != CODE)) {
				if (ldUp.hasText) {
					// interpreted as the underline of heading
					color = COLOR_HEADING;
				} else if (ld.fixedStyle == CONTINUOUS_MINUS_SIGN) {
					// interpreted as a thematic break
					color = COLOR_THEMATIC_BREAK;
				}
			}
		} else if ((ld.fixedStyle == HAS_SETEXT_HEADING)
				|| (ld.fixedStyle == HAS_ATX_HEADING)) {
			color = COLOR_HEADING;
		} else if (ld.fixedStyle == HAS_THEMATIC_BREAK) {
			color = COLOR_THEMATIC_BREAK;
		} else if ((ld.fixedStyle == REFERENCE_WITH_TITLE)
				|| (ld.fixedStyle == REFERENCE_WITHOUT_TITLE)) {
			color = COLOR_LINK;
		} else if (ld.fixedStyle == HTML_BLOCK_BOUNDARY) {
			color = COLOR_HTML_BLOCK;
		}
		if (color > 0) {
			doColor(colorCodes, ld.beginCol, line.length, color);
		}
		return colorCodes;
	}

	private static void doColor(byte[] colorCodes, int begin, int end,
			byte color) {
		for (int col = begin; col < end; col += 1) {
			colorCodes[col] = color;
		}
	}

}
