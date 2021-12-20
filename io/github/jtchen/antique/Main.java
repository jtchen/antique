/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

import java.awt.*;
import java.io.*;
import java.util.*;

/*
 *	This class handles the command line user interface and serves as the entry
 *	point for the entire program.
 */
class Main {

	static final String NAME = "Antique";

	private String mainFontName = "DialogInput";
	private String fallbackFontName = "DialogInput";
	private int fontSize = 18;

	private String encoding = "UTF8"; // old JVMs does not recognize "UTF-8"
	private int tabSize = 4;
	private boolean isLineWrap = false;

	private Vector selectedFileVector = new Vector(); // of File

	private String title
			= "Antique 1.0b1 - a text/code editor based on Java 1.1\n";

	private String usage
			= "Usage: java -jar antique.jar [options] [files]\n";

	private String options
			= "Options:\n"
			+ "  -fs <size>     "
			+ "    Set a font size\n"
			+ "  -fn <name>     "
			+ "    Specify a font name (e.g. Arial or \"Courier New\")\n"
			+ "  -ffn <name>    "
			+ "    Specify a fallback font name\n"
			+ "  -w             "
			+ "    Wrap long lines\n"
			+ "  -t <size>      "
			+ "    Set a tab size (default is 4)\n"
			+ "  -e <encoding>  "
			+ "    Specify a character encoding\n"
			+ "  -h             "
			+ "    Display this information";

	Main(String[] args) {
		for (int i = 0; i < args.length; i += 1) {
			if (args[i].equals("-w")) {
				isLineWrap = true;
			} else if (args[i].equals("-fs") && ((i + 1) < args.length)) {
				int size = ensureInteger(args[i + 1]);
				if (size > 0) {
					fontSize = size;
				} else {
					System.out.println("The specified font size is invalid.");
					// use default font size and continue
				}
				i += 1;
			} else if (args[i].equals("-fn") && ((i + 1) < args.length)) {
				// let the system handle invalid names
				mainFontName = args[i + 1];
				i += 1;
			} else if (args[i].equals("-ffn") && ((i + 1) < args.length)) {
				// let the system handle invalid names
				fallbackFontName = args[i + 1];
				i += 1;
			} else if (args[i].equals("-e") && ((i + 1) < args.length)) {
				Reader reader = null;
				try {
					byte[] bytes = new byte[0];
					reader = new InputStreamReader(
							new ByteArrayInputStream(bytes), args[i + 1]);
				} catch (IOException e) {
					System.out.println("The specified encoding is invalid.");
					// exit because the default encoding may not work
					System.exit(0);
				} finally {
					try {
						if (reader != null) {
							reader.close();
						}
					} catch (IOException e) {}
				}
				encoding = args[i + 1];
				i += 1;
			} else if (args[i].equals("-t") && ((i + 1) < args.length)) {
				int size = ensureInteger(args[i + 1]);
				if (size > 0) {
					tabSize = size;
				} else {
					System.out.println("The specified tab size is invalid.");
					// use default tab size and continue
				}
				i += 1;
			} else if (args[i].equals("-h")) {
				System.out.println(title);
				System.out.println(usage);
				System.out.println(options);
				System.exit(0);
			} else if (! args[i].startsWith("-")) {
				selectedFileVector.addElement(new File(args[i]));
			}
		}

		Font font = new Font(mainFontName, Font.PLAIN, fontSize);
		Font fallbackFont = new Font(fallbackFontName, Font.PLAIN, fontSize);
		AbstractEditor editor = new Editor(font, encoding, tabSize);
		editor.setFallbackFont(fallbackFont);
		editor.setLineWrap(isLineWrap);
		if (selectedFileVector.size() == 0) {
			editor.newFile();
		} else {
			for (int i = 0; i < selectedFileVector.size(); i += 1) {
				File file = (File) selectedFileVector.elementAt(i);
				editor.openFile(file);
			}
		}
	}

	private int ensureInteger(String s) {
		int size = 0;
		try {
			size = Integer.parseInt(s);
		} catch (NumberFormatException e) {}
		return size;
	}

	/*
	 *	This is the actual entrance of the entire program.
	 */
	public static void main(String[] args) {
		new Main(args);
	}

}
