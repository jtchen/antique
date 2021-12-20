/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

/*
 *	This is a view implemented in a Java 1.1 compatible way.
 */
class View extends AbstractView {

	private static final int VERTICAL = 0;
	private static final int HORIZONTAL = 1;

	private AbstractModel model;

	private FontMetrics fontMetrics;
	private Font mainFont;
	private Font fallbackFont;
	private int tabSize;

	private PageMetrics pageMetrics;

	private int gutterWidth = 0;
	private int lineHeight;
	private int ascent;
	private int descent;
	private boolean isLineWrap = false;
	private int pageWidth;
	private int pageHeight;

	private int scrollbarThickness;

	int numberWidth;
	int hanziWidth = -1; // an impossible value as a flag
	boolean isMonospacedFont;

	private Rectangle cursor;
	private Rectangle viewport;

	private Container panel = new Panel();

	PlainScrollbar vScrollbar = new PlainScrollbar(VERTICAL);
	PlainScrollbar hScrollbar = new PlainScrollbar(HORIZONTAL);

	View(AbstractEditor editor, AbstractModel model) {
		this.model = model;
		setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

		mainFont = editor.getMainFont();
		fallbackFont = editor.getFallbackFont();
		fontMetrics = getFontMetrics(mainFont);
		tabSize = editor.getTabSize();
		pageMetrics = new PageMetrics();

		lineHeight = fontMetrics.getHeight();
		ascent = fontMetrics.getAscent();
		descent = fontMetrics.getDescent();
		numberWidth = fontMetrics.charWidth('0');
		if (fontMetrics.charWidth('i') == fontMetrics.charWidth('W')) {
			isMonospacedFont = true;
		} else {
			isMonospacedFont = false;
		}
		cursor = new Rectangle(1, lineHeight);
		viewport = new Rectangle();

		scrollbarThickness = (int) (1.5 * numberWidth);

		panel.setLayout(null);
		panel.add(vScrollbar);
		panel.add(hScrollbar);
		panel.add(this);
		panel.addComponentListener(this);

		disableFocusTraversalKeys(); // in order to use the tab key
		setSize(1, lineHeight); // the minimum size is the cursor size
	}

	/*
	 *	This method is intended to be overridden.
	 */
	void disableFocusTraversalKeys() {}

	/*
	 *	This method is intended to be overridden.
	 */
	boolean canDisplay(char c) {
		return true;
	}

	synchronized void setModel(AbstractModel model) {
		this.model = model;
		pageMetrics = new PageMetrics();
	}

	Rectangle getViewport() {
		return viewport;
	}

	boolean isLineWrap() {
		return isLineWrap;
	}

	void setLineWrap(boolean isLineWrap) {
		this.isLineWrap = isLineWrap;
		if (isLineWrap) {
			viewport.x = 0;
		}
		updateTextByModel();
	}

	/* ---- Methods used in doKeyNavigation() of the Controller ------------ */

	int getLineHeight() {
		return lineHeight;
	}

	int getCursorX() { // relative to the viewport
		return cursor.x + gutterWidth - viewport.x;
	}

	int getCursorY() { // relative to the viewport
		return cursor.y - viewport.y;
	}

	/* ---- Event listeners ------------------------------------------------ */

	/*
	 *	This is a simplified version of the adjustmentValueChanged() method in
	 *	the AdjustmentListener interface. There is no need to create
	 *	AdjustmentEvent objects in this program.
	 */
	void adjustmentValueChanged(PlainScrollbar ps, int value) {
		if (ps.getOrientation() == HORIZONTAL) {
			int limit = pageMetrics.getWidth() + gutterWidth - viewport.width;
			limit = Math.max(limit, 0);
			viewport.x = Math.min(value, limit);
		} else {
			int limit = pageMetrics.getHeight() - viewport.height;
			limit = Math.max(limit, 0);
			viewport.y = Math.min(value, limit);
		}
		repaint();
	}

