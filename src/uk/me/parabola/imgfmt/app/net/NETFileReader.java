/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.LBLFileReader;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * Read the NET file.
 */
public class NETFileReader extends ImgFile {
	private final NETHeader netHeader = new NETHeader();

	// To begin with we only need LBL offsets.
	private final Map<Integer, Integer> offsetLabelMap = new HashMap<Integer, Integer>();
	private List<Integer> offsets;

	private List<City> cities;
	private int citySize;

	private List<Zip> zips;
	private int zipSize;
	private LBLFileReader labels;

	public NETFileReader(ImgChannel chan) {
		setHeader(netHeader);

		setReader(new BufferedImgFileReader(chan));
		netHeader.readHeader(getReader());

		readLabelOffsets();
	}

	/**
	 * Get the label offset, given the NET offset.
	 * @param netOffset An offset into NET 1, as found in the road entries in
	 * RGN for example.
	 * @return The offset into LBL as found in NET 1.
	 */
	public int getLabelOffset(int netOffset) {
		Integer off = offsetLabelMap.get(netOffset);
		if (off == null)
			return 0;
		else
			return off;
	}

	/**
	 * Get the list of roads from the net section.
	 *
	 * Saving the bare minimum that is needed, please improve.
	 * @return A list of RoadDefs. Note that currently not everything is
	 * populated in the road def so it can't be written out as is.
	 */
	public List<RoadDef> getRoads() {
		ImgFileReader reader = getReader();
		int start = netHeader.getRoadDefinitionsStart();

		List<RoadDef> roads = new ArrayList<RoadDef>();
		int record = 0;
		for (int off : offsets) {
			reader.position(start + off);

			RoadDef road = new RoadDef(++record, null);
			readLabels(reader, road);
			byte netFlags = reader.get();
			/*int len =*/ reader.getu3();

			int[] counts = new int[24];
			int level = 0;
			while (level < 24) {
				int n = reader.get();
				counts[level++] = (n & 0x7f);
				if ((n & 0x80) != 0)
					break;
			}

			for (int i = 0; i < level; i++) {
				int c = counts[i];
				for (int j = 0; j < c; j++) {
					/*byte b =*/ reader.get();
					/*char sub =*/ reader.getChar();
				}
			}

			if ((netFlags & RoadDef.NET_FLAG_ADDRINFO) != 0) {
				char flags2 = reader.getChar();

				int zipFlag = (flags2 >> 10) & 0x3;
				int cityFlag = (flags2 >> 12) & 0x3;
				int numberFlag = (flags2 >> 14) & 0x3;

				road.setZip(fetchZipCity(reader, zipFlag, zips, zipSize));
				road.setCity(fetchZipCity(reader, cityFlag, cities, citySize));

				fetchNumber(reader, numberFlag);
			}

			if ((netFlags & RoadDef.NET_FLAG_NODINFO) != 0) {
				int nodFlags = reader.get();
				int nbytes = nodFlags & 0x3;
				if (nbytes > 0) {
					/*int nod = */reader.getUint(nbytes+1);
				}
			}

			roads.add(road);
		}
		return roads;
	}

	/**
	 * Fetch a zip or a city.
	 * @param <T> Can be city or zip.
	 * @return The found City or Zip.
	 */
	private <T> T fetchZipCity(ImgFileReader reader, int flag, List<T> list, int size) {
		T item = null;
		if (flag == 2) {
			// fetch city/zip index
			int ind = (size == 2)? reader.getChar(): (reader.get() & 0xff);
			if (ind != 0)
				item = list.get(ind-1);
		} else if (flag == 3) {
			// there is no item
		} else if (flag == 0) {
			// Skip over these
			int n = reader.get();
			reader.get(n);
		} else if (flag == 1) {
			// Skip over these
			int n = reader.getChar();
			reader.get(n);
		} else {
			assert false : "flag is " + flag;
		}
		return item;
	}

	/**
	 * Fetch a block of numbers.
	 * @param reader The reader.
	 * @param numberFlag The flag that says how the block is formatted.
	 */
	private void fetchNumber(ImgFileReader reader, int numberFlag) {
		int n = 0;
		if (numberFlag == 0) {
			n = reader.get();
		} else if (numberFlag == 1) {
			n = reader.getChar();
		} else if (numberFlag == 3) {
			// There is no block
			return;
		} else {
			// Possible but don't know what to do in this context
			assert false;
		}
		if (n > 0)
			reader.get(n);
	}

	private void readLabels(ImgFileReader reader, RoadDef road) {
		for (int i = 0; i < 4; i++) {
			int lab = reader.getu3();
			Label label = labels.fetchLabel(lab & 0x7fffff);
			road.addLabel(label);
			if ((lab & 0x800000) != 0)
				break;
		}
	}

	/**
	 * The first field in NET 1 is a label offset in LBL.  Currently we
	 * are only interested in that to convert between a NET 1 offset and
	 * a LBL offset.
	 */
	private  void readLabelOffsets() {
		ImgFileReader reader = getReader();
		offsets = readOffsets();
		int start = netHeader.getRoadDefinitionsStart();
		for (int off : offsets) {
			reader.position(start + off);
			int labelOffset = reader.getu3();
			// TODO what if top bit is not set?, there can be more than one name and we will miss them
			offsetLabelMap.put(off, labelOffset & 0x7fffff);
		}
	}

	/**
	 * NET 3 contains a list of all the NET 1 record start positions.  They
	 * are in alphabetical order of name.  So read them in and sort into
	 * memory address order.
	 * @return A list of start offsets in NET 1, sorted by increasing offset.
	 */
	private List<Integer> readOffsets() {
		int start = netHeader.getSortedRoadsStart();
		int end = netHeader.getSortedRoadsEnd();
		ImgFileReader reader = getReader();
		reader.position(start);

		List<Integer> offsets = new ArrayList<Integer>();
		while (reader.position() < end) {
			int net1 = reader.getu3();

			// The offset is stored in the bottom 22 bits. The top 2 bits are an index into the list
			// of lbl pointers in the net1 entry. Since we pick up all the labels at a particular net1
			// entry we only need one of the offsets so pick the first one.
			int idx = (net1 >> 22) & 0x3;
			if (idx == 0)
				offsets.add(net1 & 0x3fffff);
		}

		// Sort in address order in the hope of speeding up reading.
		Collections.sort(offsets);
		return offsets;
	}

	public void setCities(List<City> cities) {
		this.cities = cities;
		this.citySize = cities.size() > 255? 2: 1;
	}

	public void setZips(List<Zip> zips) {
		this.zips = zips;
		this.zipSize = zips.size() > 255? 2: 1;
	}

	public void setLabels(LBLFileReader labels) {
		this.labels = labels;
	}
}
