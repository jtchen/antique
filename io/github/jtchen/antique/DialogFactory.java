/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.event.*;

/*
 *	This class interacts with the editor to help create various dialogs.
 */
class DialogFactory {

	static final int YES = 0;
	static final int NO = 1;
	static final int CANCEL = 2;

	private static final String DLG_OK = "OK";
	private static final String DLG_YES = "Yes";
	private static final String DLG_NO = "No";
	private static final String DLG_CANCEL = "Cancel";
	private static final String DLG_MATCH_CASE = "Match case";
	private static final String DLG_WHOLE_WORD = "Find whole words only";
	private static final String DLG_UP = "Up";
	private static final String DLG_DOWN = "Down";
	private static final String DLG_FIND = "Find";
	private static final String DLG_FIND_WHAT = "Find what:";
	private static final String DLG_DIRECTION = "Direction:";
	private static final String DLG_FIND_NEXT = "Find Next";
	private static final String DLG_REPLACE = "Replace";
	private static final String DLG_REPLACE_WITH = "Replace with:";
	private static final String DLG_REPLACE_ALL = "Replace All";
	private static final String DLG_GOTO_LINE = "Goto line";
	private static final String DLG_LINE_NUMBER = "Line Number:";

	/* ---- The dialog-building components --------------------------------- */