	public synchronized void componentResized(ComponentEvent e) {
		int vw = panel.getSize().width - scrollbarThickness;
		int vh = panel.getSize().height - scrollbarThickness;
		int dw = vw - viewport.width;
		int dh = vh - viewport.height;
		pageWidth = pageMetrics.getWidth();
		pageHeight = pageMetrics.getHeight();

		if (dw > 0) {
			if ((! isLineWrap) && ((pageWidth + gutterWidth) > vw)) {
				viewport.x = Math.max(0, viewport.x - dw);
			} else {
				viewport.x = 0;
			}
		}
		if (dh > 0) {
			if (pageHeight > vh) {
				viewport.y = Math.max(0, viewport.y - dh);
			} else {
				viewport.y = 0;
			}
		}
		setBounds(0, 0, vw, vh);
		vScrollbar.setBounds(vw, 0, scrollbarThickness, vh);
		hScrollbar.setBounds(0, vh, vw, scrollbarThickness);
		viewport.setSize(vw, vh);
		updateScrollbarValues();

		if (isLineWrap) {
			updateTextByModel();
		} else {
			repaint();
		}
	}

	public void componentMoved(ComponentEvent e) {}
	public void componentShown(ComponentEvent e) {}
	public void componentHidden(ComponentEvent e) {}

	/* ---- Update methods ------------------------------------------------- */

	private int canvasYToModelRow(int y) {
		int lc = model.getLineCount();
		int mRow = lc - 1;
		for (int row = 1; row < lc; row += 1) {
			if (pageMetrics.getY(row, 0) > y) {
				mRow = row - 1;
				break;
			}
		}
		return mRow;
	}

	int[] moveCursorByPoint(int x, int y) {
		x += viewport.x;
		y += viewport.y;
		x = Math.max(x, gutterWidth);
		y = Math.max(y, 0);
		y = Math.min(y, pageHeight - lineHeight);

		int mRow = canvasYToModelRow(y);
		char[] line = model.getLine(mRow);
		boolean isCursorAtWrap = false;

		int mCol = line.length;
		for (int col = 1; col < (line.length + 1); col += 1) {
			int curY = pageMetrics.getY(mRow, col);
			int curX = pageMetrics.getX(mRow, col);
			int pageX = x - gutterWidth;

			if ((curY > y) // because of wrapping
					|| (((curY + lineHeight) > y) && (curX > pageX))) {
				int colWidth = pageMetrics.columnWidth(mRow, col - 1);
				int prevCurX = pageMetrics.getX(mRow, col - 1);
				if (pageX > (prevCurX + (colWidth / 2))) {
					mCol = col;
					if (curY > y) { // because of wrapping
						isCursorAtWrap = true;
					}
				} else {
					/*
					 *	Without this check, if a user drags the mouse cursor
					 *	into the gutter and if the line is wrapped, the caret
					 *	will go to the "previous line" (in fact the same line).
					 */
					if (y > (pageMetrics.getY(mRow, col - 1) + lineHeight)) {
						mCol = col;
					} else {
						mCol = col - 1;
					}
				}
				break;
			}
		}

		cursor.x = pageMetrics.getX(mRow, mCol, isCursorAtWrap);
		cursor.y = pageMetrics.getY(mRow, mCol, isCursorAtWrap);
		moveViewportToContainCursor();
		repaint();
		return new int[] { mRow, mCol };
	}

	private synchronized void moveViewportToContainCursor() {
		Point o = viewport.getLocation();
		Dimension size = viewport.getSize();
		int curRight = cursor.x + cursor.width + gutterWidth;
		int curBottom = cursor.y + cursor.height;
		int vpRight = o.x + size.width;
		int vpBottom = o.y + size.height;

		if (! isLineWrap) {
			if (curRight > vpRight) {
				o.x += curRight - vpRight;
			} else if (cursor.x < o.x) {
				o.x += cursor.x - o.x;
			}
		} else {
			o.x = 0;
		}
		if (curBottom > vpBottom) {
			o.y += curBottom - vpBottom;
		} else if (cursor.y < o.y) {
			o.y += cursor.y - o.y;
		}

		viewport.setLocation(o.x, o.y);
		updateScrollbarValues();
	}

