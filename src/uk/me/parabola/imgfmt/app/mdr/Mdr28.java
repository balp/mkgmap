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
package uk.me.parabola.imgfmt.app.mdr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * One of these per region name. There are pointers into the other sections
 * that are sorted by region to the first record that has the given name.
 *
 * @author Steve Ratcliffe
 */
public class Mdr28 extends MdrSection implements HasHeaderFlags {
	private final List<Mdr28Record> index = new ArrayList<Mdr28Record>();

	public Mdr28(MdrConfig config) {
		setConfig(config);
	}

	public void buildFromRegions(List<Mdr13Record> regions) {
		int record = 0;
		for (Mdr13Record region : regions) {
			Mdr28Record mdr28 = region.getMdr28();
			if (mdr28 != null) {
				mdr28.setIndex(++record);
				index.add(mdr28);
			}
		}
	}

	/**
	 * Write out the contents of this section.
	 *
	 * @param writer Where to write it.
	 */
	public void writeSectData(ImgFileWriter writer) {
		PointerSizes sizes = getSizes();
		int size21 = sizes.getSize(21);
		int size23 = sizes.getSize(23);
		int size27 = sizes.getSize(27);

		for (Mdr28Record mdr28 : index) {
			putN(writer, size23, mdr28.getMdr23());
			putStringOffset(writer, mdr28.getStrOffset());
			putN(writer, size21, mdr28.getMdr21());
			putN(writer, size27, mdr28.getMdr27());
		}
	}

	/**
	 * The size of a record in the section.  This is not a constant and might vary
	 * on various factors, such as the file version, if we are preparing for a
	 * device, the number of maps etc.
	 *
	 * @return The size of a record in this section.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();

		return sizes.getSize(23)
				+ sizes.getStrOffSize()
				+ sizes.getSize(21)
				+ sizes.getSize(27);
	}

	/**
	 * The number of records in this section.
	 *
	 * @return The number of items in the section.
	 */
	public int getNumberOfItems() {
		return index.size();
	}

	/**
	 * Flag purposes are not known.
	 */
	public int getExtraValue() {
		return 0x7;
	}

	public List<Mdr28Record> getIndex() {
		return Collections.unmodifiableList(index);
	}
}
