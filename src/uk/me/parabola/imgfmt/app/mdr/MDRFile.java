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
package uk.me.parabola.imgfmt.app.mdr;

import java.util.Arrays;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.FileBackedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.Country;
import uk.me.parabola.imgfmt.app.lbl.Region;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.imgfmt.app.mdr.MdrSection.PointerSizes;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

/**
 * The MDR file.  This is embedded into a .img file, either its own
 * separate one, or as one file in the gmapsupp.img.
 *
 * @author Steve Ratcliffe
 */
public class MDRFile extends ImgFile {
	private final MDRHeader mdrHeader;

	// The sections
	private final Mdr1 mdr1;
	private final Mdr4 mdr4;
	private final Mdr5 mdr5;
	private final Mdr6 mdr6;
	private final Mdr7 mdr7;
	private final Mdr8 mdr8;
	private final Mdr9 mdr9;
	private final Mdr10 mdr10;
	private final Mdr11 mdr11;
	private final Mdr12 mdr12;
	private final Mdr13 mdr13;
	private final Mdr14 mdr14;
	private final Mdr15 mdr15;
	private final Mdr17 mdr17;
	private final Mdr18 mdr18;
	private final Mdr19 mdr19;
	private final Mdr20 mdr20;
	private final Mdr21 mdr21;
	private final Mdr22 mdr22;
	private final Mdr23 mdr23;
	private final Mdr24 mdr24;
	private final Mdr25 mdr25;
	private final Mdr26 mdr26;
	private final Mdr27 mdr27;
	private final Mdr28 mdr28;
	private final Mdr29 mdr29;

	private int currentMap;

	private final boolean forDevice;

	private final MdrSection[] sections;
	private PointerSizes sizes;

	public MDRFile(ImgChannel chan, MdrConfig config) {
		Sort sort = config.getSort();

		forDevice = config.isForDevice();

		mdrHeader = new MDRHeader(config.getHeaderLen());
		mdrHeader.setSort(sort);
		setHeader(mdrHeader);
		if (config.isWritable()) {
			ImgFileWriter fileWriter = new FileBackedImgFileWriter(chan, config.getOutputDir());
			setWriter(fileWriter);

			// Position at the start of the writable area.
			position(mdrHeader.getHeaderLength());
		} else {
			setReader(new BufferedImgFileReader(chan));
			mdrHeader.readHeader(getReader());
		}

		// Initialise the sections
		mdr1 = new Mdr1(config);
		mdr4 = new Mdr4(config);
		mdr5 = new Mdr5(config);
		mdr6 = new Mdr6(config);
		mdr7 = new Mdr7(config);
		mdr8 = new Mdr8(config);
		mdr9 = new Mdr9(config);
		mdr10 = new Mdr10(config);
		mdr11 = new Mdr11(config);
		mdr12 = new Mdr12(config);
		mdr13 = new Mdr13(config);
		mdr14 = new Mdr14(config);
		mdr15 = new Mdr15(config);
		mdr17 = new Mdr17(config);
		mdr18 = new Mdr18(config);
		mdr19 = new Mdr19(config);
		mdr20 = new Mdr20(config);
		mdr21 = new Mdr21(config);
		mdr22 = new Mdr22(config);
		mdr23 = new Mdr23(config);
		mdr24 = new Mdr24(config);
		mdr25 = new Mdr25(config);
		mdr26 = new Mdr26(config);
		mdr27 = new Mdr27(config);
		mdr28 = new Mdr28(config);
		mdr29 = new Mdr29(config);

		this.sections = new MdrSection[]{
				null,
				mdr1, null, null, mdr4, mdr5, mdr6,
				mdr7, mdr8, mdr9, mdr10, mdr11, mdr12,
				mdr13, mdr14, mdr15, null, mdr17, mdr18, mdr19,
				mdr20, mdr21, mdr22, mdr23, mdr24, mdr25,
				mdr26, mdr27, mdr28, mdr29,
		};

		mdr11.setMdr10(mdr10);
	}

	/**
	 * Add a map to the index.  You must add the map, then all of the items
	 * that belong to it, before adding the next map.
	 * @param mapName The numeric name of the map.
	 * @param codePage The code page of the map.
	 */
	public void addMap(int mapName, int codePage) {
		currentMap++;
		mdr1.addMap(mapName);
		Sort sort = mdrHeader.getSort();
		if (sort.getSortOrderId() == 0) {
			sort = SrtTextReader.sortForCodepage(codePage);
			mdrHeader.setSort(sort);
		} else {
			if (sort.getCodepage() != codePage)
				System.err.println("WARNING: input files have different code pages");
		}

	}

