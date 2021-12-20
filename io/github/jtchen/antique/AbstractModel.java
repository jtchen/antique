/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

/*
 *	This class defines the model and serves as an interface to other classes.
 *	Currently, the model is implemented as a passive model and should not have
 *	any knowledge of the view or controller.
 */
abstract class AbstractModel {

	abstract void cloneFrom(AbstractModel model);

	abstract int getCharCount();
	abstract String getStringByRange(Range range);

	abstract char[] getLine(int row);
	abstract int getLineCount();
	abstract int getCaretRow();
	abstract int getCaretColumn();
	abstract void setCaret(int[] pos);
	abstract void setCaret(int row, int col);

	/*
	 *	This is the principal method to support syntax highlighting.
	 */
	abstract byte[] getColorCodes(int row);

	abstract boolean isSelected();
	abstract Range getSelection();
	abstract void setSelectionBegin(int row, int col);
	abstract void setSelectionEnd(int row, int col);
	abstract void clearSelection();

	/*
	 *	Regardless of the current position of the caret, this method only
	 *	counts the occurrences of matches in the entire document.
	 */
	abstract int countMatch(AbstractEditor.MatchConfig matchConfig);
	abstract boolean isCaretAtMatchEnd();
	abstract void moveCaretToNextMatch();
	abstract void disableMatch();

	abstract void insert(char c);
	abstract void backSpace();
	abstract void insertString(String s);
	abstract void deleteStringByRange(Range range);

	/*
	 *	The constructor of Integer(int) has been deprecated in recent JDKs, and
	 *	the Integer.valueOf(int) method is relatively new (since Java 1.5).
	 *	Therefore, this class can be used as an alternative to using the
	 *	original Vector with Integer objects for efficiency and compatibility.
	 */
	static class IntVector {

		private int[] array;
		private int count = 0;

		IntVector() {
			array = new int[10]; // the same as Vector
		}

		int size() {
			return count;
		}

		synchronized void addElement(int i) {
			if ((count + 1) > array.length) {
				int[] oldArray = array;
				array = new int[array.length * 2];
				System.arraycopy(oldArray, 0, array, 0, count);
			}
			array[count] = i;
			count += 1;
		}

		int elementAt(int index) {
			if (index < count) {
				return array[index];
			}
			throw new ArrayIndexOutOfBoundsException();
		}

		void removeAllElements() {
			count = 0;
		}

		synchronized void setSize(int newSize) {
			if (newSize < 0) {
				throw new ArrayIndexOutOfBoundsException();
			}

			if (newSize > count) {
				int[] oldArray = array;
				array = new int[newSize];
				System.arraycopy(oldArray, 0, array, 0, count);
			}
			count = newSize;
		}

	}

}
