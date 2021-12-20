/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;

/*
 *	This class defines an editor and is used as an interface to other classes.
 *	In addition, it contains all the code related to reflection.
 */
abstract class AbstractEditor extends Frame
		implements ActionListener, ItemListener {

	static final int WIDTH = 800;
	static final int HEIGHT = 600;

	static final byte PLAIN_SYNTAX = 0;
	static final byte MARKDOWN_SYNTAX = 1;
	static final byte JAVA_SYNTAX = 2;

	static final long MAX_FILE_SIZE = 1024 * 1024;
	static final long MAX_PASTABLE_STRING_SIZE = 512 * 1024;

	/*
	 *	This class contains all the information used to find/replace.
	 */
	static class MatchConfig {
		String target = "";
		String replacement = null;
		boolean isForwardMatch = true;
		boolean isCaseSensitiveMatch = false;
		boolean isWholeWordMatch = false;
	}

	abstract MatchConfig getMatchConfig();

	abstract int getTabSize();
	abstract void setLineWrap(boolean isLineWrap);

	abstract Font getMainFont();
	abstract void setFallbackFont(Font font);
	abstract Font getFallbackFont();

	/*
	 *	This method generates a key typed event for the controller.
	 */
	abstract void keyType(char c);

	abstract void doNextTab();
	abstract void doPreviousTab();

	abstract void doTextChanged();
	abstract void doSelectionChanged();

	/*
	 *	This method returns the count of closed dialogs.
	 */
	abstract int closeDialogs();

	abstract void popupFindDialog(String target);
	abstract void popupReplaceDialog(String target);

	abstract void findNext();
	abstract void replace();
	abstract void replaceAll();

	/*
	 *	This method calls the controller's disableMatch(), which calls the
	 *	model's disableMatch() to set the matching target to an empty string,
	 *	and then the editor will update the view. As a result, the highlighting
	 *	of matching phrases will be cleared.
	 */
	abstract void disableMatch();

	abstract void goToLine(int lineNumber);

	abstract void newFile();
	abstract void openFile(File file);

	static boolean isHanzi(char c) {
		return (c >= 0x2e80) ? true : false;
	}

	static abstract class AdaptiveCanvas extends Canvas {

		private Method setRenderingHint = null;
		private Object[] setRenderingHintArguments = null;

		private Image bufferImage;

		void enableRenderingHint(Graphics g) {
			try {
				if (setRenderingHint == null) {
					Class c = Class.forName("java.awt.Graphics2D");
					Class[] types = new Class[] {
						Class.forName("java.awt.RenderingHints$Key"),
						Object.class
					};
					setRenderingHint = c.getMethod("setRenderingHint", types);
				}
				if (setRenderingHintArguments == null) {
					Class c = Class.forName("java.awt.RenderingHints");
					setRenderingHintArguments = new Object[] {
						c.getField("KEY_TEXT_ANTIALIASING").get(null),
						c.getField("VALUE_TEXT_ANTIALIAS_LCD_HRGB").get(null)
					};
				}
				setRenderingHint.invoke(g, setRenderingHintArguments);
			} catch (Exception e) {}
		}

		public void update(Graphics g) {
			paint(g);
		}

		public void paint(Graphics g) {
			int width = getSize().width;
			int height = getSize().height;
			if ((width <= 0) || (height <= 0)) {
				return;
			}
			if (bufferImage == null
					|| (bufferImage.getWidth(this) != width)
					|| (bufferImage.getHeight(this) != height)) {
				bufferImage = createImage(width, height);
			}
			Graphics bg = bufferImage.getGraphics();
			enableRenderingHint(bg);

			paint(bg, width, height);

			g.drawImage(bufferImage, 0, 0, this);
		}

		/*
		 *	This method is used to replace paint() method of the canvas to
		 *	obtain the double buffering and anti-aliasing features.
		 */
		abstract void paint(Graphics bg, int width, int height);

	}

	static class AdaptiveFileDialog extends FileDialog {

		AdaptiveFileDialog(Frame parent, String title, int mode) {
			super(parent, title, mode);
			try {
				Method m;
				m = getClass().getMethod("setMultipleMode",
						new Class[] { boolean.class });
				m.invoke(this, new Object[] { Boolean.TRUE });
			} catch (Exception e) {}
		}

		File[] getMutipleFiles() {
			File[] files;
			try {
				Method m;
				m = getClass().getMethod("getFiles", new Class[] {});
				files = (File[]) m.invoke(this, new Object[] {});
			} catch (Exception e) {
				if (getFile() == null) {
					files = new File[0];
				} else {
					files = new File[1];
					files[0] = new File(getDirectory() + getFile());
				}
			}
			return files;
		}

	}

	static AbstractView createView(
			AbstractEditor editor, AbstractModel model) {
		Object o;
		try {
			Class c = Class.forName("java.awt.event.MouseWheelEvent");
			// must be at least Java 1.4 to continue
			String mv = "io.github.jtchen.antique.ModernView";
			o = Class.forName(mv).getDeclaredConstructors()[0]
					.newInstance(new Object[] { editor, model });
		} catch (Exception e) {
			o = new View(editor, model);
		}
		return (AbstractView) o;
	}

}