	private void moveCursorByModel() {
		int curRow = model.getCaretRow();
		int curCol = model.getCaretColumn();
		cursor.x = pageMetrics.getX(curRow, curCol);
		cursor.y = pageMetrics.getY(curRow, curCol);
	}

	void updateCursorByModel() {
		moveCursorByModel();
		moveViewportToContainCursor();
		repaint();
	}

	private void updateScrollbarValues() {
		int width = pageWidth + gutterWidth;
		int height = pageHeight;
		if (width > viewport.width) {
			hScrollbar.setValues(viewport.x, viewport.width, 0, width);
			hScrollbar.setEnabled(true);
		} else {
			hScrollbar.setValues(0, 0, 0, 0);
			hScrollbar.setEnabled(false);
		}
		if (height > viewport.height) {
			vScrollbar.setValues(viewport.y, viewport.height, 0, height);
			vScrollbar.setEnabled(true);
		} else {
			vScrollbar.setValues(0, 0, 0, 0);
			vScrollbar.setEnabled(false);
		}
	}

	private void updateLines(int beginRow, int endRow, Point viewportOrigin) {
		pageMetrics.resetLines(beginRow, endRow);

		String s = Integer.toString(model.getLineCount());
		gutterWidth = (s.length() + 1) * numberWidth;

		if ((pageWidth != pageMetrics.getWidth())
				|| (pageHeight != pageMetrics.getHeight())) {
			int dw = pageMetrics.getWidth() - pageWidth;
			int dh = pageMetrics.getHeight() - pageHeight;
			pageWidth += dw;
			pageHeight += dh;

			if (viewportOrigin != null) {
				viewport.x = viewportOrigin.x;
				viewport.y = viewportOrigin.y;
			} else {
				if ((! isLineWrap) && (dw < 0)) { // pageWidth decreases
					if ((viewport.x + viewport.width) > pageWidth) {
						viewport.x = Math.max(0, viewport.x + dw);
					}
				}
				if (dh < 0) { // pageHeight decreases
					if ((viewport.y + viewport.height) > pageHeight) {
						viewport.y = Math.max(0, viewport.y + dh);
					}
				}
			}
		}
	}

	void updateTextByModel() {
		updateTextByModel(0, model.getLineCount() - 1);
	}

	void updateTextByModel(int beginRow, int endRow) {
		updateLines(beginRow, endRow, null);
		moveCursorByModel();
		moveViewportToContainCursor();
		repaint();
	}

	/*
	 *	This method is only called by the select() method of the TabCollection
	 *	class. It assumes that a new PageMetrics object has just been created.
	 */
	void updateTextByModel(Point viewportOrigin) {
		viewport.setLocation(viewportOrigin);
		updateLines(0, model.getLineCount() - 1, viewportOrigin);
		moveCursorByModel();
		updateScrollbarValues();
		repaint();
	}

	/* ---- Routine methods for painting ----------------------------------- */

	synchronized void paint(Graphics bg, int width, int height) {
		bg.setColor(Theme.BACKGROUND_COLOR);
		bg.fillRect(0, 0, width, height);

		Point o = viewport.getLocation();
		int beginRow = canvasYToModelRow(o.y);
		int endRow = canvasYToModelRow(o.y + height - 1);

		paintSelection(bg, beginRow, endRow);
		paintCurrentLineIndicator(bg);
		paintRows(bg, beginRow, endRow);
		paintGutter(bg, beginRow, endRow);
		if (isMonospacedFont) {
			paintEdge(bg);
		}
		paintCursor(bg);
	}

