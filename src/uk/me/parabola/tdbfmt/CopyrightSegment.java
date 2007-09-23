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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.tdbfmt;

import java.io.IOException;

/**
 * One copyright that is within the copyright block.
 *
 * @author Steve Ratcliffe
 */
public class CopyrightSegment {
	 

	/**
	 * Source information text string.  Describes what data sources were used
	 * in generating the map.
	 */
	private static final int CODE_SOURCE_INFORMATION = 0x00;

	/** Copyright information from the map manufacturer. */
	private static final int CODE_COPYRIGHT_TEXT_STRING = 0x06;

	/**
	 * A filename that contains a CMP image to be printed along with
	 * the map.
	 */
	private static final int CODE_COPYRIGHT_BITMAP_REFERENCE = 0x07;

	/**
	 * A code that shows what kind of copyright information is
	 * contaied in this segment.
	 * The field {@link #extraProperties} can be used too as extra information.
	 */
	private byte copyrightCode;
	private byte whereCode;
	private short extraProperties;
	private String copyright;

	public CopyrightSegment(StructuredInputStream ds) throws IOException {
		copyrightCode = (byte) ds.read();
		whereCode = (byte) ds.read();
		extraProperties = (short) ds.read2();
		copyright = ds.readString();
	}

	public String toString() {
		return "Copyright: "
				+ copyrightCode
				+ ", where="
				+ whereCode
				+ ", extra="
				+ extraProperties
				+ ": "
				+ copyright
				;
	}
}