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
 * Base routines and data used by points, lines and polygons.
 *
 * If fact they are all very similar, so there is very little extra in the
 * subclasses apart from the write routine.
 *
 * @author Steve Ratcliffe
 */
public abstract class TypElement {
	private int type;
	private int subType;

	protected final List<TypLabel> labels = new ArrayList<TypLabel>();

	protected Xpm xpm;

	protected int fontStyle;
	protected Rgb dayFontColour;
	protected Rgb nightFontColour;

	protected int offset;

	public void setType(int type) {
		this.type = type;
	}

	public void setSubType(int subType) {
		this.subType = subType;
	}

	public int getType() {
		return type;
	}

	/**
	 * Get the type in the format required for writing in the typ file sections.
	 */
	public int getTypeForFile() {
		return (type << 5) | (subType & 0x1f);
	}

	public void addLabel(String text) {
		labels.add(new TypLabel(text));
	}

	public void setXpm(Xpm xpm) {
		this.xpm = xpm;
	}

	public void setFontStyle(int font) {
		this.fontStyle = font;
	}

	public void setDayFontColor(String value) {
		dayFontColour = new Rgb(value);
	}

	public void setNightCustomColor(String value) {
		nightFontColour = new Rgb(value);
	}

	public abstract void write(ImgFileWriter writer, CharsetEncoder encoder);

	public int getOffset() {
		return offset;
	}

	/**
	 * Does this element have two colour bitmaps, with possible automatic night colours. For lines and polygons.
	 *
	 * Overridden for points and icons.
	 */
	public boolean simpleBitmap() {
		return true;
	}

	/**
	 * Make the label block separately as we need its length before we write it out properly.
	 *
	 * @param encoder For encoding the strings as bytes.
	 * @return A byte buffer with position set to the length of the block.
	 */
	protected ByteBuffer makeLabelBlock(CharsetEncoder encoder) {
		ByteBuffer out = ByteBuffer.allocate(256 * labels.size());
		for (TypLabel tl : labels) {
			out.put((byte) tl.getLang());
			CharBuffer cb = CharBuffer.wrap(tl.getText());
			try {
				ByteBuffer buffer = encoder.encode(cb);
				out.put(buffer);
			} catch (CharacterCodingException ignore) {
				String name = encoder.charset().name();
				//System.out.println("cs " + name);
				throw new TypLabelException(name);
			}
			out.put((byte) 0);
		}

		return out;
	}

	/**
	 * Write the label block, this is the same for all element types.
	 * @param encoder To properly encode the labels.
	 */
	protected void writeLabelBlock(ImgFileWriter writer, CharsetEncoder encoder) {
		ByteBuffer out = makeLabelBlock(encoder);

		int len = out.position();

		// The length is encoded as a variable length integer with the length indicated by a suffix.
		len = (len << 1) + 1;
		int mask = ~0xff;
		while ((len & mask) != 0) {
			mask <<= 8;
			len <<= 1;
		}

		// write out the length, I'm assuming that it will be 1 or 2 bytes
		if (len > 0xff)
			writer.putChar((char) len);
		else
			writer.put((byte) len);

		// Prepare and write buffer
		out.flip();
		writer.put(out);
	}

	/**
	 * Write out extended font information, colour and size.
	 *
	 * This is the same for each element type.
	 */
	protected void writeExtendedFontInfo(ImgFileWriter writer) {
		byte fontExt = (byte) fontStyle;
		if (dayFontColour != null)
			fontExt |= 0x8;

		if (nightFontColour != null)
			fontExt |= 0x10;

		writer.put(fontExt);

		if (dayFontColour != null)
			dayFontColour.write(writer, (byte) 0x10);

		if (nightFontColour != null)
			nightFontColour.write(writer, (byte) 0x10);
	}

	/**
	 * Write out an image. The width and height are written separately, because they are not
	 * repeated for the night image.
	 *
	 * @param xpm Either the day or night XPM.
	 */
	protected void writeImage(ImgFileWriter writer, Xpm xpm) {
		ColourInfo colourInfo = xpm.getColourInfo();

		writer.put((byte) colourInfo.getNumberOfSColoursForCM());
		writer.put((byte) colourInfo.getColourMode());

		colourInfo.write(writer);
		xpm.writeImage(writer);
	}
}
