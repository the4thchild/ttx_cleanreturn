/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is the Text Trix code.
 *
 * The Initial Developer of the Original Code is
 * Text Flex.
 * Portions created by the Initial Developer are Copyright (C) 2003-4
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s): David Young <dvd@textflex.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.textflex.texttrix;

import javax.swing.*;
import java.io.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/** Removes extra hard returns.
    For example, unformatted email arrives with hard returns inserted after 
    every line; this method strips all but the paragraph, double-spaced
    hard returns.
    Text within <code>&#060;pre&#062;</code>
    and <code>&#060;/pre&#062;</code> tags are
    left untouched.  Additionally, each line whose first character is a 
    dash, asterisk, or tab
    gets its own line.  The line above such lines also gets to remain
    by itself.  "&#062;" at the start of lines, such as " &#062; &#062; " 
    from inline email message replies, are also removed.
*/
public class ExtraReturnsRemover extends PlugInWindow { //implements PlugIn {

	private ExtraReturnsRemoverDialog diag = null;
	private String lists = "";
	private int threshold = 0;
	private boolean emailMarkers = false;
	private boolean selectedRegion = false;

	/** Constructs the extra returns remover with descriptive text and 
	images.
	*/
	public ExtraReturnsRemover() {
		super(
			"Extra Returns Remover",
			"tools",
			"Removes extra hard returns and indentations",
			"desc.html",
			"icon.png",
			"icon-roll.png");
		setAlwaysEntireText(true); // retrieve the entire body of text

		// Runs the plug-in if the user hits "Enter" in components with this adapter
		KeyAdapter removerEnter = new KeyAdapter() {
			public void keyPressed(KeyEvent evt) {
				if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
					runPlugIn();
				}
			}
		};


		// Runs the plug-in if the user hits the "Extra Returns Remover"
		// button;
		// creates a shortcut key (alt-E) as an alternative way to invoke
		// the button
		Action extraReturnsRemoverAction = 
			new AbstractAction("Extra Returns Remover", null) {
			public void actionPerformed(ActionEvent e) {
				applyUserOptions();
				runPlugIn();
			}
		};
		LibTTx.setAcceleratedAction(
			extraReturnsRemoverAction,
			"Extra Returns Remover",
			'E',
			KeyStroke.getKeyStroke("alt E"));

