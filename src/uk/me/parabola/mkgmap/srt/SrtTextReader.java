/*
 * Copyright (C) 2010.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.srt;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import uk.me.parabola.imgfmt.app.srt.SRTFile;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read in a sort file from a text format.
 *
 * The file is in utf-8, regardless of the target codepage.
 *
 * The file should start with a codepage declaration, which determines the
 * target codepage for the sort.  This can be followed by a description which is
 * used if this a SRT file is created.
 *
 * The characters are listed in order arranged in a way that shows the strength of the
 * difference between the characters. These are:
 *
 * Primary difference - different letters (eg a and b)
 * Secondary difference - different accents (eg a and a-acute)
 * Tertiary difference - different case (eg a and A)
 *
 * Primary differences are represented by an new 'code' line, or alternatively by the less-than separator.
 * Secondary differences are represented by the semi-colon separator.
 * Tertiary differences are represented by the comma separator.
 *
 * Characters are represented by a two digit hex number that is the code point in the target code page. Alternatively
 * you can write the characters as themselves in <emphasis>unicode (utf-8)</emphasis> (the whole file must be in utf-8).
 *
 * Example
 * <pre>
 * # This is a comment
 * codepage 1252
 * description "Example sort"
 * code a, A; â Â
 * code b, B
 * # Last two lines could be written:
 * # code a, A; â, Â < b, B
 * </pre>
 *
 * NOTE: as we always use upper case in an img file, the upper-lower case sorting differences are untested
 * and based on guess work. In particular you might expect that upper-case sorts before lowercase, but we
 * have the opposite.
 *
 * @author Steve Ratcliffe
 */
public class SrtTextReader {

	// States
	private static final int IN_INITIAL = 0;
	private static final int IN_CODE = 1;
	private static final int IN_TAB2 = 2;

	private int codepage;

	// Data that is read in, the output of the reading operation
	private final Sort sort = new Sort();

	private CharsetEncoder encoder;
	private CharsetDecoder decoder;

	// Used during parsing.
	private int pos1;
	private int pos2;
	private int pos3;
	private int state;
	private String cflags = "";

	public SrtTextReader(String filename) throws IOException {
		this(filename, new InputStreamReader(new FileInputStream(filename), "utf-8"));
	}

	public SrtTextReader(Reader r) throws IOException {
		this("stream", r);
	}

	public SrtTextReader(String filename, Reader r) throws IOException {
		read(filename, r);
	}

	/**
	 * Read in a file and save the information in a form that can be used
	 * to compare strings.
	 * @param filename The name of the file, used for display purposes. It need
	 * not refer to a file that actually exists.
	 * @param r The opened file or other readable source.
	 * @throws IOException If the file cannot be read.
	 * @throws SyntaxException If the format of the file is incorrect.
	 */
	public void read(String filename, Reader r) throws IOException {
		TokenScanner scanner = new TokenScanner(filename, r);
		resetPos();
		state = IN_INITIAL;
		while (!scanner.isEndOfFile()) {
			Token tok = scanner.nextRawToken();

			// We deal with whole line comments here
			if (tok.isValue("#")) {
				scanner.skipLine();
				continue;
			}

			switch (state) {
			case IN_INITIAL:
				initialState(scanner, tok);
				break;
			case IN_CODE:
				codeState(scanner, tok);
				break;
			case IN_TAB2:
				tab2State(scanner, tok);
				break;
			}
		}
	}

	/**
	 * The initial state, looking for a variable to set or a command to change
	 * the state.
	 * @param scanner The scanner for more tokens.
	 * @param tok The first token to process.
	 */
	private void initialState(TokenScanner scanner, Token tok) {
		String val = tok.getValue();
		TokType type = tok.getType();
		if (type == TokType.TEXT) {
			if (val.equals("codepage")) {
				codepage = scanner.nextInt();
				sort.setCodepage(codepage);
				Charset charset = Charset.forName("cp" + codepage);
				encoder = charset.newEncoder();
				decoder = charset.newDecoder();
			} else if (val.equals("description")) {
				sort.setDescription(scanner.nextWord());
			} else if (val.equals("code")) {
				if (codepage == 0)
					throw new SyntaxException(scanner, "Missing codepage declaration before code");
				state = IN_CODE;
				scanner.skipSpace();
			} else if (val.equals("tab2")) {
				if (codepage == 0)
					throw new SyntaxException(scanner, "Missing codepage declaration before code");
				state = IN_TAB2;
			}
		}
	}