	private void paintSelection(Graphics g, int beginRow, int endRow) {
		Range r = model.getSelection();
		if (r == null) {
			return;
		}

		beginRow = Math.max(r.beginRow, beginRow);
		endRow = Math.min(r.endRow, endRow);
		Point o = viewport.getLocation();
		for (int row = beginRow; row <= endRow; row += 1) {
			int beginCol = (row == r.beginRow) ? r.beginColumn : 0;
			int endCol = (row == r.endRow)
					? r.endColumn : model.getLine(row).length;
			for (int col = beginCol; col < endCol; col += 1) {
				int x = pageMetrics.getX(row, col);
				int y = pageMetrics.getY(row, col);
				Point p = new Point(x, y);
				p.translate(gutterWidth - o.x, - o.y);
				int w = pageMetrics.columnWidth(row, col);
				g.setColor(Theme.CONTROL_BACKGROUND_COLOR);
				g.fillRect(p.x, p.y, w, lineHeight);
			}
		}
	}

	private void paintCurrentLineIndicator(Graphics g) {
		g.setColor(Theme.CONTROL_BACKGROUND_COLOR);
		int y = getCursorLocation().y + lineHeight - 1;
		g.drawLine(gutterWidth, y, viewport.getSize().width - 1, y);
	}

	int getHanziWidth() {
		if (hanziWidth == -1) {
			if (canDisplay('\u4e00')) {
				hanziWidth = fontMetrics.charWidth('\u4e00');
			} else {
				hanziWidth = getFontMetrics(fallbackFont).charWidth('\u4e00');
			}
		}
		return hanziWidth;
	}

	private void paintRows(Graphics g, int beginRow, int endRow) {
		Point o = viewport.getLocation();
		for (int row = beginRow; row <= endRow; row += 1) {
			char[] line = model.getLine(row);
			byte[] colorCodes = model.getColorCodes(row);
			for (int col = 0; col < line.length; col += 1) {
				char c = line[col];

				int x = pageMetrics.getX(row, col);
				int y = pageMetrics.getY(row, col);
				Point p = new Point(x, y);
				p.translate(gutterWidth - o.x, - o.y);

				byte colorCode = colorCodes[col];
				if ((colorCode & Theme.MATCH_MASK) == Theme.MATCH_MASK) {
					g.setColor(Theme.HARD_HIGHLIGHT_COLOR);
					int w = pageMetrics.columnWidth(row, col);
					int h = 4;
					g.fillRect(p.x, p.y + lineHeight - h, w, h);
				}
				if ((c == ' ') || (c == '\t')) {
					if ((colorCode & Theme.MESSY_WHITESPACE_MASK)
							!= Theme.MESSY_WHITESPACE_MASK) {
						continue;
					}
					g.setColor(Theme.SOFT_HIGHLIGHT_COLOR);
					int w = pageMetrics.columnWidth(row, col);
					g.drawRect(p.x, p.y, w, lineHeight - 1);
				}

				if (canDisplay(c)) {
					g.setFont(mainFont);
				} else {
					g.setFont(fallbackFont);
				}
				if (isMonospacedFont && AbstractEditor.isHanzi(c)) {
					p.x += (2 * numberWidth - getHanziWidth()) / 2;
				}
				g.setColor(Theme.getColor(colorCode));
				g.drawChars(line, col, 1, p.x, p.y + ascent);
			}
		}
	}

	Point getCursorLocation() {
		Point o = viewport.getLocation();
		Point p = cursor.getLocation();
		p.translate(gutterWidth - o.x, - o.y);
		return p;
	}

	private void paintCursor(Graphics g) {
		g.setColor(Theme.CURSOR_COLOR);
		Point p = getCursorLocation();
		g.fillRect(p.x, p.y, 1, lineHeight);
	}

