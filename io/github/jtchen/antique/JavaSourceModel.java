/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.util.*;

/*
 *	This is an implementation of a model that supports syntax highlighting of
 *	the Java programming language.
 */
class JavaSourceModel extends PassiveModel {

	private static final byte COLOR_BRACKET = Theme.IMPORTANT;
	private static final byte COLOR_KEYWORD = Theme.PRIMARY_BLOCK;
	private static final byte COLOR_COMMENT = Theme.SECONDARY_BLOCK;
	private static final byte COLOR_OPERATOR = Theme.PRIMARY_INLINE;
	private static final byte COLOR_LITERAL = Theme.SECONDARY_INLINE;

	private static String[][] keywords = new String[13][];
	static {
		keywords[2] = new String[] { "do", "if" };
		keywords[3] = new String[] { "for", "int", "new", "try" };
		keywords[4] = new String[] { "byte", "case", "char", "else", "goto",
				"long", "this", "void" };
		keywords[5] = new String[] { "break", "catch", "class", "const",
				"final", "float", "short", "super", "throw", "while" };
		keywords[6] = new String[] { "double", "import", "native", "public",
				"return", "static", "switch", "throws" };
		keywords[7] = new String[] { "boolean", "default", "extends",
				"finally", "package", "private" };
		keywords[8] = new String[] { "abstract", "continue", "volatile" };
		keywords[9] = new String[] { "interface", "protected", "transient" };
		keywords[10] = new String[] { "implements", "instanceof" };
		keywords[12] = new String[] { "synchronized" };
	}

	private static final String OPERATORS = "=><!~?:+-*/&|^%";

	private static String[] numberPatterns = new String[] {
		"0[lL]?",
		"[1-9]([0-9]+)?[lL]?",
		"0[xX][0-9a-fA-F]+[lL]?",
		"0[0-7]+[lL]?",
		"[0-9]+.([0-9]+)?([eE][+-]?[0-9]+)?[fFdD]?",
		".[0-9]+([eE][+-]?[0-9]+)?[fFdD]?",
		"[0-9]+([eE][+-]?[0-9]+)[fFdD]?",
		"[0-9]+([eE][+-]?[0-9]+)?[fFdD]"
	};
	private Vector numberRuleVector = new Vector(); // of Rule

	private Vector linePropertyVector = new Vector(); // of LineProperty
	private Vector commentRangeVector = new Vector(); // of Range

	private static class LineProperty {
		IntVector commentBeginVector;
		IntVector commentEndVector;
	}

	private Range bracketedRange = null;
	private char[] beginBrackets = new char[] { '(', '[', '{' };
	private char[] endBrackets = new char[] { ')', ']', '}' };

	JavaSourceModel() {
		super();
		linePropertyVector.insertElementAt(getLineProperty(new char[0]), 0);

		for (int i = 0; i < numberPatterns.length; i += 1) {
			Rule rule = new Rule(parseRule(numberPatterns[i]));
			numberRuleVector.addElement(rule);
		}
	}

	private LineProperty getLineProperty(char[] line) {
		LineProperty lp = new LineProperty();
		for (int col = 0; col < (line.length - 1); col += 1) {
			if ((line[col] == '/') && (line[col + 1] == '*')) {
				if (lp.commentBeginVector == null) {
					lp.commentBeginVector = new IntVector();
				}
				lp.commentBeginVector.addElement(col);
			} else if ((line[col] == '*') && (line[col + 1] == '/')) {
				if (lp.commentEndVector == null) {
					lp.commentEndVector = new IntVector();
				}
				lp.commentEndVector.addElement(col + 1);
			}
		}
		return lp;
	}

	void setCaret(int row, int col) {
		super.setCaret(row, col);

		Range range = null;
		char[] line = getLine(row);
		loop: for (int i = col; i >= (col - 1); i -= 1) {
			if ((i >= 0) && (line.length > i)) {
				for (int j = 0; j < beginBrackets.length; j += 1) {
					if ((range = getBracketedRange(row, i,
							beginBrackets[j], endBrackets[j])) != null) {
						break loop;
					} else if ((range = getBracketedRange(row, i,
							endBrackets[j], beginBrackets[j])) != null) {
						break loop;
					}
				}
			}
		}
		bracketedRange = range;
	}

