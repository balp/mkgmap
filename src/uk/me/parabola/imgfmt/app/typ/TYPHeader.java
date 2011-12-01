/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Dec 14, 2007
 */
package uk.me.parabola.imgfmt.app.typ;

import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Section;

/**
 * The header for the TYP file.
 *
 * @author Thomas Lußnig
 */
public class TYPHeader extends CommonHeader {
	public static final int HEADER_LEN = 0x9c; // 0x6e;

	private char familyId;
	private char productId;
	private char codePage;

	private final Section pointData = new Section();
	private final Section lineData = new Section(pointData);
	private final Section polygonData = new Section(lineData);

	private final Section pointIndex = new Section(polygonData, (char) 2);
	private final Section lineIndex = new Section(pointIndex, (char) 2);
	private final Section polygonIndex = new Section(lineIndex, (char) 2);

	private final Section shapeStacking = new Section(polygonIndex, (char) 5);

	private final Section iconData = new Section(polygonIndex);
	private final Section iconIndex = new Section(iconData, (char) 3);

	private final Section labels = new Section(iconIndex);
	private final Section stringIndex = new Section(labels);
	private final Section typeIndex = new Section(stringIndex);

	public TYPHeader() {
		super(HEADER_LEN, "GARMIN TYP");
	}

	/**
	 * Read the rest of the header.  Specific to the given file.  It is guaranteed
	 * that the file position will be set to the correct place before this is
	 * called.
	 *
	 * @param reader The header is read from here.
	 */
	protected void readFileHeader(ImgFileReader reader) {
		// Reset position for the real header reading code.
		reader.position(COMMON_HEADER_LEN);
		codePage = reader.getChar();	// 1252
		pointData.setPosition(reader.getInt());
		pointData.setSize(reader.getInt());

		lineData.setPosition(reader.getInt());
		lineData.setSize(reader.getInt());

		polygonData.setPosition(reader.getInt());
		polygonData.setSize(reader.getInt());

		familyId = reader.getChar();
		productId = reader.getChar();

		pointIndex.setPosition(reader.getInt());
		pointIndex.setItemSize(reader.getChar());
		pointIndex.setSize(reader.getInt());

		lineIndex.setPosition(reader.getInt());
		lineIndex.setItemSize(reader.getChar());
		lineIndex.setSize(reader.getInt());

		polygonIndex.setPosition(reader.getInt());
		polygonIndex.setItemSize(reader.getChar());
		polygonIndex.setSize(reader.getInt());

		shapeStacking.setPosition(reader.getInt());
		shapeStacking.setItemSize(reader.getChar());
		shapeStacking.setSize(reader.getInt());
	}

	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * This header appears to have a different layout to most other headers.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putChar(codePage);

		pointData.writeSectionInfo(writer);
		lineData.writeSectionInfo(writer);
		polygonData.writeSectionInfo(writer);

		writer.putChar(familyId);
		writer.putChar(productId);

		// Can't use Section.writeSectionInfo here as there is an unusual layout.
		writeSectionInfo(writer, pointIndex);
		writeSectionInfo(writer, lineIndex);
		writeSectionInfo(writer, polygonIndex);
		writeSectionInfo(writer, shapeStacking);

		if (getHeaderLength() > 0x5b) {
			writeSectionInfo(writer, iconIndex);
			writer.put((byte) 0x13);
			iconData.writeSectionInfo(writer);
			writer.putInt(0);
		}

		if (getHeaderLength() > 0x6e) {
			labels.writeSectionInfo(writer);

			// not known, guessing. Different layout to other files.
			writer.putInt(stringIndex.getItemSize());
			writer.putInt(0x1b);
			writer.putInt(stringIndex.getPosition());
			writer.putInt(stringIndex.getSize());

			writer.putInt(typeIndex.getItemSize());
			writer.putInt(0x1b);
			writer.putInt(typeIndex.getPosition());
			writer.putInt(typeIndex.getSize());

			writer.putChar((char) 0);
		}
	}

	/**
	 * There is an unusual layout of the section pointers in the TYP file for the sections
	 * that have an item size.
	 */
	private void writeSectionInfo(ImgFileWriter writer, Section section) {
		writer.putInt(section.getPosition());
		writer.putChar(section.getItemSize());
		writer.putInt(section.getSize());
	}

	void setCodePage(char codePage) {
		this.codePage = codePage;
	}

	Section getPointData() {
		return pointData;
	}

	void setFamilyId(char familyId) {
		this.familyId = familyId;
	}

	void setProductId(char productId) {
		this.productId = productId;
	}

	Section getPointIndex() {
		return pointIndex;
	}

	Section getShapeStacking() {
		return shapeStacking;
	}

	public Section getPolygonData() {
		return polygonData;
	}

	public Section getPolygonIndex() {
		return polygonIndex;
	}

	public Section getLineData() {
		return lineData;
	}

	public Section getLineIndex() {
		return lineIndex;
	}

	public Section getIconData() {
		return iconData;
	}

	public Section getIconIndex() {
		return iconIndex;
	}

	public Section getLabels() {
		return labels;
	}

	public Section getStringIndex() {
		return stringIndex;
	}

	public Section getTypeIndex() {
		return typeIndex;
	}
}