	private void paintGutter(Graphics g, int beginRow, int endRow) {
		g.setColor(Theme.CONTROL_BACKGROUND_COLOR);
		g.fillRect(0, 0, gutterWidth, viewport.getSize().height);
		g.setFont(mainFont);
		Point o = viewport.getLocation();
		for (int row = beginRow; row <= endRow; row += 1) {
			String s = Integer.toString(row + 1);
			char[] line = model.getLine(row);
			int top = pageMetrics.getY(row, 0) - o.y;
			int bottom = pageMetrics.getY(row, (line.length - 1)) - o.y;
			int x = gutterWidth - (int) ((s.length() + 0.5) * numberWidth);
			if (top != bottom) {
				int x1 = x + (int) ((s.length() - 0.5) * numberWidth);
				int x2 = x1 + (numberWidth / 2);
				int y1 = top + (lineHeight / 2);
				int y2 = bottom + (lineHeight / 2);
				g.setColor(Theme.CONTROL_FOREGROUND_COLOR);
				g.drawLine(x1, y1, x1, y2);
				g.drawLine(x1, y2, x2, y2);
				g.setColor(Theme.CONTROL_BACKGROUND_COLOR);
				g.fillRect(0, top, gutterWidth, lineHeight);
			}
			g.setColor(Theme.CONTROL_FOREGROUND_COLOR);
			g.drawString(s, x, top + ascent);
		}
	}

	private void paintEdge(Graphics g) {
		g.setColor(Theme.CONTROL_BACKGROUND_COLOR);
		int x = (80 * numberWidth) + gutterWidth - viewport.getLocation().x;
		g.drawLine(x, 0, x, viewport.getSize().height);
	}

	/*
	 *	This class is used to calculate the character position.
	 */
	private class PageMetrics {

		private int tabWidth;

		private Vector lineMetricsVector = new Vector(); // of LineMetrics
		private AbstractModel.IntVector wrapPointsVector
				= new AbstractModel.IntVector();

		private int pageWidthCache = -1; // an impossible value as a flag

		private class LineMetrics {
			int segmentCount; // parts of a line by wrapping
			int previousSegmentCount = -1; // an impossible value as a flag
			int[] xLocations;
			int[] wrapPoints;
		}

		PageMetrics() {
			tabWidth = tabSize * fontMetrics.charWidth(' ');
		}

		synchronized void resetLines(int beginRow, int endRow) {
			endRow = Math.min(endRow, lineMetricsVector.size() - 1);
			for (int i = beginRow; i <= endRow; i += 1) {
				lineMetricsVector.setElementAt(null, i);
			}
			pageWidthCache = -1;
		}

		private int getCharWidth(int pageX, char c) {
			if (c == '\t') {
				return ((pageX + tabWidth) / tabWidth) * tabWidth - pageX;
			}
			if (isMonospacedFont && AbstractEditor.isHanzi(c)) {
				return 2 * numberWidth;
			}
			return fontMetrics.charWidth(c);
		}

		private synchronized LineMetrics getLineMetrics(int row) {
			if (row > (lineMetricsVector.size() - 1)) {
				lineMetricsVector.setSize(row + 1);
			}

			LineMetrics lm = (LineMetrics) lineMetricsVector.elementAt(row);
			if (lm == null) {
				lm = new LineMetrics();
				char[] line = model.getLine(row);
				int[] xLocs = new int[line.length + 1]; // xLocs[0] == 0
				int segCount = 1;

				Dimension size = viewport.getSize();
				int wpWidth = size.width - gutterWidth - cursor.width;
				wrapPointsVector.removeAllElements();

				for (int col = 1; col <= line.length; col += 1) {
					int colWidth = getCharWidth(xLocs[col - 1], line[col - 1]);
					xLocs[col] = xLocs[col - 1] + colWidth;

					if (isLineWrap && (xLocs[col] > wpWidth)) {
						int lastWrapablePos = 0;
						for (int i = (col - 1); i > 0; i -= 1) {
							if (xLocs[i] == 0) {
								break;
							}

							char c = line[i];
							if (AbstractEditor.isHanzi(c)) {
								lastWrapablePos = i;
								break;
							}
							if (i > 1) {
								c = line[i - 1];
								if (AbstractEditor.isHanzi(c)
										|| (c == ' ') || (c == '\t')) {
									lastWrapablePos = i;
									break;
								}
							}
						}

						if (lastWrapablePos > 0) { // wrapping succeeded
							col = lastWrapablePos + 1;

							colWidth = xLocs[col] - xLocs[col - 1];
							xLocs[col - 1] = 0;
							xLocs[col] = colWidth;
							segCount += 1; // the line was wrapped
							wrapPointsVector.addElement(col - 1);
						}
					}
				}

				int[] wrapPoints = new int[segCount];
				for (int i = 0; i < (segCount - 1); i += 1) {
					wrapPoints[i] = wrapPointsVector.elementAt(i);
				}
				wrapPoints[segCount - 1] = line.length + 1; // for getY()

				lm.xLocations = xLocs;
				lm.segmentCount = segCount;
				lm.wrapPoints = wrapPoints;
				lineMetricsVector.setElementAt(lm, row);
			}
			return lm;
		}

