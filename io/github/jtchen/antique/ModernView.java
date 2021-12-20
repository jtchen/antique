/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.im.InputMethodRequests;
import java.text.*;
import java.text.AttributedCharacterIterator.Attribute;
import java.io.File;
import java.net.URI;
import java.util.StringTokenizer;

/*
 *	This is an "modern" version of the view outside the scope of Java 1.1. When
 *	supported by the runtime, it will be instantiated through reflection.
 */
class ModernView extends View {

	private AbstractEditor editor;
	private Font mainFont;
	private int ascent;
	private int lineHeight;
	private InputMethodWindow window;

	ModernView(AbstractEditor editor, AbstractModel model) {
		super(editor, model);
		this.editor = editor;
		this.mainFont = editor.getMainFont();
		FontMetrics fm = getFontMetrics(mainFont);
		ascent = fm.getAscent();
		lineHeight = fm.getHeight();

		// avoid flickering when resizing
		Toolkit.getDefaultToolkit().setDynamicLayout(false);

		vScrollbar.setFocusable(false);
		hScrollbar.setFocusable(false);
		window = new InputMethodWindow();
		addInputMethodListener(window);

		addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (window.getSize().width == 0) {
					vScrollbar.setValue(vScrollbar.getValue()
							+ (e.getUnitsToScroll() * lineHeight));
					adjustmentValueChanged(vScrollbar, vScrollbar.getValue());
					repaint();
				}
			}
		});

		setDropTarget(new FileListDropTarget());
	}

	private class FileListDropTarget extends DropTarget {

		/*
		 * Please refer to JDK-4899516.
		 */
		public void drop(DropTargetDropEvent dtde) {
			try {
				dtde.acceptDrop(DnDConstants.ACTION_COPY);
				Transferable tr = dtde.getTransferable();
				java.util.List files = null;
				if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					files = (java.util.List)
							tr.getTransferData(DataFlavor.javaFileListFlavor);
				} else {
					DataFlavor uriListFlavor = new DataFlavor(
							"text/uri-list;class=java.lang.String");
					if (tr.isDataFlavorSupported(uriListFlavor)) {
						String s = (String) tr.getTransferData(uriListFlavor);
						files = textURIListToFileList(s);
					}
				}
				if (files != null) {
					for (int i = 0; i < files.size(); i += 1) {
						editor.openFile((File) files.get(i));
					}
				}
			} catch (Exception e) {}
		}

		private java.util.List textURIListToFileList(String data) {
			java.util.List list = new java.util.ArrayList();
			StringTokenizer st = new StringTokenizer(data, "\r\n");
			while (st.hasMoreTokens()) {
				String s = st.nextToken();
				if (s.startsWith("#")) { // this line is a comment
					continue;
				}
				try {
					list.add(new File(new URI(s)));
				} catch (Exception e) {}
			}
			return list;
		}

	}

	boolean canDisplay(char c) {
		return mainFont.canDisplay(c);
	}

	/*
	 *	This method overrides Component's getInputMethodRequests().
	 */
	public InputMethodRequests getInputMethodRequests() {
		return window;
	}

	void disableFocusTraversalKeys() {
		setFocusTraversalKeysEnabled(false);
	}

	private class InputMethodWindow extends Window
			implements InputMethodRequests, InputMethodListener {

		private FontMetrics fontMetrics;

		private StringBuffer composedStringBuffer = new StringBuffer();
		private String composedString;
		private int composeCaretIndex = 0;

		private Image bufferImage;

		InputMethodWindow() {
			super(editor);
			Font fallbackFont = editor.getFallbackFont();
			setFont(fallbackFont);
			fontMetrics = getFontMetrics(fallbackFont);
			setSize(0, 0);
			setFocusableWindowState(false);
			setVisible(true);
		}

		private int computeStringWidth(String s) {
			int width = 0;
			char[] line = s.toCharArray();
			int padding = (2 * numberWidth) - getHanziWidth();
			for (int col = 0; col < line.length; col += 1) {
				char c = line[col];
				if (isMonospacedFont && AbstractEditor.isHanzi(c)) {
					width += padding;
				}
				width += fontMetrics.charWidth(c);
			}
			return width;
		}

		private Rectangle getComposedCaret() {
			String s = composedString.substring(0, composeCaretIndex);
			return new Rectangle(computeStringWidth(s), 0, 0, lineHeight);
		}

		public void update(Graphics g) {
			paint(g);
		}

		public void paint(Graphics g) {
			Dimension size = getSize();
			if (size.width == 0) {
				return;
			}

			if ((bufferImage == null)
					|| (bufferImage.getWidth(this) != size.width)) {
				bufferImage = createImage(size.width, lineHeight);
			}
			Graphics bg = bufferImage.getGraphics();
			enableRenderingHint(bg); // a method of AdaptiveCanvas

			bg.setColor(Theme.CONTROL_BACKGROUND_COLOR);
			bg.fillRect(0, 0, size.width, size.height);

			bg.setColor(Theme.CONTROL_FOREGROUND_COLOR);
			int x = 0;
			char[] line = composedString.toCharArray();
			int padding = (2 * numberWidth) - getHanziWidth();
			for (int col = 0; col < line.length; col += 1) {
				char c = line[col];
				if (isMonospacedFont && AbstractEditor.isHanzi(c)) {
					x += (padding / 2);
					bg.drawChars(line, col, 1, x, ascent);
					x += (padding / 2);
				} else {
					bg.drawChars(line, col, 1, x, ascent);
				}
				x += fontMetrics.charWidth(c);
			}

			Rectangle rect = getComposedCaret();
			if (rect != null) {
				bg.setColor(Theme.CURSOR_COLOR);
				bg.fillRect(rect.x, rect.y, 1, lineHeight);
			}

			g.drawImage(bufferImage, 0, 0, this);
		}

		/*
		 *	This method implements the InputMethodRequests interface.
		 */
		public Rectangle getTextLocation(TextHitInfo offset) {
			// the parameter 'offset' was ignored in this implementation
			Rectangle rect = getComposedCaret();
			Point p = getLocationOnScreen();
			rect.translate(p.x, p.y);
			return rect;
		}

		/*
		 *	This method implements the InputMethodListener interface.
		 */
		public void inputMethodTextChanged(InputMethodEvent event) {
			AttributedCharacterIterator it = event.getText();
			if (event.getCaret() != null) {
				composeCaretIndex = event.getCaret().getCharIndex();
			} else {
				composeCaretIndex = 0;
			}
			composedStringBuffer.setLength(0);

			if (it != null) {
				int committedCharCount = event.getCommittedCharacterCount();
				char c = it.first();
				for (int i = 0; i < committedCharCount; i += 1) {
					editor.keyType(c);
					c = it.next();
				}

				int i = it.getBeginIndex() + committedCharCount;
				if ((it.getEndIndex() - i) > 0) {
					it.setIndex(i);
					c = it.current();
					while (c != CharacterIterator.DONE) {
						composedStringBuffer.append(c);
						c = it.next();
					}
				}
			}

			composedString = composedStringBuffer.toString();
			if (composedString.length() > 0) {
				int width = computeStringWidth(composedString) + 1;
				setSize(width, lineHeight);
				Point o = ModernView.this.getLocationOnScreen();
				Point p = ModernView.this.getCursorLocation();
				setLocation(o.x + p.x, o.y + p.y);
				toFront();
			} else {
				setSize(0, 0);
			}
			repaint();
		}

		/* ---- An empty method for InputMethodListener interface ---------- */

		public void caretPositionChanged(InputMethodEvent event) {}

		/* ---- Empty methods for InputMethodRequests interface ------------ */

		public int getCommittedTextLength() {
			return 0;
		}

		public AttributedCharacterIterator cancelLatestCommittedText(
				Attribute[] attributes) {
			return null;
		}

		public AttributedCharacterIterator getCommittedText(
				int beginIndex, int endIndex, Attribute[] attributes) {
			return null;
		}

		public int getInsertPositionOffset() {
			return 0;
		}

		public TextHitInfo getLocationOffset(int x, int y) {
			return null;
		}

		public AttributedCharacterIterator getSelectedText(
				Attribute[] attributes) {
			return null;
		}

	}

}
