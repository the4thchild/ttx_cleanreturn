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
 * Portions created by the Initial Developer are Copyright (C) 2003
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
public class ExtraReturnsRemover extends PlugIn { //implements PlugIn {

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

	/** Removes the extra hard returns.
	Also removes ">" and similar email symbols at the start of lines.
	Preserves lists.
	@param s the full text from which to strip extra hard returns
	@param start index in <code>s</code> at which to start manipulation
	@param end index in <code>s</code> at which to no longer manipulate
	@return stripped text
	 */
	public PlugInOutcome run(String s, int start, int end) {
		/* This function works by generally checking the characters afer
		 * a hard return to determine whether to keep it or not.
		 * To strip inline message reply characters, the function must also
		 * check the beginning of the string separately.  Additionally, the
		 * function completely excludes pre-tag-delimited areas from hard
		 * return removal.
		 */
		int len = s.length();
		StringBuffer stripped = new StringBuffer(len); // new string
		int n = start; // string index
		String searchChars = " >"; // inline message reply chars
		String inlineReplySigns = ">"; // inline message indicators
		boolean isCurrentLineReply = false; // current line part of msg reply
		boolean isNextLineReply = false; // next line part of message reply
		boolean ignorePre = false; // ignore <pre>'s within inline replies

		// append text preceding the selection
		stripped.append(s.substring(0, n));
		// check for inline reply symbols at start of string
		n = containingSeq(s, n, searchChars, inlineReplySigns);
		if (s.indexOf("<pre>") == 0 || n == start) {
			isCurrentLineReply = false;
		} else {
			isCurrentLineReply = true;
			stripped.append("----Original Message----\n\n"); // mark replies
			ignorePre = true;
		}

		while (n < end) {
			int inlineReply = 0; // eg ">" or "<" from inline email msg replies
			int nextInlineReply = 0; // inline replies on next line
			int singleReturn = s.indexOf("\n", n); // next hard return
			boolean isDoubleReturn = false; // double hard return flag
			boolean isDash = false; // dash flag
			boolean isAsterisk = false; // asterisk flag
			boolean isNumber = false; // number flag
			boolean isLetterList = false; // lettered list flag
			boolean isTab = false; // tab flag
			int startPre = s.indexOf("<pre>", n); // next opening pre tag
			int endPre = s.indexOf("</pre>", n); // next cloisng pre tag

			// check the character after a hard return
			if (singleReturn != -1) {
				int afterSingRet = singleReturn + 1;
				// get the length of chars inline msg reply chars after 
				// the return
				inlineReply =
					containingSeq(
						s,
						afterSingRet,
						searchChars,
						inlineReplySigns);
				// if the reply chars contine another hard return, 
				// find the length
				// of reply chars after it
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
				// check whether the character after a return is a 
				// tab, dash, asterisk, or number
				int afterInlineReply = singleReturn + inlineReply + 1;
				if (afterInlineReply < s.length()) {
					isTab = s.startsWith("\t", afterInlineReply);
					isDash = s.startsWith("-", afterInlineReply);
					isAsterisk = s.startsWith("*", afterInlineReply);
					String listDelimiters = ").";
					String numbers = "1234567890";
					int potentialListPos = 0;
					for (potentialListPos = afterInlineReply;
						potentialListPos < s.length()
							&& Character.isDigit(s.charAt(potentialListPos));
						potentialListPos++);
					isNumber =
						((potentialListPos != afterInlineReply)
							&& listDelimiters.indexOf(s.charAt(potentialListPos))
								!= -1)
							? true
							: false;
					isLetterList =
						(Character.isLetter(s.charAt(afterInlineReply))
							&& (potentialListPos = afterInlineReply + 1)
								< s.length()
							&& listDelimiters.indexOf(s.charAt(potentialListPos))
								!= -1)
							? true
							: false;
				}
			}
			isNextLineReply =
				(inlineReply != 0 || nextInlineReply != 0) ? true : false;

			/* Append the chars to keep while removing single returns
			 * and their inline msg reply chars appropriately.
			 */
			// skip <pre>-delimited sections, removing only the <pre> tags
			// The <pre> tags should each be at the start of its own line.
			if (startPre == n && !ignorePre) {
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
				// add the rest of the text if no more single returns exist.
				// Also catches null strings
				// Skips final "--------" for inline replies w/ no 
				// later singleReturn
			} else if (singleReturn == -1) {
				stripped.append(s.substring(n, end));
				/* to add final dashed line after reply, even when no final
				 * return, uncomment these lines
				 if (isCurrentReply)
				 stripped.append("\n-----------------------\n\n");
				*/
				n = end;
				// mark that start of inline message reply
			} else if (!isCurrentLineReply && isNextLineReply) {
				stripped.append(
					s.substring(n, singleReturn)
						+ "\n\n----Original Message----\n\n");
				n =
					(isDoubleReturn)
						? (singleReturn + inlineReply + 2 + nextInlineReply)
						: (singleReturn + inlineReply + 1);
				// mark that end of inline message reply
			} else if (isCurrentLineReply && !isNextLineReply) {
				// dashed start, so own line
				stripped.append(
					s.substring(n, singleReturn)
						+ "\n------------------------\n\n");
				n =
					(isDoubleReturn)
						? (singleReturn + inlineReply + 2 + nextInlineReply)
						: (singleReturn + inlineReply + 1);
				// preserve double returns
			} else if (isDoubleReturn) {
				stripped.append(s.substring(n, singleReturn) + "\n\n");
				// skip over processed rets
				n = singleReturn + inlineReply + 2 + nextInlineReply;
				// preserve separate lines for lines starting w/
				// dashes, asterisks, numbers, or tabs, as in lists
			} else if (
				isDash || isAsterisk || isNumber || isLetterList || isTab) {
				// + 2 to pick up the dash
				stripped.append(s.substring(n, singleReturn + 1));
				n = singleReturn + inlineReply + 1;
				// join the tail-end of the text
			} else {
				// don't add space if single return is at beginning of line
				// or a space exists right before the single return.
				if (singleReturn == n || s.charAt(singleReturn - 1) == ' ') {
					stripped.append(s.substring(n, singleReturn));
					// add space if none exists right before the single return
				} else {
					stripped.append(s.substring(n, singleReturn) + " ");
				}
				n = singleReturn + inlineReply + 1;
			}
			// flag whether the current line is part of a msg reply
			isCurrentLineReply = isNextLineReply;
			// flag to ignore <pre> tags if in inline message reply
			ignorePre =
				(inlineReply != 0 || nextInlineReply != 0) ? true : false;
		}
		/* n should have never exceeded len
		   String finalText = stripped.toString();
		   if (n < len)
		   finalText += s.substring(n);
		   return finalText;
		*/
		//	System.out.println(stripped.toString() + s.substring(n));
		return new PlugInOutcome(stripped.toString() + s.substring(n));
	}

	/** Front-end for the extra returns remover; assumes that the remover 
	should tinker with entire text should be tinkered with.
	@param s the full text from which to strip extra hard returns
	@return stripped text
	*/
	public PlugInOutcome run(String s, int caretPosition) {
		return run(s, 0, s.length());
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

}