	public Mdr14Record addCountry(Country country) {
		Mdr14Record record = new Mdr14Record();

		String name = country.getLabel().getText();
		record.setMapIndex(currentMap);
		record.setCountryIndex((int) country.getIndex());
		record.setLblOffset(country.getLabel().getOffset());
		record.setName(name);
		record.setStrOff(createString(name));

		mdr14.addCountry(record);
		return record;
	}

	public Mdr13Record addRegion(Region region, Mdr14Record country) {
		Mdr13Record record = new Mdr13Record();

		String name = region.getLabel().getText();
		record.setMapIndex(currentMap);
		record.setLblOffset(region.getLabel().getOffset());
		record.setCountryIndex(region.getCountry().getIndex());
		record.setRegionIndex(region.getIndex());
		record.setName(name);
		record.setStrOffset(createString(name));
		record.setMdr14(country);

		mdr13.addRegion(record);
		return record;
	}

	public void addCity(Mdr5Record city) {
		int labelOffset = city.getLblOffset();
		if (labelOffset != 0) {
			String name = city.getName();
			assert name != null : "off=" + labelOffset;
			city.setMapIndex(currentMap);
			city.setStringOffset(createString(name));
			mdr5.addCity(city);
		}
	}
	
	public void addZip(Zip zip) {
		int strOff = createString(zip.getLabel().getText());
		mdr6.addZip(currentMap, zip, strOff);
	}

	public void addPoint(Point point, Mdr5Record city, boolean isCity) {
		assert currentMap > 0;

		int fullType = point.getType();
		if (!MdrUtils.canBeIndexed(fullType))
			return;

		Label label = point.getLabel();
		String name = label.getText();
		int strOff = createString(name);

		Mdr11Record poi = mdr11.addPoi(currentMap, point, name, strOff);
		poi.setCity(city);
		poi.setIsCity(isCity);
		poi.setType(fullType);

		mdr4.addType(point.getType());
	}

	public void addStreet(RoadDef street, Mdr5Record mdrCity) {
		// Add a separate record for each name
		for (Label lab : street.getLabels()) {
			if (lab == null)
				break;

			if (lab.getOffset() == 0)
				continue;
			
			String name = lab.getText();
			String cleanName = cleanUpName(name);
			int strOff = createString(cleanName);

			// We sort on the dirty name (ie with the Garmin shield codes) although those codes do not
			// affect the sort order. The string for mdr15 does not include the shield codes.
			mdr7.addStreet(currentMap, name, lab.getOffset(), strOff, mdrCity);
		}
	}

	/**
	 * Remove shields and other kinds of strange characters.  Perform any
	 * rearrangement of the name to make it searchable.
	 * @param name The street name as read from the img file.
	 * @return The name as it will go into the index.
	 */
	private String cleanUpName(String name) {
		return Label.stripGarminCodes(name);
	}

	public void write() {
		mdr15.release();
		
		for (MdrSection s : sections) {
			if (s != null)
				s.finish();
		}

		ImgFileWriter writer = getWriter();
		writeSections(writer);

		// Now refresh the header
		position(0);
		getHeader().writeHeader(writer);
	}