		/*
		 *	Since there are tabs, this method replaces fontMetrics.charWidth().
		 */
		int columnWidth(int row, int col) {
			int[] xLocs = getLineMetrics(row).xLocations;
			int w = xLocs[col + 1] - xLocs[col];

			if (w > 0) {
				return w;
			} else {
				char[] line = model.getLine(row);
				return getCharWidth(xLocs[col], line[col]);
			}
		}

		/*
		 *	There is a special case that the cursor is at the wrapping point of
		 *	a line. The rightmost cursor location in a "row" is actually the
		 *	leftmost cursor location in the "next row". If a user presses the
		 *	End key, the desired cursor location should be the rightmost one.
		 *	This method uses a boolean flag to distinguish the two conditions.
		 */
		int getX(int row, int col, boolean isCursorAtWrap) {
			LineMetrics lm = getLineMetrics(row);
			if (isCursorAtWrap && (col > 0)) {
				int[] xLocs = lm.xLocations;
				char[] line = model.getLine(row);
				int colWidth = getCharWidth(xLocs[col - 1], line[col - 1]);
				return xLocs[col - 1] + colWidth;
			}
			return lm.xLocations[col];
		}

		int getX(int row, int col) {
			return getX(row, col, false);
		}

		private int getPreviousSegmentCount(int row) {
			LineMetrics lm = getLineMetrics(row);
			if (lm.previousSegmentCount != -1) {
				return lm.previousSegmentCount;
			}

			int count = 0;
			for (int i = 0; i < row; i += 1) {
				lm = getLineMetrics(i);
				lm.previousSegmentCount = count;
				count += lm.segmentCount;
			}
			return count;
		}

		/*
		 *	The meaning of this method's boolean flag is the same as that used
		 *	in the getX() method.
		 */
		int getY(int row, int col, boolean isCursorAtWrap) {
			int[] wpPoints = getLineMetrics(row).wrapPoints;
			int wpRows = 0;

			for (int i = 0; i < wpPoints.length; i += 1) {
				if ((isCursorAtWrap && (wpPoints[i] == col))
						|| (wpPoints[i] > col)) {
					wpRows = i;
					break;
				}
			}
			return (getPreviousSegmentCount(row) + wpRows) * lineHeight;
		}

		int getY(int row, int col) {
			return getY(row, col, false);
		}

		int getWidth() {
			if (isLineWrap) {
				return viewport.getSize().width - gutterWidth;
			}

			if (pageWidthCache < 0) {
				int w = 0;
				int lc = model.getLineCount();
				for (int i = 0; i < lc; i += 1) {
					int[] xLocs = getLineMetrics(i).xLocations;
					int newWidth = xLocs[model.getLine(i).length];
					if (newWidth > w) {
						w = newWidth;
					}
				}
				pageWidthCache = w + cursor.width;
			}
			return pageWidthCache;
		}

