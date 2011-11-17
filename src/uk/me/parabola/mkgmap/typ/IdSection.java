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
package uk.me.parabola.mkgmap.typ;

import uk.me.parabola.imgfmt.app.typ.TypData;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

/**
 * Process lines from the id section of a typ.txt file.
 *
 * @author Steve Ratcliffe
 */
class IdSection implements ProcessSection {
	private final TypData data;

	public IdSection(TypData data) {
		this.data = data;
	}

	public void processLine(TokenScanner scanner, String name, String value) {
		int ival;
		try {
			ival = Integer.decode(value);
		} catch (NumberFormatException e) {
			throw new SyntaxException(scanner, "Bad integer " + value);
		}

		if (name.equalsIgnoreCase("FID")) {
			data.setFamilyId(ival);
		} else if (name.equalsIgnoreCase("ProductCode")) {
			data.setProductId(ival);
		} else if (name.equalsIgnoreCase("CodePage")) {
			data.setSort(SrtTextReader.sortForCodepage(ival));
		} else {
			throw new SyntaxException(scanner, "Unrecognised keyword in id section: " + name);
		}
	}

	public void finish(TokenScanner scanner) {
	}
}
