/*
 * Copyright (C) 2011.
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
package uk.me.parabola.imgfmt.app.typ;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Base routines used by points, lines and polygons.
 *
 * @author Steve Ratcliffe
 */
public abstract class TypElement {
	private int type;
	private int subType;
	protected final List<TypLabel> labels = new ArrayList<TypLabel>();
	protected ColourInfo colourInfo;
	protected BitmapImage image;
	protected int fontStyle;
	protected Rgb dayFontColour;
	protected int offset;
	private Rgb nightFontColour;

	public void setType(int type) {
		this.type = type;
	}

	public void setSubType(int subType) {
		this.subType = subType;
	}

	public int getType() {
		return type;
	}

	public int getSubType() {
		return subType;
	}

	public void addLabel(String text) {
		labels.add(new TypLabel(text));
	}

	public void setColourInfo(ColourInfo colourInfo) {
		this.colourInfo = colourInfo;
	}

	public void setImage(BitmapImage image) {
		this.image = image;
	}

	public void setFontStyle(int font) {
		this.fontStyle = font;
	}

	public void setDayCustomColor(String value) {
		dayFontColour = new Rgb(value);
	}

	public abstract void write(ImgFileWriter writer, CharsetEncoder encoder);

	public int getOffset() {
		return offset;
	}

	public void setNightCustomColor(String value) {
		nightFontColour = new Rgb(value);
	}

	/**
	 * Make the label block separately as we need its length before we write it out properly.
	 *
	 * @param encoder For encoding the strings as bytes.
	 * @return A byte buffer with position set to the length of the block.
	 */
	protected ByteBuffer makeLabelBlock(CharsetEncoder encoder) {
		ByteBuffer out = ByteBuffer.allocate(256);
		for (TypLabel tl : labels) {
			out.put((byte) tl.getLang());
			CharBuffer cb = CharBuffer.wrap(tl.getText());
			try {
				ByteBuffer buffer = encoder.encode(cb);
				out.put(buffer);
			} catch (CharacterCodingException ignore) {
				System.out.println("WARNING: failed to encode string: " + tl.getText() +
						". File should be in unicode");
			}
			out.put((byte) 0);
		}

		return out;
	}
}