		int getHeight() {
			int lc = model.getLineCount();
			LineMetrics lm = getLineMetrics(lc - 1);
			int count = getPreviousSegmentCount(lc - 1) + lm.segmentCount;
			return count * lineHeight;
		}

	}

	/*
	 *	This class is a simpler replacement for the AWT scrollbar. It is also
	 *	used as a solution to the buggy implementation of some early Java
	 *	virtual machines.
	 */
	class PlainScrollbar extends AbstractEditor.AdaptiveCanvas
			implements MouseListener, MouseMotionListener {

		private int orientation;
		private int value;
		private int visible;
		private int min;
		private int max;

		private Rectangle slider = new Rectangle();
		private boolean isDragging = false;
		private Point draggingOrigin = new Point();

		PlainScrollbar(int orientation) {
			this.orientation = orientation;
			this.value = 0;
			this.visible = 1;
			this.min = 0;
			this.max = 1;
			addMouseListener(this);
			addMouseMotionListener(this);
		}

		public int getOrientation() {
			return orientation;
		}

		public int getValue() {
			return value;
		}

		public void setValue(int v) {
			setValues(v, visible, min, max);
		}

		public void setValues(int value, int visible, int min, int max) {
			this.visible = visible;
			this.min = min;
			this.max = max;
			if ((value + visible) > max) {
				value = max - visible;
			} else if (value < min) {
				value = min;
			}
			this.value = value;
			repaint();
		}

		public void mousePressed(MouseEvent e) {
			int minimalSliderLength = lineHeight;
			Point p = e.getPoint();
			Rectangle rect = new Rectangle(slider);
			if (orientation == VERTICAL) {
				rect.grow(0, minimalSliderLength);
			} else {
				rect.grow(minimalSliderLength, 0);
			}
			if (isEnabled() && rect.contains(p)) {
				isDragging = true;
				draggingOrigin.setLocation(p.x - slider.x, p.y - slider.y);
			}
		}

		public void mouseDragged(MouseEvent e) {
			int minimalSliderLength = lineHeight;
			if (isDragging) {
				Point p = e.getPoint();
				int width = getSize().width - minimalSliderLength;
				int height = getSize().height - minimalSliderLength;
				if (orientation == VERTICAL) {
					int y = p.y - draggingOrigin.y;
					y = Math.max(0, y);
					y = Math.min(height - slider.height, y);
					value = y * (max - min) / height;
				} else {
					int x = p.x - draggingOrigin.x;
					x = Math.max(0, x);
					x = Math.min(width - slider.width, x);
					value = x * (max - min) / width;
				}
				repaint();

				adjustmentValueChanged(this, value);
			}
		}

		public void mouseReleased(MouseEvent e) {
			isDragging = false;
		}

		public void mouseMoved(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}

		void paint(Graphics bg, int width, int height) {
			int minimalSliderLength = lineHeight;
			bg.setColor(Theme.CONTROL_BACKGROUND_COLOR);
			bg.fillRect(0, 0, width, height);

			int range = max - min;
			if (isEnabled() && (range > 0)) {
				bg.setColor(Theme.CONTROL_FOREGROUND_COLOR);
				if (orientation == VERTICAL) {
					int y = (value * (height - minimalSliderLength)) / range;
					int h = (visible * (height - minimalSliderLength)) / range;
					slider.setBounds(0, y, width, h);
					int rh = h + minimalSliderLength;
					rh = Math.min(rh, (height - slider.y - 1));
					bg.drawRect(slider.x + 1, slider.y, width - 3, rh);
				} else {
					int x = (value * (width - minimalSliderLength)) / range;
					int w = (visible * (width - minimalSliderLength)) / range;
					slider.setBounds(x, 0, w, height);
					int rw = w + minimalSliderLength;
					rw = Math.min(rw, (width - slider.x - 1));
					bg.drawRect(slider.x, slider.y + 1, rw, height - 3);
				}
			}
		}

	}

}
