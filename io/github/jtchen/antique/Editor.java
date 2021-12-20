/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

/*
 *	This is the principal class of the editor's user interface and the handler
 *	for menu items. The user can select a tab from the tab bar, and then the
 *	editor will select the corresponding controller to receive user input.
 *
 *	In the current implementation, there is only one view in the entire editor.
 *	The main reason for this design is that when switching views, the editing
 *	area will flicker and the solution will be complicated. Therefore, all tabs
 *	use the view, and there are methods to change the connected components.
 *
 *	Please note that the editor holds references of some dialogs in order to
 *	"realize" them in advance and shorten the time interval between the a user
 *	pressing the shortcut and the system displaying a dialog. If the dialog
 *	pops up too late, some user input may be placed in the document, which is
 *	the wrong place.
 */
class Editor extends AbstractEditor {

	private static final String UNTITLED = "(Untitled)";

	private static final String MSG_EXCEED_PASTE_LIMIT
			= "The clipboard content is too large.";
	private static final String MSG_CANNOT_FIND_MATCH
			= Main.NAME + " was unable to find a matched text.";
	private static final String MSG_CANNOT_READ_DIRECTORY
			= Main.NAME + " was unable to read a directory.";
	private static final String MSG_CANNOT_READ_FILE
			= Main.NAME + " was unable to read the file.";
	private static final String MSG_EXCEED_FILESIZE_LIMIT
			= "This file is too large to open.";
	private static final String MSG_UNSAVED_FILE
			= "The file has not been saved yet. Save it now?";
	private static final String MSG_OPENED_FILE
			= "A file of that name was already opened.";
	private static final String MSG_READONLY_FILE
			= "This file is read-only.";
	private static final String MSG_CANNOT_WRITE_FILE
			= Main.NAME + " was unable to write the file.";

	private static final String DLG_SAVE_AS = "Save As";
	private static final String DLG_OPEN = "Open";

	private static final String MEN_FILE = "File";
	private static final String MEN_NEW = "New";
	private static final String MEN_OPEN = "Open...";
	private static final String MEN_CLOSE = "Close";
	private static final String MEN_SAVE = "Save";
	private static final String MEN_SAVE_AS = "Save As...";
	private static final String MEN_EXIT = "Exit";
	private static final String MEN_EDIT = "Edit";
	private static final String MEN_UNDO = "Undo";
	private static final String MEN_REDO = "Redo";
	private static final String MEN_CUT = "Cut";
	private static final String MEN_COPY = "Copy";
	private static final String MEN_PASTE = "Paste";
	private static final String MEN_FIND = "Find...";
	private static final String MEN_FIND_NEXT = "Find Next";
	private static final String MEN_FIND_PREVIOUS = "Find Previous";
	private static final String MEN_REPLACE = "Replace...";
	private static final String MEN_GO_TO = "Go To...";
	private static final String MEN_SELECT_ALL = "Select All";
	private static final String MEN_FORMAT = "Format";
	private static final String MEN_WORD_WRAP = "Word Wrap";

	private Dimension defaultSize = new Dimension(WIDTH, HEIGHT);

	private AbstractView view = null;

	private DialogFactory.FindDialog findDialog;
	private DialogFactory.ReplaceDialog replaceDialog;
	private DialogFactory.GoToDialog goToDialog;

	private MenuItem undoMenuItem;
	private MenuItem redoMenuItem;
	private MenuItem cutMenuItem;
	private MenuItem copyMenuItem;
	private MenuItem pasteMenuItem;
	private MenuItem findNextMenuItem;
	private MenuItem findPreviousMenuItem;
	private MenuItem selectAllMenuItem;
	private CheckboxMenuItem wordWrapItem;

	private Font mainFont;
	private Font fallbackFont;

	private String encoding;
	private int tabSize;

	private MatchConfig matchConfig = new MatchConfig();

	private TabCollection tabCollection;
	private Tab activeTab = null;
	private AbstractController activeController = null;

