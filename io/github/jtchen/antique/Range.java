/*
 *	This file is part of Antique. It is distributed WITHOUT ANY WARRANTY.
 *	Details can be found on <https://github.com/jtchen/antique>.
 */

package io.github.jtchen.antique;

/*
 *	This class represents a multi-line text range.
 */
class Range {

	int beginRow = 0;
	int beginColumn = 0;
	int endRow = 0;
	int endColumn = 0;

	private boolean isReversed = false;

	Range() {}

	private Range(int beginRow, int beginColumn, int endRow, int endColumn) {
		setBegin(beginRow, beginColumn);
		setEnd(endRow, endColumn);
	}

	synchronized void setBegin(int row, int col) {
		beginRow = row;
		beginColumn = col;
	}

	synchronized void setEnd(int row, int col) {
		if ((row < beginRow) || ((row == beginRow) && (col < beginColumn))) {
			isReversed = true;
		} else {
			isReversed = false;
		}
		endRow = row;
		endColumn = col;
	}

	Range getForwardRange() {
		if (isReversed) {
			return new Range(endRow, endColumn, beginRow, beginColumn);
		}
		return this;
	}

}