	/**
	 * Write all the sections out.
	 *
	 * The order of all the operations in this method is important. The order
	 * of the sections in the actual output file doesn't matter at all, so
	 * they can be re-ordered to suit.
	 *
	 * Most of the complexity here is arranging the order of things so that the smallest
	 * amount of temporary memory is required.
	 *
	 * @param writer File is written here.
	 */
	private void writeSections(ImgFileWriter writer) {
		sizes = new MdrMapSection.PointerSizes(sections);

		// Deal with the dependencies between the sections. The order of the following
		// statements is sometimes important.
		mdr28.buildFromRegions(mdr13.getRegions());
		mdr23.sortRegions(mdr13.getRegions());
		mdr29.buildFromCountries(mdr14.getCountries());
		mdr24.sortCountries(mdr14.getCountries());
		mdr26.sortMdr28(mdr28.getIndex());

		writeSection(writer, 4, mdr4);

		mdr1.preWrite();
		mdr5.preWrite();
		mdr20.preWrite();

		// We write the following sections that contain per-map data, in the
		// order of the subsections of the reverse index that they are associated
		// with.
		writeSection(writer, 11, mdr11);
		mdr10.setNumberOfPois(mdr11.getNumberOfPois());
		mdr12.setIndex(mdr11.getIndex());
		mdr19.setPois(mdr11.getPois());
		mdr17.addPois(mdr11.getPois());
		mdr11.release();

		if (forDevice) {
			mdr19.preWrite();
			writeSection(writer, 19, mdr19);
			mdr18.setPoiTypes(mdr19.getPoiTypes());
			mdr19.release();
			writeSection(writer, 18, mdr18);
		}

		writeSection(writer, 10, mdr10);
		mdr9.setGroups(mdr10.getGroupSizes());
		mdr10.release();

		// mdr7 depends on the size of mdr20, so mdr20 must be built first
		mdr7.preWrite();
		mdr20.buildFromStreets(mdr7.getStreets());
		writeSection(writer, 7, mdr7);

		writeSection(writer, 5, mdr5);
		mdr25.sortCities(mdr5.getCities());
		mdr27.sortCities(mdr5.getCities());
		mdr17.addCities(mdr5.getSortedCities());
		mdr5.release();
		writeSection(writer, 6, mdr6);

		writeSection(writer, 20, mdr20);
		mdr20.release();

		mdr21.buildFromStreets(mdr7.getStreets());
		writeSection(writer, 21, mdr21);
		mdr21.release();
		
		mdr22.buildFromStreets(mdr7.getStreets());
		mdr8.setIndex(mdr7.getIndex());
		mdr17.addStreets(mdr7.getSortedStreets());

		mdr7.release();
		writeSection(writer, 22, mdr22);
		mdr17.addStreetsByCountry(mdr22.getStreets());
		mdr22.release();

		if (forDevice) {
			writeSection(writer, 17, mdr17);
			mdr17.release();
		}

		// The following do not have mdr1 subsections
		//writeSection(writer, 8, mdr8);
		writeSection(writer, 9, mdr9);
		writeSection(writer, 12, mdr12);
		writeSection(writer, 13, mdr13);
		writeSection(writer, 14, mdr14);
		writeSection(writer, 15, mdr15);

		writeSection(writer, 23, mdr23);
		writeSection(writer, 24, mdr24);
		writeSection(writer, 25, mdr25);
		mdr28.preWrite(); // TODO reorder writes below so this is not needed. Changes the output file though
		writeSection(writer, 26, mdr26);
		writeSection(writer, 27, mdr27);
		writeSection(writer, 28, mdr28);
		writeSection(writer, 29, mdr29);

		// write the reverse index last.
		mdr1.writeSubSections(writer);
		mdrHeader.setPosition(1, writer.position());

		mdr1.writeSectData(writer);
		mdrHeader.setItemSize(1, mdr1.getItemSize());
		mdrHeader.setEnd(1, writer.position());
		mdrHeader.setExtraValue(1, mdr1.getExtraValue());
	}

	/**
	 * Write out the given single section.
	 */
	private void writeSection(ImgFileWriter writer, int sectionNumber, MdrSection section) {

		// Some sections are just not written in the device config
		if (forDevice && Arrays.asList(12, 13, 14, 15, 21, 23, 26, 27, 28).contains(sectionNumber))
			return;

		section.setSizes(sizes);

		mdrHeader.setPosition(sectionNumber, writer.position());
		mdr1.setStartPosition(sectionNumber);

		section.preWrite();
		if (!forDevice && section instanceof MdrMapSection) {
			MdrMapSection mapSection = (MdrMapSection) section;
			mapSection.setMapIndex(mdr1);
			mapSection.initIndex(sectionNumber);
		}

		if (section instanceof HasHeaderFlags)
			mdrHeader.setExtraValue(sectionNumber, ((HasHeaderFlags) section).getExtraValue());

		section.writeSectData(writer);

		int itemSize = section.getItemSize();
		if (itemSize > 0)
			mdrHeader.setItemSize(sectionNumber, itemSize);

		mdrHeader.setEnd(sectionNumber, writer.position());
		mdr1.setEndPosition(sectionNumber);
	}

	/**
	 * Creates a string in MDR 15 and returns an offset value that can be
	 * used to refer to it in the other sections.
	 * @param str The text of the string.
	 * @return An offset value.
	 */
	private int createString(String str) {
		return mdr15.createString(str);
	}
}