		// Creates the options dialog window
		diag =
			new ExtraReturnsRemoverDialog(
				removerEnter,
				extraReturnsRemoverAction);
		setWindow(diag);
	}
	
	public void applyUserOptions() {
		lists = diag.getLists();
		threshold = diag.getThreshold();
		emailMarkers = diag.getEmailMarkers();
		selectedRegion = diag.getSelectedRegion();
	}

	/** Gets the normal icon.
	@return normal icon
	*/
	public ImageIcon getIcon() {
		return getIcon(getIconPath());
	}

	/** Gets the rollover icon.
	@return rollover icon
	*/
	public ImageIcon getRollIcon() {
		return getRollIcon(getRollIconPath());
	}

	/** Gets the detailed, HTML-formatted description.
	For display as a tool tip.
	@return a buffered reader for the description file
	*/
	public BufferedReader getDetailedDescription() {
		return super.getDetailedDescription(getDetailedDescriptionPath());
	}
	
	public PlugInOutcome run(String s) {
		return run(s, 0, 0);
	}
	
	/**Runs the extra return remover on the text, following the options
	 * set in the dialogue window.
	 * Assumes that these options have been recorded in the class
	 * fields.
	 * @param s the string to remove extraneous returns from
	 * @param x the starting index of any selected region, taken as the 
	 * starting position to work on, but ignored
	 * if the "selectedArea" option is unchecked
	 * @param y the final index of any selected region, taken as the 
	 * ending position, noninclusive, on which to work, but ignored if
	 * the "selectedArea" option is unchecked
	 * @return the text, clean, washed, and ready
	*/
	public PlugInOutcome run(String s, int x, int y) {
		/* This function works by generally checking the characters afer
		 * a hard return to determine whether to keep it or not.
		 * To strip inline message reply characters, the function must also
		 * check the beginning of the string separately.  Additionally, the
		 * function completely excludes "<pre>"-tag-delimited areas from hard
		 * return removal.
		 */
		
		/* Indices */
		int n = x; // string index
		int end = y;//len;
		// resets the indices to work on the entire text if the selectedegion
		// option is unchecked
		if (!selectedRegion) {
			n = 0;
			end = s.length();
		}
		
		/* Flags, storage, and symbols */
		StringBuffer stripped = new StringBuffer(end - n); // new string
		String searchChars = " >"; // inline message reply chars
		String inlineReplySigns = ">"; // inline message indicators
		boolean isCurrentLineReply = false; // current line part of msg reply
		boolean isNextLineReply = false; // next line part of message reply
		boolean ignorePre = false; // ignore <pre>'s within inline replies
		// inline reply symbols (eg "<" or ">") in next line, right after singleReturn
		int inlineReply = 0;
		// inline replies on next next line, important for double returns embedded
		// in otherwise continuous reply symbols
		int nextInlineReply = 0;
		int singleReturn = s.indexOf("\n", n); // next hard return
		boolean isDoubleReturn = false; // double hard return flag
		boolean isList = false; // current line part of a list flag
		int startPre = 0; // next opening pre tag
		int endPre = 0; // next cloisng pre tag
		int lineStart = LibTTx.reverseIndexOf(s, "\n", n - 1) + 1;
		int returnsRemoved = 0;
		
		// List markers
		
		// breaks user's comma-separated list of list markers
		StringTokenizer listTok = new StringTokenizer(lists, ",");
		int listCount = listTok.countTokens();
		// array of list markers
		ListLookup[] listDelims = new ListLookup[listCount];
		for (int i = 0; i < listCount; i++) {
			listDelims[i] = new ListLookup(listTok.nextToken());
		}
		String outlineChars = "1234567890ivxlcdm";
		String emailMarkerStart = "----Original Message----\n\n";
		String emailMarkerEnd = "\n-----------------------";
		if (!emailMarkers) {
			emailMarkerStart = "";
			emailMarkerEnd = "";
		}
		
		/* Initiate the remover */
		// Lines in the main loop are assumed to have already been stripped
		// of any email reply symbols and flagged about whether the previous
		// line had any of the symbols.  Prior to this loop, the first line must
		// be checked and flagged for these symbols.
		
		// append text preceding the selection
		stripped.append(s.substring(0, n));
		// check for inline reply symbols at start of string
		inlineReply = containingSeq(s, n, searchChars, inlineReplySigns);
		if (s.indexOf("<pre>", n) != n && inlineReply != 0) {
			isCurrentLineReply = true;
			// mark reply region as "Original Message", but only if at start
			// of message to prevent splitting reply region if text highlighted
			// in middle of such a region
			if (n == 0) stripped.append(emailMarkerStart);
			n += inlineReply;
		}
		
		/* Cycle through the lines from the first to the last */
		// assuming that the first line has already been appropriately
		// flagged
		
		while (n < end) {
			inlineReply = 0;
			nextInlineReply = 0;
			singleReturn = s.indexOf("\n", n); // next hard return
			isDoubleReturn = false;
			isList = false;
			startPre = s.indexOf("<pre>", n); // next opening pre tag
			endPre = s.indexOf("</pre>", n); // next cloisng pre tag

			// Check the character after a hard return
			if (singleReturn != -1) {
				int afterSingRet = singleReturn + 1;
				// get the length of email reply chars after the return
				inlineReply =
					containingSeq(
						s,
						afterSingRet,
						searchChars,
						inlineReplySigns);
				// if the reply chars continue after another hard return, 
				// find the length of reply chars after it; necessary to 
				// identify reply symbols surrounding a double return
				if (s.length() > (afterSingRet += inlineReply)
					&& s.charAt(afterSingRet) == '\n') {
					isDoubleReturn = true;
					nextInlineReply =
						containingSeq(
							s,
							afterSingRet + 1,
							searchChars,
							inlineReplySigns);
				}
				
				// Check whether the character after a return is a 
				// tab, dash, asterisk, outline symbol (eg "a)" or "ii."), or
				// other user-defined list marker
				
				// char right after inline reply marker
				int afterInlineReply = singleReturn + inlineReply + 1;
				if (afterInlineReply < s.length()) {
					int listEndPos = -1;
					String marker = ""; // the list marker to check
					// the list symbol that gets incremented, eg the "a" in "a)"
					String outlineIncrementor = "";
					
					// cycles through the user-defined list markers to see if
					// the start of the line matches any of them;
					// stops as soon as finds a match
					for (int i = 0; !isList && i < listDelims.length; i++) {
						marker = listDelims[i].getMarker();
						// only checks the first char of the line if the marker isn't
						// an outline character
						if (!listDelims[i].getOutline()) {
							isList = s.startsWith(marker, afterInlineReply);
							
						} else if ((listEndPos = s.indexOf(marker, afterInlineReply)) > 0) {
							// for outline markers, checks whether each of the 
							// chars preceding the outline closer, eg ")" or ".",
							// matches one of typical outline symbols
							isList = true;
							outlineIncrementor = s.substring(afterInlineReply, listEndPos);
							
							// first checks if in string of typical symbols
							for (int j = 0; isList && j < outlineIncrementor.length(); j++) {
								isList = 
									outlineChars.indexOf(outlineIncrementor.substring(j, j + 1)
										.toLowerCase())
									!= -1;
							}
							
							// if not, checks if a single letter or multiple of the same letter
							if (!isList) {
								isList = true;
								String firstChar = outlineIncrementor.substring(0, 1)
									.toLowerCase();
								for (int j = 1; isList && j < outlineIncrementor.length(); j++) {
									isList = 
										outlineIncrementor.substring(j, j + 1).toLowerCase()
											.equals(firstChar);
								}
							}
						}
					}
				}
			}
			isNextLineReply = inlineReply != 0 || nextInlineReply != 0;//

			/* Append the chars to keep while removing single returns
			 * and their inline msg reply chars appropriately.
			 */
			if (startPre == n && !ignorePre) {
				// Skip <pre>-delimited sections, removing only the <pre> tags
				// The <pre> tags should each be at the start of its own line.
				// go to the end of the "pre" section
				if (endPre != -1) {
					stripped.append(s.substring(n + 6, endPre));
					n = endPre + 7;
					// if user forgets closing "pre" tag, goes to end
				} else if (n + 6 < end) {
					stripped.append(s.substring(n + 6, end));
					n = end;
				} else {
					n = end;
				}
				
			} else if (singleReturn == -1) {
				// Add the rest of the text if no more single returns exist.
				// Also catches null strings and skips final "--------" for inline 
				// replies w/ no later singleReturn
				//System.out.println("n: " + n + ", end: " + end + ", char b4 end: " + s.charAt(end - 1));
				stripped.append(s.substring(n, end));
				/* to add final dashed line after reply, even when no final
				 * return, uncomment these lines
				 if (isCurrentReply)
					stripped.append(emailMarkerEnd);
				*/
				n = end;
				
			} else if (singleReturn - lineStart < threshold) {
				// Preserves lines that have below the threshold level of characters.
				// Eg if a line has 4 characters, including reply markers, with the 
				// threshold set to 5, the line will stay the same.  If the line has reply
				// markers, however, they will be removed, under the assumption that
				// wants to preserve the formatting while still deleting extraneous chars.
				//System.out.println("s: " + s.substring(n, singleReturn + 1) + ", singleReturn: " + singleReturn + ", lineStart: " + lineStart);
				
				stripped.append(s.substring(n, singleReturn + 1));
				n = singleReturn + inlineReply + 1;
				
			} else if (!isCurrentLineReply && isNextLineReply) {
				// Check for marks that at the start of an inline message reply
				stripped.append(
					s.substring(n, singleReturn)
						+ "\n\n" + emailMarkerStart);
				// Skip the 2nd return in double returns, including any surrounding
				// email reply symbols
				n =
					(isDoubleReturn)
						? (singleReturn + inlineReply + 2 + nextInlineReply)
						: (singleReturn + inlineReply + 1);
						
			} else if (isCurrentLineReply && !isNextLineReply) {
				// Check for marks that at the end of an inline message reply
				stripped.append(
					s.substring(n, singleReturn)
						+ emailMarkerEnd + "\n\n");
				// Skip the 2nd return in double returns, including any surrounding
				// email reply symbols
				n =
					(isDoubleReturn)
						? (singleReturn + inlineReply + 2 + nextInlineReply)
						: (singleReturn + inlineReply + 1);
						
			} else if (isDoubleReturn) {
				// Preserve double returns
				stripped.append(s.substring(n, singleReturn) + "\n\n");
				// skip over processed rets
				n = singleReturn + inlineReply + 2 + nextInlineReply;
				
			} else if (isList) {
				// Preserve separate lines for lines starting w/
				// list markers
				stripped.append(s.substring(n, singleReturn + 1));
				n = singleReturn + inlineReply + 1;
				
			} else {
				// Join the tail-end of the text.
				// don't add space if single return is at beginning of line
				// or a space exists right before the single return.
				if (singleReturn == n || s.charAt(singleReturn - 1) == ' ') {
					stripped.append(s.substring(n, singleReturn));
					// add space if none exists right before the single return
				} else {
					stripped.append(s.substring(n, singleReturn) + " ");
				}
				returnsRemoved++;
				n = singleReturn + inlineReply + 1;
			}
			// flag whether the current line is part of a msg reply
			isCurrentLineReply = isNextLineReply;
			// marks the start of the next line, including any reply symbols;
			// checks for double return b/c, if so, the next line is skipped;
			// lineStart then has to refer to the next next line
			lineStart = (isDoubleReturn) ? singleReturn + 2 : singleReturn + 1;
			// flag to ignore <pre> tags if in inline message reply
			ignorePre = inlineReply != 0 || nextInlineReply != 0;//) ? true : false;
		}
		
		// Create the new string and display the results, both in the TextPad
		// and summarized in the plug-in window
		String strippedStr = stripped.toString() + s.substring(n); // the final product
		/*
		int fewerChars = s.length() - strippedStr.length(); // change in length
		// explanation of smaller, possibly negative changes in length
		String emailMarkerExp = (emailMarkers) 
			? " (minus a bunch of mail markers)" : "";
		*/
		// the result templates...
		String[] results = {
			"Unloaded " + returnsRemoved + " useless hard returns",
			"Welcome to text lite!  " + returnsRemoved
				+ " hard returns removed",
			returnsRemoved + " extraneous hard returns...gone",
			"Nice and slick with " + returnsRemoved
				+ " fewer hard returns"
		};
		// ...chosen randomly
		displayResults(results, 4);
		//	System.out.println(stripped.toString() + s.substring(n));
		return new PlugInOutcome(strippedStr);
	}
	
	/**Storage class for list markers.
	 * Contains the marker as well as a flag for whether the marker
	 * is associated with an outline symbol, where "[outline]" flags
	 * such symbols.  For example, "-" or "*" are not outline
	 * symbols, whereas "IV" or "ix" or "a" could serve as these
	 * symbols.  If the user submits the marker, "[outline])", ")' is
	 * considered the marker, while "[outline]" flags the outline
	 * field.
	*/
	private class ListLookup {
		private String marker = ""; // list marker
		private boolean outline = false; // flags outline marker
		// user generic symbol for outline incrementors, eg 
		// "IV","A", or "3"
		private String outlineStr = "[outline]";
		// length of the generic outline symbol
		private int outlineStrLen = outlineStr.length();
		
		/**Creates a marker storage object.
		 * Checks for outline symbols.
		 * @param aMarker the marker to store; pared down
		 * to the symbols following the generic "[outline]"
		 * designation if appropriate
		*/
		public ListLookup(String aMarker) {
			marker = aMarker;
			// pares down and flags outline markers
			if (aMarker.toLowerCase().startsWith(outlineStr)) {
				outline = true;
				marker = aMarker.substring(outlineStrLen);
			}
		}
		
		/**Gets the marker.
		 * @return the marker
		*/
		public String getMarker() { return marker; }
		/**Gets the outline flag.
		 * @return outline flag, <code>true</code> if the originally set
		 * marker started with "[outline]"
		*/
		public boolean getOutline() { return outline; }
	}

	/** Finds the first continuous string consisting of any of a given
	set of chars and returns the sequence's length if it contains any of 
	another given set of chars.
	@param seq string to search
	@param start <code>seq</code>'s index at which to start searching
	@param chars chars for which to search in <code>seq</code>
	@param innerChars required chars to return the length of the first
	continuous string of chars from <code>chars</code>; if no
	<code>innerChars</code> are found, returns 0
	 */
	public int containingSeq(
		String seq,
		int start,
		String chars,
		String innerChars) {
		char nextChar;
		boolean inSeq = false;
		int i = start;
		while (seq.length() > i
			&& chars.indexOf(nextChar = seq.charAt(i)) != -1) {
			i++;
			if (innerChars.indexOf(nextChar) != -1) {
				inSeq = true; // set flag that found a char from innerChar
			}
		}
		return (inSeq) ? i - start : 0;
	}

	private void displayResults(String[] results, int weightFront) {
		int n = (int) (results.length * Math.pow(Math.random(), weightFront));
		diag.setResultsLbl(results[n]);
	}
	
}

