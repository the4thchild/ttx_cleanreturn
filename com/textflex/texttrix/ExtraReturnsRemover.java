/* TextTrix.java    
   Text Trix
   the text tinker
   http://textflex.com/texttrix
   
   Copyright (c) 2002-3, Text Flex
   All rights reserved.
   
   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions 
   are met:
   
   * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
   * Neither the name of the Text Trix nor the names of its
   contributors may be used to endorse or promote products derived
   from this software without specific prior written permission.
   
   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
   IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
   TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
   PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
   OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.  
*/

package com.textflex.texttrix;

import javax.swing.*;
import java.io.*;

public class ExtraReturnsRemover extends PlugIn {//implements PlugIn {

    public ExtraReturnsRemover() {
	super("Extra Returns Remover",
	      "tools",
	      "Removes extra hard returns and indentations",
	      "desc.html",
	      "icon.png",
	      "icon-roll.png");
    }


    public ImageIcon getIcon() {
	return super.getIcon(getIconPath());
    }

    public ImageIcon getRollIcon() {
	return super.getIcon(getRollIconPath());
    }

    public BufferedReader getDetailedDescription() {
	return super.getDetailedDescription(getDetailedDescriptionPath());
    }


    /**Removes extra hard returns.
     * For example, unformatted email arrives with hard returns inserted after 
     * every line; this method strips all but the paragraph, double-spaced
     * hard returns.
     * Text within <code>&#060;pre&#062;</code>
     * and <code>&#060;/pre&#062;</code> tags are
     * left untouched.  Additionally, each line whose first character is a 
     * dash, asterisk, or tab
     * gets its own line.  The line above such lines also gets to remain
     * by itself.  "&#062;" at the start of lines, such as " &#062; &#062; " 
     * from inline email message replies, are also removed.
     * @param s the full text from which to strip extra hard returns
     * @return stripped text
     */
    public String run(String s, int start, int end) {
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
	boolean isCurrentLineReply = false; // current line part of message reply
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
	    int singleReturn = s.indexOf("\n", n); // next hard return occurrence
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
		inlineReply = containingSeq(s, afterSingRet, searchChars, 
					    inlineReplySigns);
		// if the reply chars contine another hard return, 
		// find the length
		// of reply chars after it
		if (s.length() > (afterSingRet += inlineReply)
		    && s.charAt(afterSingRet) == '\n') {
		    isDoubleReturn = true;
		    nextInlineReply = 
			containingSeq(s, afterSingRet + 1,
				      searchChars, inlineReplySigns);
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
		    isNumber = ((potentialListPos != afterInlineReply) 
				&& listDelimiters
				.indexOf(s.charAt(potentialListPos)) != -1) 
			? true : false;
		    isLetterList = 
			(Character.isLetter(s.charAt(afterInlineReply)) 
			 && (potentialListPos = afterInlineReply + 1) 
			 < s.length()
			 && listDelimiters
			 .indexOf(s.charAt(potentialListPos)) != -1) 
			? true : false;
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
		// Skips final "--------" for inline replies if no singleReturn after
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
		stripped.append(s.substring(n, singleReturn)
				+ "\n\n----Original Message----\n\n");
		n = (isDoubleReturn) 
		    ? (singleReturn + inlineReply + 2 + nextInlineReply)
		    : (singleReturn + inlineReply + 1);
		// mark that end of inline message reply
	    } else if (isCurrentLineReply && !isNextLineReply) {
		// dashed start, so own line
		stripped.append(s.substring(n, singleReturn)
				+ "\n------------------------\n\n"); 
		n = (isDoubleReturn) 
		    ? (singleReturn + inlineReply + 2 + nextInlineReply)
		    : (singleReturn + inlineReply + 1);
		// preserve double returns
	    } else if (isDoubleReturn) {
		stripped.append(s.substring(n, singleReturn) + "\n\n");
		// skip over processed rets
		n = singleReturn + inlineReply + 2 + nextInlineReply; 
		// preserve separate lines for lines starting w/
		// dashes, asterisks, numbers, or tabs, as in lists
	    } else if (isDash || isAsterisk || isNumber || isLetterList 
		       || isTab) {
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
	return stripped.toString() + s.substring(n);
    }

    public String run(String s) {
	return run(s, 0, s.length());
    }


    /**Finds the first continuous string consisting of any of a given
     * set of chars and returns the sequence's length if it contains any of 
     * another given set of chars.
     * @param seq string to search
     * @param start <code>seq</code>'s index at which to start searching
     * @param chars chars for which to search in <code>seq</code>
     * @param innerChars required chars to return the length of the first
     * continuous string of chars from <code>chars</code>; if no
     * <code>innerChars</code> are found, returns 0
     */
    public int containingSeq(String seq, int start, 
				    String chars, String innerChars) {
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