	/* ---- Override the basic operations of PassiveModel ------------------ */

	void setLine(char[] line, int row) {
		super.setLine(line, row);
		linePropertyVector.setElementAt(getLineProperty(line), row);
	}

	void insertLine(char[] line, int row) {
		super.insertLine(line, row);
		linePropertyVector.insertElementAt(getLineProperty(line), row);
	}

	void removeLine(int row) {
		super.removeLine(row);
		linePropertyVector.removeElementAt(row);
	}

	/* ---- Methods to locate block comments ------------------------------- */

	private int[] findCommentBound(int row, int col, boolean isBegin) {
		for (int r = row; r < linePropertyVector.size(); r += 1) {
			int beginCol = (r == row) ? col : 0;
			LineProperty lp = (LineProperty) linePropertyVector.elementAt(r);
			IntVector v = isBegin
					? lp.commentBeginVector : lp.commentEndVector;
			if (v == null) {
				continue;
			}

			for (int i = 0; i < v.size(); i += 1) {
				int comCol = v.elementAt(i);
				if (comCol >= beginCol) {
					return new int[] { r, comCol };
				}
			}
		}
		return null;
	}

	private int[] findCommentBegin(int row, int col) {
		return findCommentBound(row, col, true);
	}

	private int[] findCommentEnd(int row, int col) {
		return findCommentBound(row, col, false);
	}

	private void computeCommentRanges() {
		commentRangeVector.setSize(0);
		int[] pos = new int[] { 0, 0 };

		while (true) {
			pos = findCommentBegin(pos[0], pos[1]);
			if (pos == null) {
				break;
			} else {
				Range range = new Range();
				range.setBegin(pos[0], pos[1]);
				pos = findCommentEnd(pos[0], pos[1] + 3);
				if (pos == null) {
					break;
				} else {
					range.setEnd(pos[0], pos[1]);
					commentRangeVector.addElement(range);
					pos[1] += 1;
				}
			}
		}
	}

	/* ---- Methods to match brackets -------------------------------------- */

	private Range getBracketedRange(int row, int col, char c1, char c2) {
		if ((getLine(row)[col] == c1) && isValidBracket(row, col)) {
			int[] pos = null;
			if (c2 > c1) {
				pos = findBracketForward(c1, c2, row, col);
			} else {
				pos = findBracketBackward(c1, c2, row, col);
			}
			if (pos != null) {
				Range range = new Range();
				range.setBegin(row, col);
				range.setEnd(pos[0], pos[1]);
				return range;
			}
		}
		return null;
	}

	private int[] findBracketForward(char c1, char c2, int row, int col) {
		int level = 1;
		for (int i = row; i < getLineCount(); i += 1) {
			char[] line = getLine(i);
			int beginCol = (i == row) ? col + 1 : 0;
			for (int j = beginCol; j < line.length; j += 1) {
				if ((level = updateLevel(level, line, c1, c2, i, j)) == 0) {
					return new int[] { i, j };
				}
			}
		}
		return null;
	}

	private int[] findBracketBackward(char c1, char c2, int row, int col) {
		int level = 1;
		for (int i = row; i >= 0; i -= 1) {
			char[] line = getLine(i);
			int beginCol = (i == row) ? col - 1 : line.length - 1;
			for (int j = beginCol; j >= 0; j -= 1) {
				if ((level = updateLevel(level, line, c1, c2, i, j)) == 0) {
					return new int[] { i, j };
				}
			}
		}
		return null;
	}

	private int updateLevel(
			int level, char[] line, char c1, char c2, int row, int col) {
		if ((line[col] == c1) && isValidBracket(row, col)) {
			level += 1;
		} else if ((line[col] == c2) && isValidBracket(row, col)) {
			level -= 1;
		}
		return level;
	}

	/* ---- Methods to determine the color codes --------------------------- */

	void modified() {
		computeCommentRanges();
	}