	/**
	 * Inside a code block that describes a set of characters that all sort
	 * at the same major position.
	 * @param scanner The scanner for more tokens.
	 * @param tok The current token to process.
	 */
	private void codeState(TokenScanner scanner, Token tok) {
		String val = tok.getValue();
		TokType type = tok.getType();
		if (type == TokType.TEXT) {
			if (val.equals("flags")) {
				scanner.validateNext("=");
				cflags = scanner.nextWord();
				// TODO not yet
			} else if (val.equals("pos")) {
				scanner.validateNext("=");
				try {
					int newPos = Integer.decode(scanner.nextWord());
					if (newPos < pos1)
						throw new SyntaxException(scanner, "cannot set primary position backwards, was " + pos1);
					pos1 = newPos;
				} catch (NumberFormatException e) {
					throw new SyntaxException(scanner, "invalid integer for position");
				}
			} else if (val.equals("pos2")) {
				scanner.validateNext("=");
				pos2 = Integer.decode(scanner.nextWord());
			} else if (val.equals("pos3")) {
				scanner.validateNext("=");
				pos3 = Integer.decode(scanner.nextWord());
			} else if (val.length() == 1) {
				addCharacter(scanner, val);
			} else if (val.length() == 2) {
				byte bval = (byte) Integer.parseInt(val, 16);
				ByteBuffer bin = ByteBuffer.allocate(1);
				bin.put(bval);
				bin.flip();
				try {
					decoder.onMalformedInput(CodingErrorAction.REPORT);
					CharBuffer out = decoder.decode(bin);
					setSortcode(bval, out.get());
				} catch (CharacterCodingException e) {
					throw new SyntaxException(scanner, "Character not valid in codepage " + codepage);
				}
			} else {
				throw new SyntaxException(scanner, "Unexpected word " + val);
			}
		} else if (type == TokType.SYMBOL) {
			if (val.equals(",")) {
				pos3++;
			} else if (val.equals(";")) {
				pos3 = 1;
				pos2++;
			} else if (val.equals("<")) {
				advancePos();
			} else {
				addCharacter(scanner, val);
			}

		} else if (type == TokType.EOL) {
			state = 0;
			advancePos();
		}
	}

	/**
	 * Unknown section. Two byte records.
	 * You usually need to be able to say things like o-umlaut sorts as if it were o followed by e
	 * so perhaps this section is used for that.
	 */
	private void tab2State(TokenScanner scanner, Token tok) {
		TokType type = tok.getType();
		if (type == TokType.TEXT) {
			String val = tok.getValue();
			char tab2 = (char) Integer.parseInt(val, 16);
			sort.add(tab2);
			scanner.skipLine();
			state = IN_INITIAL;
		} else if (type == TokType.EOL) {
			state = IN_INITIAL;
		}
	}

	/**
	 * Add a character to the sort table.
	 * @param scanner Input scanner, for line number information.
	 * @param val A single character string containing the character to be added.
	 */
	private void addCharacter(TokenScanner scanner, String val) {
		CharBuffer cbuf = CharBuffer.wrap(val.toCharArray());
		try {
			ByteBuffer out = encoder.encode(cbuf);
			if (out.remaining() > 1)
				throw new SyntaxException(scanner, "more than one character resulter from conversion of " + val);
			byte b = out.get();
			char cval = val.charAt(0);
			setSortcode(b, cval);
		} catch (CharacterCodingException e) {
			throw new SyntaxException(scanner, "Invalid character in the target charset " + val);
		}
	}

	/**
	 * Set the sort code for the given 8-bit character.
	 * @param b The 8-bit character in the character set of the codepage.
	 * @param cval The same character in unicode.
	 */
	private void setSortcode(byte b, char cval) {
		int flags = 0;
		if (Character.isLetter(cval) && (Character.getType(cval) & Character.MODIFIER_LETTER) == 0)
			flags = 1;
		if (Character.isDigit(cval))
			flags = 2;
		if (cflags.contains("0"))
			flags = 0;
		if (cflags.contains("g"))
			flags |= 0x10;
		if (cflags.contains("w"))
			flags |= 0x20;

		sort.add(b, pos1, pos2, pos3, flags);
		this.cflags = "";
	}

	/**
	 * Reset the position fields to their initial values.
	 */
	private void resetPos() {
		pos1 = 0;
		pos2 = 1;
		pos3 = 1;
	}

	/**
	 * Advance the major position value, resetting the minor position variables.
	 */
	private void advancePos() {
		pos1 += pos2;
		pos2 = 1;
		pos3 = 1;
	}

	public Sort getSort() {
		return sort;
	}

	/**
	 * Read in a sort description text file and create a SRT from it.
	 * @param args First arg is the text input file, the second is the name of the output file. The defaults are
	 * in.txt and out.srt.
	 */
	public static void main(String[] args) throws IOException {
		String infile = "in.txt";
		if (args.length > 0)
			infile = args[0];

		String outfile = "out.srt";
		if (args.length > 1)
			outfile = args[1];
		ImgChannel chan = new FileImgChannel(outfile);
		SRTFile sf = new SRTFile(chan);

		SrtTextReader tr = new SrtTextReader(infile);
		Sort sort1 = tr.getSort();
		sf.setSort(sort1);
		sf.setDescription(sort1.getDescription());
		sf.write();
		sf.close();
		chan.close();
	}
}