	/*
	 *	The design of this class is that all components in the dialog will use
	 *	the dialog's event listener. Please note that the dialog itself does
	 *	not require a ActionListener, and this interface is used for buttons or
	 *	textfields in the dialog.
	 */
	abstract static class GenericDialog extends Dialog
			implements ActionListener, KeyListener {

		AbstractEditor editor;

		GenericDialog(AbstractEditor editor, String title, boolean isModal) {
			super(editor, title, isModal);
			this.editor = editor;
			setFont(editor.getFont());
			setResizable(false);
			addKeyListener(this);
			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					dispose();
				}
			});
		}

		Point computeLocation() {
			Point p = editor.getLocation();
			p.x += (editor.getSize().width - getSize().width) / 2;
			p.y += (editor.getSize().height - getSize().height) / 2;
			return p;
		}

		public void setVisible(boolean isVisible) {
			if (isVisible) {
				setLocation(computeLocation());
			}
			super.setVisible(isVisible);
		}

		public void keyTyped(KeyEvent e) {
			if (e.getKeyChar() == KeyEvent.VK_ESCAPE) {
				dispose();
			}
		}

		public void keyPressed(KeyEvent e) {}
		public void keyReleased(KeyEvent e) {}

		abstract public void actionPerformed(ActionEvent e);

	}

	private static class DialogPanel extends Panel {

		static final int CENTER = 0;
		static final int LEFT = 1;

		DialogPanel(int alignment) {
			if (alignment == CENTER) {
				setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
			} else if (alignment == LEFT) {
				setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
			}
		}

		public void paint(Graphics g) {
			g.setColor(SystemColor.control);
			g.fillRect(0, 0, getSize().width, getSize().height);
		}

	}

	private static class DialogButton extends Button {
		DialogButton(String label, GenericDialog dialog) {
			super(label);
			addKeyListener(dialog);
			addActionListener(dialog);
		}
	}

	/* ---- Dialogs used in the editor ------------------------------------- */

	static class AlertDialog extends GenericDialog {

		Label label = new Label("", Label.CENTER);

		AlertDialog(AbstractEditor editor) {
			super(editor, Main.NAME, true);

			setLayout(new GridLayout(2, 1));
			add(label);
			Panel p = new DialogPanel(DialogPanel.CENTER);
			p.add(new DialogButton(DLG_OK, this));
			add(p);
		}

		void setText(String s) {
			label.setText(s);
		}

		public void actionPerformed(ActionEvent e) {
			dispose();
		}

	}

	static class ConfirmDialog extends GenericDialog {

		private int answer = CANCEL; // the default value

		ConfirmDialog(AbstractEditor editor, String message) {
			super(editor, Main.NAME, true);

			setLayout(new GridLayout(2, 1));
			add(new Label(message, Label.CENTER));
			Panel p = new DialogPanel(DialogPanel.CENTER);
			p.add(new DialogButton(DLG_YES, this));
			p.add(new DialogButton(DLG_NO, this));
			p.add(new DialogButton(DLG_CANCEL, this));
			add(p);
		}

		int getAnswer() {
			return answer;
		}

		public void actionPerformed(ActionEvent e) {
			String cmd = e.getActionCommand();
			if (cmd.equals(DLG_YES)) {
				answer = YES;
			} else if (cmd.equals(DLG_NO)) {
				answer = NO;
			} else { // cmd.equals(DLG_CANCEL)
				answer = CANCEL;
			}
			dispose();
		}

	}

	static class FindDialog extends GenericDialog implements ItemListener {

		static Point location = null; // save the location if a user moves it

		TextField findField = new TextField(32);
		Checkbox matchCaseCheckbox = new Checkbox(DLG_MATCH_CASE);
		Checkbox wholeWordCheckbox = new Checkbox(DLG_WHOLE_WORD);
		CheckboxGroup directionGroup = new CheckboxGroup();
		Checkbox upCheckbox = new Checkbox(DLG_UP, directionGroup, false);
		Checkbox downCheckbox = new Checkbox(DLG_DOWN, directionGroup, true);

		FindDialog(AbstractEditor editor, String title) {
			super(editor, title, false);
			findField.addKeyListener(this);
			findField.addActionListener(this);
			matchCaseCheckbox.addItemListener(this);
			wholeWordCheckbox.addItemListener(this);
			upCheckbox.addItemListener(this);
			downCheckbox.addItemListener(this);
		}

		FindDialog(AbstractEditor editor) {
			this(editor, DLG_FIND);

			setLayout(new GridLayout(4, 1));
			Panel p1 = new DialogPanel(DialogPanel.LEFT);
			p1.add(new Label(DLG_FIND_WHAT));
			add(p1);
			add(findField);
			Panel p2 = new DialogPanel(DialogPanel.LEFT);
			p2.add(new Label(DLG_DIRECTION));
			p2.add(upCheckbox);
			p2.add(downCheckbox);
			add(p2);
			Panel p3 = new DialogPanel(DialogPanel.CENTER);
			p3.add(matchCaseCheckbox);
			p3.add(wholeWordCheckbox);
			p3.add(new DialogButton(DLG_FIND_NEXT, this));
			p3.add(new DialogButton(DLG_CANCEL, this));
			add(p3);
		}

		Point computeLocation() {
			if (FindDialog.location == null) {
				return super.computeLocation();
			}
			return FindDialog.location;
		}

		public void setVisible(boolean isVisible) {
			if (isVisible) {
				AbstractEditor.MatchConfig mc = editor.getMatchConfig();
				matchCaseCheckbox.setState(mc.isCaseSensitiveMatch);
				wholeWordCheckbox.setState(mc.isWholeWordMatch);
				if (mc.isForwardMatch) {
					directionGroup.setSelectedCheckbox(downCheckbox);
				} else {
					directionGroup.setSelectedCheckbox(upCheckbox);
				}
			}
			super.setVisible(isVisible);
		}

		void setTarget(String s) {
			findField.setText(s);
			findField.setCaretPosition(s.length());
			findField.selectAll();
		}

		boolean isForwardMatch() {
			return (directionGroup.getSelectedCheckbox() == downCheckbox);
		}

		public void keyPressed(KeyEvent e) {
			String s = findField.getText();
			int keyCode = e.getKeyCode();
			if (e.isControlDown() && (keyCode == KeyEvent.VK_H)) {
				editor.popupReplaceDialog(s);
			} else if (keyCode == KeyEvent.VK_F3) {
				AbstractEditor.MatchConfig mc = editor.getMatchConfig();
				mc.target = s;
				mc.isForwardMatch = (! e.isShiftDown());
				editor.findNext();
			} else {
				super.keyPressed(e);
			}
		}

		public void actionPerformed(ActionEvent e) {
			String s = findField.getText();
			String cmd = e.getActionCommand();
			if (cmd.equals(DLG_CANCEL)) {
				dispose();
			} else if ((e.getSource() == findField)
					|| cmd.equals(DLG_FIND_NEXT)) {
				AbstractEditor.MatchConfig mc = editor.getMatchConfig();
				mc.target = s;
				mc.isForwardMatch = isForwardMatch();
				editor.findNext();
			}
		}

		public void itemStateChanged(ItemEvent e) {
			AbstractEditor.MatchConfig mc = editor.getMatchConfig();
			if (e.getSource() == matchCaseCheckbox) {
				mc.isCaseSensitiveMatch = matchCaseCheckbox.getState();
			} else if (e.getSource() == wholeWordCheckbox) {
				mc.isWholeWordMatch = wholeWordCheckbox.getState();
			} else {
				mc.isForwardMatch = isForwardMatch();
			}
		}

		public void dispose() {
			if (! getLocation().equals(super.computeLocation())) {
				FindDialog.location = getLocation();
			}
			super.dispose();
		}

	}

	static class ReplaceDialog extends FindDialog {

		TextField replaceField = new TextField(32);

		ReplaceDialog(AbstractEditor editor) {
			super(editor, DLG_REPLACE);
			replaceField.addKeyListener(this);
			replaceField.addActionListener(this);

			setLayout(new GridLayout(6, 1));
			Panel p1 = new DialogPanel(DialogPanel.LEFT);
			p1.add(new Label(DLG_FIND_WHAT));
			add(p1);
			add(findField);
			Panel p2 = new DialogPanel(DialogPanel.LEFT);
			p2.add(new Label(DLG_REPLACE_WITH));
			add(p2);
			add(replaceField);
			Panel p3 = new DialogPanel(DialogPanel.LEFT);
			p3.add(new Label(DLG_DIRECTION));
			p3.add(upCheckbox);
			p3.add(downCheckbox);
			add(p3);
			Panel p4 = new DialogPanel(DialogPanel.CENTER);
			p4.add(matchCaseCheckbox);
			p4.add(wholeWordCheckbox);
			p4.add(new DialogButton(DLG_FIND_NEXT, this));
			p4.add(new DialogButton(DLG_REPLACE, this));
			p4.add(new DialogButton(DLG_REPLACE_ALL, this));
			p4.add(new DialogButton(DLG_CANCEL, this));
			add(p4);
		}

		public void keyPressed(KeyEvent e) {
			String s = findField.getText();
			if (e.isControlDown() && (e.getKeyCode() == KeyEvent.VK_F)) {
				editor.popupFindDialog(s);
			} else {
				super.keyPressed(e);
			}
		}

		public void actionPerformed(ActionEvent e) {
			String s = findField.getText();
			String cmd = e.getActionCommand();
			AbstractEditor.MatchConfig mc = editor.getMatchConfig();
			mc.target = s;
			mc.replacement = replaceField.getText();
			mc.isForwardMatch = isForwardMatch();
			if ((e.getSource() == replaceField) || cmd.equals(DLG_REPLACE)) {
				editor.replace();
			} else if (cmd.equals(DLG_REPLACE_ALL)) {
				editor.replaceAll();
			}
			super.actionPerformed(e);
		}

	}

	static class GoToDialog extends GenericDialog {

		TextField lineNumberField = new TextField(32);

		GoToDialog(AbstractEditor editor) {
			super(editor, DLG_GOTO_LINE, false);
			lineNumberField.addKeyListener(this);
			lineNumberField.addActionListener(this);

			setLayout(new GridLayout(3, 1));
			Panel p1 = new DialogPanel(DialogPanel.LEFT);
			p1.add(new Label(DLG_LINE_NUMBER));
			add(p1);
			add(lineNumberField);
			Panel p2 = new DialogPanel(DialogPanel.CENTER);
			p2.add(new DialogButton(DLG_OK, this));
			p2.add(new DialogButton(DLG_CANCEL, this));
			add(p2);
		}

		public void setVisible(boolean isVisible) {
			if (isVisible) {
				lineNumberField.setText("");
			}
			super.setVisible(isVisible);
		}

		public void actionPerformed(ActionEvent e) {
			String text = lineNumberField.getText();
			if (e.getActionCommand().equals(DLG_CANCEL)) {
				dispose();
			} else if (text.length() > 0) {
				try {
					editor.goToLine(Integer.parseInt(text));
					dispose();
				} catch (NumberFormatException ex) {
					lineNumberField.setText("");
				}
			}
		}

	}

}