	private boolean isRangeContains(Range range, int row, int col) {
		range = range.getForwardRange();
		if (range.beginRow != range.endRow) {
			if (((row == range.beginRow) && (range.beginColumn <= col))
					|| ((row == range.endRow) && (col <= range.endColumn))
					|| ((range.beginRow < row) && (row < range.endRow))) {
				return true;
			}
		} else if ((row == range.beginRow) // range.beginRow == range.endRow
				&& (range.beginColumn <= col) && (col <= range.endColumn)) {
			return true;
		}
		return false;
	}

	private boolean isInsideCommentRanges(int row, int col) {
		for (int i = 0; i < commentRangeVector.size(); i += 1) {
			Range range = (Range) commentRangeVector.elementAt(i);
			if (isRangeContains(range, row, col)) {
				return true;
			}
		}
		return false;
	}

	private boolean isValidBracket(int row, int col) {
		if (isInsideCommentRanges(row, col)) {
			return false;
		}

		char[] line = getLine(row);
		boolean isQuoted = false;
		for (int i = 0; i < col; i += 1) {
			char c = line[i];
			if ((c == '/') && ((i + 1) < line.length)) {
				if (line[i + 1] == '/') {
					return false;
				}
			}
			if ((c == '"') || (c == '\'')) {
				char quote = c;
				isQuoted = true;
				i += 1;
				while (i < col) {
					c = line[i];
					if (c == quote) {
						isQuoted = false;
						break;
					}
					if (c == '\\') {
						i += 1;
					}
					i += 1;
				}
			}
		}
		return (! isQuoted);
	}

	private byte[] highlightBracketedRange(int row, byte[] colorCodes) {
		char[] line = getLine(row);
		if (bracketedRange != null) {
			for (int col = 0; col < line.length; col += 1) {
				if ((bracketedRange.beginRow == row)
						&& (bracketedRange.beginColumn == col)) {
					colorCodes[col] = COLOR_BRACKET;
				} else if ((bracketedRange.endRow == row)
						&& (bracketedRange.endColumn == col)) {
					colorCodes[col] = COLOR_BRACKET;
				}
			}
		}
		return colorCodes;
	}

	private static void setColorCodes(
			byte[] colorCodes, int begin, int end, byte colorCode) {
		for (int col = begin; col < end; col += 1) {
			colorCodes[col] = colorCode;
		}
	}

	byte[] computeColorCodes(byte[] colorCodes, int row) {
		char[] line = getLine(row);
		colorCodes = highlightBracketedRange(row, colorCodes);

		for (int col = 0; col < line.length; col += 1) {
			char c = line[col];
			if (isWhitespace(c)) {
				continue;
			}

			if (isInsideCommentRanges(row, col)) {
				colorCodes[col] = COLOR_COMMENT;
				continue;
			}

			if ((c == '/') && ((col + 1) < line.length)
					&& (line[col + 1] == '/')) {
				setColorCodes(colorCodes, col, line.length, COLOR_COMMENT);
				break;
			} else if (Character.isJavaIdentifierStart(c)) {
				int end = parseIdentifier(line, col);
				String s = String.valueOf(line).substring(col, end);
				if (isKeyword(s)) {
					setColorCodes(colorCodes, col, end, COLOR_KEYWORD);
				} else if (s.equals("true") || s.equals("false")
						|| s.equals("null")) {
					setColorCodes(colorCodes, col, end, COLOR_LITERAL);
				}
				col = end - 1;
			} else if (isDigit(c) || ((c == '.')
					&& ((col + 1) < line.length)
					&& isDigit(line[col + 1]))) {
				int end = parseNumber(line, col);
				if (isNumeric(String.valueOf(line).substring(col, end))) {
					setColorCodes(colorCodes, col, end, COLOR_LITERAL);
				}
				col = end - 1;
			} else if ((c == '"') || (c == '\'')) {
				int end = parseQuoted(line, col, c);
				setColorCodes(colorCodes, col, end, COLOR_LITERAL);
				col = end - 1;
			} else if (OPERATORS.indexOf(c) >= 0) {
				colorCodes[col] = COLOR_OPERATOR;
			}
		}

		return colorCodes;
	}