/** Find and replace dialog.
    Creates a dialog box accepting input for search and replacement 
    expressions as well as options to tailor the search.
*/
class ExtraReturnsRemoverDialog extends JPanel {//JFrame {
	JLabel tips = null; // offers tips on using the plug-in 
	JLabel listsLbl = null; // label for the search field
	JTextField listsFld = null; // search expression input
	JLabel thresholdLbl = null; // label for the replacement field
	SpinnerNumberModel thresholdMdl = null;
	JSpinner thresholdSpinner = null; // replacement expression input
	JCheckBox emailMarkersChk = null; // reply boundaries
	JCheckBox selectedRegionChk = null; // only work on selected region
	JLabel resultsTitleLbl = null; // intros the results
	JLabel resultsLbl = null; // shows the results
	JButton removerBtn = null; // label for the search button

	/**Construct a find/replace dialog box
	 * @param owner frame to which the dialog box will be attached; 
	 * can be null
	 */
	public ExtraReturnsRemoverDialog(
		KeyAdapter removerEnter,
		Action extraReturnsRemoverAction) {
		super(new GridBagLayout());
		setSize(350, 200);
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.CENTER;
		String msg = "";

		// Threshold spinner
		
		JLabel thresholdLbl =
			new JLabel("Minimum length of line:");
		String thresholdTipTxt =
			"<html>The minimum number of characters that a line must"
				+ "<br>contain before the Extra Return Remove will"
				+ "<br>check for an extra return.</html>";
		thresholdLbl.setToolTipText(thresholdTipTxt);
		// houses the user-chosen value
		thresholdMdl =
			new SpinnerNumberModel(0, 0, 100, 5);
		thresholdSpinner = new JSpinner(thresholdMdl);

		// tips display; intros the plug-in
		tips = new JLabel("Welcome to ERR, the Extra Returns Remover!");
		LibTTx.addGridBagComponent(
			tips,
			constraints,
			0,
			0,
			2,
			1,
			100,
			0,
			this);//contentPane);

		// user-defined, comma-delimited list of list markers
		listsLbl = new JLabel("List markers:");
		LibTTx.addGridBagComponent(
			listsLbl,
			constraints,
			0,
			1,
			1,
			1,
			100,
			0,
			this);
		listsFld = new JTextField("-,[outline].,[outline]),*", 20);
		LibTTx.addGridBagComponent(
			listsFld,
			constraints,
			1,
			1,
			1,
			1,
			100,
			0,
			this);//contentPane);
		// pressing enter in the input field starts the remover
		listsFld.addKeyListener(removerEnter);
		
		// Threshold placement
		LibTTx.addGridBagComponent(
			thresholdLbl,
			constraints,
			0,
			2,
			1,
			1,
			100,
			0,
			this);
		LibTTx.addGridBagComponent(
			thresholdSpinner,
			constraints,
			1,
			2,
			1,
			1,
			100,
			0,
			this);//contentPane);
		
		// Option to add reply email boundary markers
		emailMarkersChk = new JCheckBox("Mark email reply region boundary");
		LibTTx.addGridBagComponent(
			emailMarkersChk,
			constraints,
			0,
			3,
			2,
			1,
			100,
			0,
			this);//contentPane);
		emailMarkersChk.setMnemonic(KeyEvent.VK_M);
		msg = "Marks the start and end of a region stripped of email reply markers";
		emailMarkersChk.setToolTipText(msg);
		
		// Option to work only within highlighted section
		selectedRegionChk = new JCheckBox("Selected area only");
		LibTTx.addGridBagComponent(
			selectedRegionChk,
			constraints,
			0,
			4,
			2,
			1,
			100,
			0,
			this);//contentPane);
		selectedRegionChk.setMnemonic(KeyEvent.VK_S);
		msg = "Removes extra returns only within the highlighted section";
		selectedRegionChk.setToolTipText(msg);
		
		// Displays the results of the removal
		resultsTitleLbl = new JLabel("Reults: ");
		LibTTx.addGridBagComponent(
			resultsTitleLbl,
			constraints,
			0,
			5,
			1,
			1,
			100,
			0,
			this);//contentPane);
		resultsLbl = new JLabel("");
		resultsLbl.setHorizontalAlignment(JLabel.RIGHT);
		LibTTx.addGridBagComponent(
			resultsLbl,
			constraints,
			1,
			5,
			1,
			1,
			100,
			0,
			this);//contentPane);

		// fires the "Extra Returns Remover" action
		removerBtn = new JButton(extraReturnsRemoverAction);
		LibTTx.addGridBagComponent(
			removerBtn,
			constraints,
			0,
			6,
			2,
			1,
			100,
			0,
			this);//contentPane);
	}
	
	/**Gets the lists.
	 * @return the list of list markers
	*/
	public String getLists() { return listsFld.getText(); }
	/**Gets the threshold.
	 * @return the minimum line length to remove returns from
	*/
	public int getThreshold() { return thresholdMdl.getNumber().intValue(); }
	/**Gets the email markers flag.
	 * @return the flag to add reply email boundary markers
	*/
	public boolean getEmailMarkers() { return emailMarkersChk.isSelected(); }
	/**Gets the selected region flag.
	 * @return flag to only work on the selected region
	*/
	public boolean getSelectedRegion() { return selectedRegionChk.isSelected(); }
	
	public void setResultsLbl(String s) {
		resultsLbl.setText(s);
	}

}