	Editor(Font mainFont, String encoding, int tabSize) {
		this.mainFont = mainFont;
		this.encoding = encoding;
		this.tabSize = tabSize;

		Menu fileMenu = new Menu(MEN_FILE);
		fileMenu.add(getMenuItem(MEN_NEW, KeyEvent.VK_N));
		fileMenu.add(getMenuItem(MEN_OPEN, KeyEvent.VK_O));
		fileMenu.add(getMenuItem(MEN_CLOSE, KeyEvent.VK_W));
		fileMenu.add(getMenuItem(MEN_SAVE, KeyEvent.VK_S));
		fileMenu.add(getMenuItem(MEN_SAVE_AS));
		fileMenu.addSeparator();
		fileMenu.add(getMenuItem(MEN_EXIT));

		Menu editMenu = new Menu(MEN_EDIT);
		undoMenuItem = getMenuItem(MEN_UNDO, KeyEvent.VK_Z);
		undoMenuItem.setEnabled(false);
		editMenu.add(undoMenuItem);
		redoMenuItem = getMenuItem(MEN_REDO, KeyEvent.VK_Y);
		redoMenuItem.setEnabled(false);
		editMenu.add(redoMenuItem);
		editMenu.addSeparator();
		cutMenuItem = getMenuItem(MEN_CUT, KeyEvent.VK_X);
		editMenu.add(cutMenuItem);
		copyMenuItem = getMenuItem(MEN_COPY, KeyEvent.VK_C);
		editMenu.add(copyMenuItem);
		pasteMenuItem = getMenuItem(MEN_PASTE, KeyEvent.VK_V);
		editMenu.add(pasteMenuItem);
		editMenu.addSeparator();
		editMenu.add(getMenuItem(MEN_FIND, KeyEvent.VK_F));
		findNextMenuItem = getMenuItem(MEN_FIND_NEXT);
		findNextMenuItem.setEnabled(false);
		editMenu.add(findNextMenuItem);
		findPreviousMenuItem = getMenuItem(MEN_FIND_PREVIOUS);
		findPreviousMenuItem.setEnabled(false);
		editMenu.add(findPreviousMenuItem);
		editMenu.add(getMenuItem(MEN_REPLACE, KeyEvent.VK_H));
		editMenu.add(getMenuItem(MEN_GO_TO, KeyEvent.VK_G));
		editMenu.addSeparator();
		selectAllMenuItem = getMenuItem(MEN_SELECT_ALL, KeyEvent.VK_A);
		editMenu.add(selectAllMenuItem);

		Menu formatMenu = new Menu(MEN_FORMAT);
		wordWrapItem = new CheckboxMenuItem(MEN_WORD_WRAP);
		wordWrapItem.addItemListener(this);
		formatMenu.add(wordWrapItem);

		MenuBar menuBar = new MenuBar();
		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(formatMenu);
		setMenuBar(menuBar);

		setBackground(Theme.CONTROL_BACKGROUND_COLOR);

		tabCollection = new TabCollection(this);
		add(tabCollection.getComponent(), BorderLayout.NORTH);

		findDialog = new DialogFactory.FindDialog(this);
		replaceDialog = new DialogFactory.ReplaceDialog(this);
		goToDialog = new DialogFactory.GoToDialog(this);
		findDialog.pack(); // realize these dialogs in advance
		replaceDialog.pack();
		goToDialog.pack();

		/*
		 *	This FocusListener was added for some old Java environments where
		 *	the focus will return to the editor when the dialog is closed, and
		 *	the keyboard operations will be frozen.
		 */
		addFocusListener(new FocusAdapter() {
			public void focusGained(FocusEvent e) {
				if (tabCollection.size() > 0) {
					activeController.requestViewFocus();
				}
			}
		});

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				confirmExit();
			}
			public void windowActivated(WindowEvent e) {
				updatePasteMenuItem();
			}
		});
		pack();
		setVisible(true);
	}

	private MenuItem getMenuItem(String name) {
		return getMenuItem(name, KeyEvent.VK_UNDEFINED);
	}

	private MenuItem getMenuItem(String name, int key) {
		MenuItem mi;
		if (key == KeyEvent.VK_UNDEFINED) {
			mi = new MenuItem(name);
		} else {
			mi = new MenuItem(name, new MenuShortcut(key));
		}
		mi.addActionListener(this);
		mi.setActionCommand(name);
		return mi;
	}

	public Dimension getPreferredSize() {
		return defaultSize;
	}

	public void setVisible(boolean isVisible) {
		if (isVisible) {
			Dimension screenSize = getToolkit().getScreenSize();
			int x = (screenSize.width - getSize().width) / 2;
			int y = (screenSize.height - getSize().height) / 2;
			setLocation(x, y);
		}
		super.setVisible(isVisible);
	}

	MatchConfig getMatchConfig() {
		return matchConfig;
	}

	int getTabSize() {
		return tabSize;
	}

	void setLineWrap(boolean isLineWrap) {
		wordWrapItem.setState(isLineWrap);
	}

	Font getMainFont() {
		return mainFont;
	}

	void setFallbackFont(Font fallbackFont) {
		this.fallbackFont = fallbackFont;
	}

	Font getFallbackFont() {
		return fallbackFont;
	}

	void keyType(char c) {
		int id = KeyEvent.KEY_TYPED;
		long when = System.currentTimeMillis();
		KeyEvent e = new KeyEvent(this, id, when, 0, KeyEvent.VK_UNDEFINED, c);
		activeController.keyTyped(e);
	}

	private String getFullPath() {
		String fullPath = "";
		try {
			fullPath = activeTab.file.getCanonicalPath();
		} catch (IOException e) {}
		return fullPath;
	}

	/* ---- Methods for the Controller ------------------------------------- */

	void doNextTab() {
		tabCollection.next();
	}

	void doPreviousTab() {
		tabCollection.previous();
	}

	void doTextChanged() {
		Tab tab = activeTab;
		boolean isTextChanged = true;
		if ((tab.controller.getCharCount() == tab.savedText.length())
				&& tab.controller.getText().equals(tab.savedText)) {
			isTextChanged = false;
		}

		if ((tab != null) && (tab.isTextChanged != isTextChanged)) {
			tab.isTextChanged = isTextChanged;
			String filename = UNTITLED;
			String fullPath = UNTITLED;
			if (tab.file != null) {
				filename = getCanonicalFilename(tab.file);
				fullPath = getFullPath();
			}
			tabCollection.tabBar.setTitle(filename);
			tabCollection.tabBar.setChanged(isTextChanged);
		}

		updateUndoRedoMenuItems();
		updateSelectAllMenuItem();
	}

	void doSelectionChanged() {
		updateCutCopyMenuItems();
	}

	int closeDialogs() {
		int count = 0;
		if (findDialog.isVisible()) {
			findDialog.dispose();
			count += 1;
		}
		if (replaceDialog.isVisible()) {
			replaceDialog.dispose();
			count += 1;
		}
		if (goToDialog.isVisible()) {
			goToDialog.dispose();
			count += 1;
		}
		return count;
	}

	/* ---- Event listeners ------------------------------------------------ */

	public void itemStateChanged(ItemEvent e) {
		if (wordWrapItem.equals((CheckboxMenuItem) e.getSource())) {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				activeController.setLineWrap(true);
			} else {
				activeController.setLineWrap(false);
			}
		}
	}

	private void updateUndoRedoMenuItems() {
		undoMenuItem.setEnabled(activeController.isUndoable());
		redoMenuItem.setEnabled(activeController.isRedoable());
	}

	private void updateCutCopyMenuItems() {
		boolean isSelected = activeController.isSelected();
		cutMenuItem.setEnabled(isSelected);
		copyMenuItem.setEnabled(isSelected);
	}

	private void updatePasteMenuItem() {
		if (activeTab != null) {
			String s = activeController.getClipboard();
			boolean isPastable = ((s != null) && (s.length() > 0));
			pasteMenuItem.setEnabled(isPastable);
		}
	}

	private void updateFindNextPreviousMenuItems() {
		boolean isFindable = (matchConfig.target.length() > 0);
		findNextMenuItem.setEnabled(isFindable);
		findPreviousMenuItem.setEnabled(isFindable);
	}

	private void updateSelectAllMenuItem() {
		int i = activeController.getCharCount();
		selectAllMenuItem.setEnabled((i > 0));
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if (cmd.equals(MEN_NEW)) {
			newFile();
		} else if (cmd.equals(MEN_OPEN)) {
			openFile();
		} else if (cmd.equals(MEN_CLOSE)) {
			tabCollection.remove(false);
		} else if (cmd.equals(MEN_SAVE)) {
			saveFile();
		} else if (cmd.equals(MEN_SAVE_AS)) {
			saveFileAs();
		} else if (cmd.equals(MEN_EXIT)) {
			confirmExit();
		} else if (cmd.equals(MEN_UNDO)) {
			activeController.undo();
		} else if (cmd.equals(MEN_REDO)) {
			activeController.redo();
		} else if (cmd.equals(MEN_CUT)) {
			activeController.cut();
			updatePasteMenuItem();
		} else if (cmd.equals(MEN_COPY)) {
			activeController.copy();
			updatePasteMenuItem();
		} else if (cmd.equals(MEN_PASTE)) {
			if (! activeController.paste()) {
				alertWithDialog(MSG_EXCEED_PASTE_LIMIT);
			}
		} else if (cmd.equals(MEN_FIND)) {
			popupFindDialog(createMatchTarget());
		} else if (cmd.equals(MEN_FIND_NEXT)) {
			matchConfig.isForwardMatch = true;
			findNext();
		} else if (cmd.equals(MEN_FIND_PREVIOUS)) {
			matchConfig.isForwardMatch = false;
			findNext();
		} else if (cmd.equals(MEN_REPLACE)) {
			popupReplaceDialog(createMatchTarget());
		} else if (cmd.equals(MEN_GO_TO)) {
			goToDialog.setVisible(true);
		} else if (cmd.equals(MEN_SELECT_ALL)) {
			activeController.selectAll();
		}
	}

	private String createMatchTarget() {
		String s = activeController.getSelectedText();
		if (s.length() == 0) {
			return matchConfig.target;
		} else if (s.indexOf('\n') == -1) { // prohibit multi-line targets
			return s;
		}
		return "";
	}

	void popupFindDialog(String target) {
		if (findDialog.isVisible()) {
			return;
		}
		if (replaceDialog.isVisible()) {
			replaceDialog.dispose();
		}
		findDialog.setVisible(true);
		// setTarget() must be placed after setVisible() for older JREs
		findDialog.setTarget(target);
	}

	void popupReplaceDialog(String target) {
		if (replaceDialog.isVisible()) {
			return;
		}
		if (findDialog.isVisible()) {
			findDialog.dispose();
		}
		replaceDialog.setVisible(true);
		// setTarget() must be placed after setVisible() for older JREs
		replaceDialog.setTarget(target);
	}

	/* ---- Methods for the DialogFactory ---------------------------------- */

	void findNext() {
		if ((matchConfig.target.length() > 0)
				&& (! activeController.findNext())) {
			alertWithDialog(MSG_CANNOT_FIND_MATCH);
		}
		updateFindNextPreviousMenuItems();
	}

	void replace() {
		if ((matchConfig.target.length() > 0)
				&& (! activeController.replace())) {
			alertWithDialog(MSG_CANNOT_FIND_MATCH);
		}
		updateFindNextPreviousMenuItems();
	}

	void replaceAll() {
		if ((matchConfig.target.length() > 0)
				&& (! activeController.replaceAll())) {
			alertWithDialog(MSG_CANNOT_FIND_MATCH);
		}
		updateFindNextPreviousMenuItems();
	}

	void disableMatch() {
		activeController.disableMatch();
		if (tabCollection.size() > 0) {
			view.updateCursorByModel();
		}
	}

	void goToLine(int lineNum) {
		activeController.goToLine(lineNum);
	}

	/* ---- Methods related to dialogs ------------------------------------- */

	private void alertWithDialog(String msg) {
		Toolkit.getDefaultToolkit().beep();
		DialogFactory.AlertDialog dlg = new DialogFactory.AlertDialog(this);
		dlg.setText(msg);
		dlg.pack();
		dlg.setVisible(true);
	}

	private File[] nameFileWithDialog(int mode) {
		String title = (mode == FileDialog.SAVE) ? DLG_SAVE_AS : DLG_OPEN;
		AdaptiveFileDialog dlg = new AdaptiveFileDialog(this, title, mode);
		File[] files;

		dlg.pack();
		dlg.setVisible(true);
		if (mode == FileDialog.SAVE) {
			if (dlg.getFile() == null) {
				files = new File[0];
			} else {
				files = new File[1];
				files[0] = new File(dlg.getDirectory() + dlg.getFile());
			}
		} else { // mode == FileDialog.LOAD
			files = dlg.getMutipleFiles();
		}

		activeController.requestViewFocus();
		return files;
	}

	/* ---- Methods for the file menu -------------------------------------- */

	void newFile() {
		tabCollection.add(null);
	}

	private void openFile() {
		File[] files = nameFileWithDialog(FileDialog.LOAD);
		if (files.length > 0) {
			for (int i = 0; i < files.length; i += 1) {
				openFile(files[i]);
			}
		}
	}

	void openFile(File namedFile) {
		if (namedFile.exists()) {
			boolean isFileOpenable = false;
			if (namedFile.isDirectory()) {
				alertWithDialog(MSG_CANNOT_READ_DIRECTORY);
			} else if (! namedFile.canRead()) {
				alertWithDialog(MSG_CANNOT_READ_FILE);
			} else if (namedFile.length() > MAX_FILE_SIZE) {
				alertWithDialog(MSG_EXCEED_FILESIZE_LIMIT);
			} else {
				isFileOpenable = true;
			}

			if (! isFileOpenable) {
				if (tabCollection.size() == 0) {
					newFile();
				}
				return;
			}
		}

		for (int i = 0; i < tabCollection.size(); i += 1) {
			File file = (tabCollection.get(i)).file;
			try {
				if ((file != null) && (file.getCanonicalPath()
						.equals(namedFile.getCanonicalPath()))) {
					tabCollection.select(i);
					return;
				}
			} catch (IOException e) {}
		}

		tabCollection.add(namedFile);

		while (tabCollection.size() >= 2) {
			int i = tabCollection.size() - 2;
			Tab tab = tabCollection.get(i);
			if ((tab.file == null) && (! tab.isTextChanged)) {
				tabCollection.select(i);
				tabCollection.remove(false);
				tabCollection.select(i);
			} else {
				break;
			}
		}
	}

	/*
	 *	If the user cancels the operation or there are any errors, this method
	 *	returns false.
	 */
	private boolean isTextChangeHandled() {
		if (activeController.getText().equals(activeTab.savedText)) {
			return true; // no need to save the file
		}

		DialogFactory.ConfirmDialog dlg
				= new DialogFactory.ConfirmDialog(this, MSG_UNSAVED_FILE);
		dlg.pack();
		dlg.setVisible(true);
		int answer = dlg.getAnswer();
		if (answer == DialogFactory.CANCEL) {
			return false;
		} else if (answer == DialogFactory.YES) {
			return saveFile();
		} else { // answer == DialogFactory.NO
			return true;
		}
	}

	/*
	 *	If there are any errors, this method returns false.
	 */
	private boolean saveFile() {
		if (activeTab.file == null) {
			return saveFileAs();
		} else {
			return saveSelectedFile();
		}
	}

	/*
	 *	If there are any errors, this method returns false.
	 */
	private boolean saveFileAs() {
		File[] files = nameFileWithDialog(FileDialog.SAVE);
		if (files.length == 0) {
			return false;
		}

		File file = files[0];
		if (file == null) {
			return false;
		}

		for (int i = 0; i < tabCollection.size(); i += 1) {
			File existFile = (tabCollection.get(i)).file;
			try {
				if ((existFile != null) && (file.getCanonicalPath()
						.equals(existFile.getCanonicalPath()))) {
					alertWithDialog(MSG_OPENED_FILE);
					return false;
				}
			} catch (IOException e) {}
		}

		activeTab.file = file;
		return saveSelectedFile();
	}

	/*
	 *	If there are any errors, this method returns false.
	 */
	private boolean saveSelectedFile() {
		File file = activeTab.file;
		if (file.exists() && (! file.canWrite())) {
			if (file.canRead()) {
				alertWithDialog(MSG_READONLY_FILE);
			} else {
				// in case if this file cannot be read or written
				alertWithDialog(MSG_CANNOT_WRITE_FILE);
			}
			return false;
		}

		activeTab.savedText = activeController.getText();
		writeFile(file, activeTab.savedText);
		activeTab.isTextChanged = false;

		String filename = getCanonicalFilename(file);
		if (filename.endsWith(".md") || filename.endsWith(".markdown")) {
			tabCollection.setSyntax(MARKDOWN_SYNTAX);
		} else if (filename.endsWith(".java")) {
			tabCollection.setSyntax(JAVA_SYNTAX);
		} else {
			tabCollection.setSyntax(PLAIN_SYNTAX);
		}
		tabCollection.tabBar.setTitle(filename);
		tabCollection.tabBar.setChanged(false);
		setTitle(getFullPath() + " - " + Main.NAME);
		return true;
	}

	private static String getCanonicalFilename(File file) {
		String filename = null;
		try {
			filename = file.getCanonicalPath();
		} catch (IOException e) {}
		if (filename != null) {
			int i = filename.lastIndexOf(File.separatorChar);
			if (i > 0) {
				filename = filename.substring(i + 1);
			}
		} else {
			filename = file.getName();
		}
		return filename;
	}

	private void confirmExit() {
		for (int i = (tabCollection.size() - 1); i >= 0; i -= 1) {
			tabCollection.remove(true);
		}
	}

	/* ---- The file input/output methods ---------------------------------- */

	private String readFile(File file) {
		StringBuffer raw = new StringBuffer();
		Reader reader = null;
		try {
			reader = new InputStreamReader(
					new FileInputStream(file), encoding);
			char[] buf = new char[8192];
			int read;
			while ((read = reader.read(buf, 0, buf.length)) > 0) {
				raw.append(buf, 0, read);
			}
		} catch (IOException e) {
			System.out.println(e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {}
		}

		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < raw.length(); i += 1) {
			char c = raw.charAt(i);
			if (c == '\r') {
				if (((i + 1) < raw.length()) && (raw.charAt(i + 1) == '\n')) {
					i += 1;
				}
				c = '\n';
			}
			sb.append(c);
		}
		return sb.toString();
	}

	private void writeFile(File file, String s) {
		char[] chars = s.toCharArray();
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(file), encoding));
			for (int i = 0; i < chars.length; i += 1) {
				char c = chars[i];
				if (c == '\n') {
					bw.newLine();
				} else {
					bw.write((int) c);
				}
			}
		} catch (IOException e) {
			System.out.println(e);
		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
			} catch (IOException e) {}
		}
	}

	/* ---- Inner classes for using the tabs ------------------------------- */

	private class Tab {

		private AbstractModel model;

		File file;
		String savedText = "";
		boolean isTextChanged = false;
		byte syntax;
		AbstractController controller;
		Point viewportOrigin = new Point();

		Tab(File file) {
			this.file = file; // it can be null
			if (file != null) {
				String filename = getCanonicalFilename(file);
				if (filename.endsWith(".md")
						|| filename.endsWith(".markdown")) {
					model = new MarkdownSourceModel();
					syntax = MARKDOWN_SYNTAX;
				} else if (filename.endsWith(".java")) {
					model = new JavaSourceModel();
					syntax = JAVA_SYNTAX;
				} else {
					model = new PassiveModel();
					syntax = PLAIN_SYNTAX;
				}
			} else {
				model = new PassiveModel();
				syntax = PLAIN_SYNTAX;
			}

			if (view == null) {
				view = AbstractEditor.createView(Editor.this, model);
			} else {
				updateViewModel();
			}
			controller = new Controller(Editor.this, model, view);
		}

		void updateViewModel() {
			view.setModel(model);
		}

		synchronized void changeModelSyntax(byte syntax) {
			this.syntax = syntax;
			AbstractModel newModel;
			if (syntax == MARKDOWN_SYNTAX) {
				newModel = new MarkdownSourceModel();
			} else if (syntax == JAVA_SYNTAX) {
				newModel = new JavaSourceModel();
			} else {
				newModel = new PassiveModel();
			}
			newModel.cloneFrom(model);

			model = newModel;
			updateViewModel();
			controller.setModel(model);
			view.updateTextByModel();
		}

	}

	private class TabCollection {

		private TabBar tabBar;

		private int index = 0;
		private Vector tabVector = new Vector(); // of Tab

		/*
		 *	When dragging a tab with the mouse, use this flag to lock the
		 *	TabCollection instance to avoid modifying its components during the
		 *	tab reordering process.
		 */
		private boolean isLocked = false;

		TabCollection(AbstractEditor editor) {
			tabBar = new TabBar(editor, this);
		}

		void lock() {
			this.isLocked = true;
		}

		void unlock() {
			this.isLocked = false;
		}

		Component getComponent() {
			return tabBar;
		}

		int size() {
			return tabVector.size();
		}

		Tab get(int index) {
			return (Tab) tabVector.elementAt(index);
		}

		void setSyntax(byte syntax) {
			if (activeTab.syntax != syntax) {
				activeTab.changeModelSyntax(syntax);
			}
		}

		synchronized void add(File file) {
			if (isLocked) {
				return;
			}

			Tab tab = new Tab(file);
			if (size() == 0) {
				tab.controller.setEnabled(true);
				Container c = tab.controller.getViewContainer();
				Editor.this.add(c, BorderLayout.CENTER);
			}

			if (file == null) {
				tabBar.add(UNTITLED);
				tab.savedText = "";
			} else {
				tabBar.add(getCanonicalFilename(file));
				if (file.exists()) {
					tab.savedText = readFile(file);
					/*
					 *	This synchronization is required because if multiple
					 *	files are opened programmatically, it may not have
					 *	enough time to completely update the view.
					 */
					synchronized (view) {
						tab.controller.setInitialText(tab.savedText);
					}
				} else {
					tab.savedText = "";
				}
			}
			tabVector.addElement(tab);
			select(size() - 1);
		}

		synchronized void remove(boolean isExiting) {
			if (isLocked) {
				return;
			}

			if (isTextChangeHandled()) {
				activeController.setEnabled(false);
				if (size() == 1) {
					if (isExiting) {
						System.exit(0);
					} else {
						Tab tab = new Tab(null);
						tabVector.setElementAt(tab, 0);
						tab.controller.setEnabled(true);
						tabBar.setTitle(UNTITLED);
						select(0);
					}
				} else {
					tabVector.removeElementAt(index);
					tabBar.remove(index);
					int newIndex = Math.max(index - 1, 0);
					index = -1; // the current tab does not exist
					select(newIndex);
				}
			} else if (isExiting) {
				select(Math.max(index - 1, 0));
			}
		}

		void next() {
			if (isLocked) {
				return;
			}

			int i = index + 1;
			if (i > (size() - 1)) {
				i = 0;
			}
			select(i);
		}

		void previous() {
			if (isLocked) {
				return;
			}

			int i = index - 1;
			if (i < 0) {
				i = size() - 1;
			}
			select(i);
		}

		synchronized void select(int newIndex) {
			AbstractController oldController = null;
			if (index >= 0) {
				oldController = get(index).controller;
				Rectangle viewport = oldController.getViewport();
				get(index).viewportOrigin = new Point(viewport.x, viewport.y);
			}

			index = newIndex;
			tabBar.select(index);
			activeTab = get(index);
			activeController = activeTab.controller;
			activeController.setLineWrap(wordWrapItem.getState());
			if (activeController != oldController) {
				if (oldController != null) {
					oldController.setEnabled(false);
				}
				activeController.setEnabled(true);
			}
			activeTab.updateViewModel();
			view.updateTextByModel(activeTab.viewportOrigin);
			validate();
			activeController.requestViewFocus();

			if (activeTab.file == null) {
				setTitle(UNTITLED + " - " + Main.NAME);
			} else {
				setTitle(getFullPath() + " - " + Main.NAME);
			}

			updateUndoRedoMenuItems();
			updateCutCopyMenuItems();
			updatePasteMenuItem();
			updateSelectAllMenuItem();
		}

		synchronized void moveIndexTo(int fromIndex, int toIndex) {
			Tab tab = get(index);
			tabVector.removeElementAt(fromIndex);
			tabVector.insertElementAt(tab, toIndex);
			select(toIndex);
		}

	}

	private class TabBar extends AdaptiveCanvas implements MouseListener,
			MouseMotionListener, ComponentListener, FocusListener {

		private AbstractEditor editor;
		private TabCollection tabCollection;

		private FontMetrics fontMetrics;
		private int lineHeight;
		private int ascent;
		private int numberWidth;

		private int index = 0;
		private int lineCount = 1;

		private int width = 0;
		private int height = 0;

		private boolean isDragging = false;
		private Point origin = new Point(0, 0);
		private Point draggingPoint = null;

		private Vector itemVector = new Vector(); // of Item

		private class Item {
			int x;
			int y;
			int width;
			String title;
			boolean isChanged = false;

			Item(int x, int y, int width, String title) {
				this.x = x;
				this.y = y;
				this.width = width;
				this.title = title;
			}
		}

		TabBar(AbstractEditor editor, TabCollection tabCollection) {
			this.editor = editor;
			this.tabCollection = tabCollection;

			Font mainFont = editor.getMainFont();
			fontMetrics = getFontMetrics(mainFont);
			lineHeight = fontMetrics.getHeight();
			ascent = fontMetrics.getAscent();
			numberWidth = fontMetrics.charWidth('0');

			addMouseListener(this);
			addMouseMotionListener(this);
			addComponentListener(this);
			addFocusListener(this);
		}

		public Dimension getPreferredSize() {
			return new Dimension(0, lineCount * lineHeight);
		}

		void select(int index) {
			this.index = index;
			repaint();
		}

		private int getTitleWidth(String title) {
			int width = 0;
			for (int i = 0; i < title.length(); i += 1) {
				char c = title.charAt(i);
				if (AbstractEditor.isHanzi(c)) {
					width += (2 * numberWidth);
				} else {
					width += fontMetrics.charWidth(c);
				}
			}
			return width + (2 * numberWidth);
		}

		synchronized void add(String title) {
			int size = itemVector.size();
			int x;
			if (size == 0) {
				x = 0;
			} else {
				Item item = (Item) itemVector.elementAt(size - 1);
				x = item.x + item.width;
			}
			int w = getTitleWidth(title);
			if (((x + w) > width) && (size != 0)) {
				x = 0;
				lineCount += 1;
			}
			int y = (lineCount - 1) * lineHeight;
			itemVector.addElement(new Item(x, y, w, title));
			invalidate();
		}

		private void updateItemLocations() {
			int x = 0;
			int lc = 1;
			width = getSize().width;
			for (int i = 0; i < itemVector.size(); i += 1) {
				Item item = (Item) itemVector.elementAt(i);
				int w = item.width;
				if (((x + w) > width) && (i != 0)) {
					x = 0;
					lc += 1;
				}
				item.x = x;
				item.y = (lc - 1) * lineHeight;
				x += w;
			}
			lineCount = lc;
			invalidate();
		}

		void remove(int index) {
			itemVector.removeElementAt(index);
			updateItemLocations();
		}

		String getTitle() {
			return ((Item) itemVector.elementAt(index)).title;
		}

		synchronized void setTitle(String title) {
			Item item = (Item) itemVector.elementAt(index);
			item.title = title;
			item.width = getTitleWidth(title);
			updateItemLocations();
			editor.validate();
			repaint();
		}

		synchronized void setChanged(boolean isChanged) {
			Item item = (Item) itemVector.elementAt(index);
			item.isChanged = isChanged;
			repaint();
		}

		/* ---- Event listeners -------------------------------------------- */

		public void focusGained(FocusEvent e) {
			if (tabCollection.size() > 0) {
				activeController.requestViewFocus();
			}
		}

		public void focusLost(FocusEvent e) {}

		private void selectItemByPoint(Point p) {
			for (int i = 0; i < itemVector.size(); i += 1) {
				Item item = (Item) itemVector.elementAt(i);
				if ((p.x > item.x) && ((item.x + item.width) > p.x)
						&& (p.y > item.y) && ((item.y + lineHeight) > p.y)) {
					this.index = i;
					tabCollection.select(i);
					return;
				}
			}
		}

		public void mousePressed(MouseEvent e) {
			origin = e.getPoint();
			selectItemByPoint(origin);
		}

		/*
		 *	The design principle is to move the moving tab to a new location
		 *	first, and then sequentially fill in other tabs until it reaches
		 *	the moving tab.
		 */
		private synchronized void moveItemByPoint(Point p) {
			Item item = (Item) itemVector.elementAt(index);
			boolean isMoving = (Math.abs(p.y - origin.y) > (lineHeight / 2))
					|| (Math.abs(p.x - origin.x) > (item.width / 2));
			if (isMoving && (! isDragging)) {
				int x = item.x + (p.x - origin.x);
				int y = item.y + (p.y - origin.y);
				if (y > (height - lineHeight)) {
					y = height - lineHeight;
				} else if (y < 0) {
					y = 0;
				}

				int row = (y + (lineHeight / 2)) / lineHeight;
				int newIndex = 0;
				int w = 0;
				for (int i = 0; i < itemVector.size(); i += 1) {
					if (i == index) {
						continue;
					}

					Item it = (Item) itemVector.elementAt(i);
					newIndex += 1;
					w += it.width;
					if (w > width) {
						w = it.width;
						row -= 1;
					}

					int middle = w - (it.width / 2);
					if (((row == 0) && (middle > x)) || (row < 0)) {
						newIndex -= 1;
						break;
					}
				}
				moveIndexTo(newIndex);
				return;
			}
			repaint();
		}

		private void moveIndexTo(int toIndex) {
			Item item = (Item) itemVector.elementAt(index);
			itemVector.removeElementAt(index);
			itemVector.insertElementAt(item, toIndex);
			updateItemLocations();

			tabCollection.moveIndexTo(index, toIndex);
			index = toIndex;
		}

		public void mouseDragged(MouseEvent e) {
			draggingPoint = e.getPoint();
			if (! isDragging) {
				isDragging = true;
				selectItemByPoint(draggingPoint);
				tabCollection.lock();
			}
			moveItemByPoint(draggingPoint);
		}

		public void mouseReleased(MouseEvent e) {
			draggingPoint = e.getPoint();
			isDragging = false;
			moveItemByPoint(draggingPoint);
			tabCollection.unlock();
		}

		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {}
		public void mouseMoved(MouseEvent e) {}

		public void componentResized(ComponentEvent e) {
			updateItemLocations();
			editor.validate();
		}

		public void componentMoved(ComponentEvent e) {}
		public void componentShown(ComponentEvent e) {}
		public void componentHidden(ComponentEvent e) {}

		/* ---- Routine methods for painting ------------------------------- */

		private void drawTitle(Graphics bg, int x, int y, char[] title) {
			int offset = 0;
			for (int i = 0; i < title.length; i += 1) {
				char c = title[i];
				if (AbstractEditor.isHanzi(c)) {
					bg.setFont(editor.getFallbackFont());
					bg.drawChars(title, i, 1, x + offset, y);
					offset += (2 * numberWidth);
				} else {
					bg.setFont(mainFont);
					bg.drawChars(title, i, 1, x + offset, y);
					offset += fontMetrics.charWidth(c);
				}
			}
		}

		void paint(Graphics bg, int width, int height) {
			this.width = width;
			this.height = height;

			bg.setColor(Theme.CONTROL_BACKGROUND_COLOR);
			bg.fillRect(0, 0, width, height);
			bg.setColor(Theme.CONTROL_FOREGROUND_COLOR);
			for (int i = 0; i < itemVector.size(); i += 1) {
				Item it = (Item) itemVector.elementAt(i);
				if (it.x != 0) {
					bg.drawLine(it.x, it.y + 2, it.x, it.y + lineHeight - 4);
				}
				if (i != index) {
					int x = it.x + numberWidth;
					int y = it.y + ascent;
					drawTitle(bg, x, y, it.title.toCharArray());
					if (it.isChanged) {
						bg.drawLine(x, y, x + it.width - (2 * numberWidth), y);
					}
				}
			}

			if (index < itemVector.size()) {
				Item it = (Item) itemVector.elementAt(index);
				int dx = (isDragging) ? (draggingPoint.x - origin.x) : 0;
				int dy = (isDragging) ? (draggingPoint.y - origin.y) : 0;
				int x = it.x + dx;
				int y = it.y + dy;
				if (y > (height - lineHeight)) {
					y = (height - lineHeight);
				} else if (y < 0) {
					y = 0;
				}

				bg.setColor(Theme.BACKGROUND_COLOR);
				bg.fillRect(x + 1, y, it.width - 2, lineHeight);
				x += numberWidth;
				y += ascent;
				bg.setColor(Theme.FOREGROUND_COLOR);
				drawTitle(bg, x, y, it.title.toCharArray());
				if (it.isChanged) {
					bg.drawLine(x, y, x + it.width - (2 * numberWidth), y);
				}
			}
		}

	}

}