	private int parseIdentifier(char[] chars, int pos) {
		int i = pos + 1;
		while (i < chars.length) {
			char c = chars[i];
			if (! Character.isJavaIdentifierPart(c)) {
				break;
			} else {
				i += 1;
			}
		}
		return i;
	}

	private int parseQuoted(char[] chars, int pos, char terminalChar) {
		int i = pos + 1;
		while (i < chars.length) {
			char c = chars[i];
			if (c == terminalChar) {
				i += 1;
				break;
			} else if (((i + 1) < chars.length) && (c == '\\')) {
				i += 1;
			}
			i += 1;
		}
		return i;
	}

	private int parseNumber(char[] chars, int pos) {
		int i = pos + 1;
		while (i < chars.length) {
			char c = Character.toLowerCase(chars[i]);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z')
					|| c == '.' || c == '+' || c == '-') {
				i += 1;
				continue;
			} else {
				break;
			}
		}
		return i;
	}

	private boolean isKeyword(String s) {
		int len = s.length();
		if ((len < 2) || (len == 11) || (len > 12)) {
			return false;
		}
		for (int i = 0; i < keywords[len].length; i += 1) {
			if (s.equals(keywords[len][i])) {
				return true;
			}
		}
		return false;
	}

	private boolean isNumeric(String s) {
		if (s == null || (s.length() == 0)) {
			return false;
		}

		for (int i = 0; i < numberRuleVector.size(); i += 1) {
			Rule rule = (Rule) numberRuleVector.elementAt(i);
			if (rule.match(s, 0) == s.length()) {
				return true;
			}
		}
		return false;
	}

	/* ---- Classes similar to RegExp to check the number syntax ----------- */

	private static class Rule {

		private int min = 1;
		private int max = 1;
		private String target;
		private Vector childRules; // of Rule

		Rule(String s) {
			this.target = s;
		}

		Rule(Vector rules) {
			this.childRules = rules;
		}

		void setRepetition(int min, int max) {
			this.min = min;
			this.max = max;
		}

		int match(String s, int pos) {
			if (pos < 0) {
				return -1;
			}

			int count = 0;
			if (childRules != null) {
				loop: while (count < max) {
					int p = pos;
					for (int i = 0; i < childRules.size(); i += 1) {
						p = ((Rule) childRules.elementAt(i)).match(s, p);
						if (p < 0) {
							break loop;
						}
					}
					count += 1;
					pos = p;
				}
			} else {
				while ((pos < s.length()) && (count < max)) {
					if (target.indexOf(s.charAt(pos)) == -1) {
						break;
					}
					count += 1;
					pos += 1;
				}
			}

			if (count < min) {
				return -1;
			}
			return pos;
		}

	}

	private Vector parseRule(String s) {
		Vector rules = new Vector(); // of Rule

		for (int i = 0; i < s.length(); i += 1) {
			Rule rule;
			char c = s.charAt(i);

			if (c == '(') {
				int p = i + 1;
				while (s.charAt(p) != ')') {
					p += 1;
				}
				rule = new Rule(parseRule(s.substring(i + 1, p)));
				i = p;
			} else if (c == '[') {
				int p = i + 1;
				StringBuffer sb = new StringBuffer();

				while (true) {
					if (s.charAt(p) == ']') {
						break;
					} else if (s.charAt(p) == '-') {
						int a = s.charAt(p - 1);
						int b = s.charAt(p + 1);
						if ((a != '[') && (b != ']')) {
							for (int n = a + 1; n < b; n += 1) {
								sb.append((char) n);
							}
							p += 1; // where s.charAt(p) == (char) b
						}
					}
					sb.append(s.charAt(p));
					p += 1;
				}

				rule = new Rule(sb.toString());
				i = p;
			} else {
				rule = new Rule(String.valueOf(c));
			}

			if ((i + 1) < s.length()) {
				c = s.charAt(i + 1);
				if (c == '+') {
					rule.setRepetition(1, Integer.MAX_VALUE);
					i += 1;
				} else if (c == '?') {
					rule.setRepetition(0, 1);
					i += 1;
				}
			}
			rules.addElement(rule);
		}

		return rules;
	}

}
