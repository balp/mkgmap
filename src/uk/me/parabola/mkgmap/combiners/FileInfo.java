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
 * Create date: Nov 15, 2007
 */
package uk.me.parabola.mkgmap.combiners;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.lbl.LBLFileReader;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.trergn.TREFileReader;
import uk.me.parabola.imgfmt.app.trergn.TREHeader;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

import static uk.me.parabola.mkgmap.combiners.FileKind.*;

/**
 * Used for holding information about an individual file that will be made into
 * a gmapsupp file.
 *
 * @author Steve Ratcliffe
 */
public class FileInfo {
	private static final Logger log = Logger.getLogger(FileInfo.class);

	private static final int ENTRY_SIZE = 240;

	private static final List<String> KNOWN_FILE_TYPE_EXT = Arrays.asList(
			"TRE", "RGN", "LBL", "NET", "NOD",
			"TYP"
	);

	// The name of the file.
	private final String filename;

	// The kind of file, see *KIND definitions above.
	private FileKind kind;

	private String mapname;
	private int hexname;
	private String innername;
	private String description;

	// If this is an img file, the size of various sections.
	private int rgnsize;
	private int tresize;
	private int lblsize;
	private int netsize;
	private int nodsize;

	private final List<Integer> fileSizes = new ArrayList<Integer>();
	private String[] copyrights;
	private CommandArgs args;
	private String mpsName;
	private int codePage;
	private int sortOrderId;

	private FileInfo(String filename, FileKind kind) {
		this.filename = filename;
		this.kind = kind;
	}

	// The area covered by the map, if it is a IMG file
	private Area bounds;

	public String getMapname() {
		return mapname;
	}

	protected void setMapname(String mapname) {
		this.mapname = mapname;
	}

	public String getDescription() {
		return description;
	}

	protected void setDescription(String description) {
		this.description = description;
	}

	public int getRgnsize() {
		return rgnsize;
	}

	protected void setRgnsize(int rgnsize) {
		this.rgnsize = rgnsize;
	}

	public int getTresize() {
		return tresize;
	}

	protected void setTresize(int tresize) {
		this.tresize = tresize;
	}

	public int getLblsize() {
		return lblsize;
	}

	protected void setLblsize(int lblsize) {
		this.lblsize = lblsize;
	}

	public Area getBounds() {
		return bounds;
	}

	/**
	 * Create a file info the the given file.
	 *
	 * @param inputName The filename to examine.
	 * @return The FileInfo structure giving information about the file.
	 * @throws FileNotFoundException If the file doesn't actually exist.
	 */
	public static FileInfo getFileInfo(String inputName) throws FileNotFoundException {

		int end = inputName.length();
		String ext = inputName.substring(end - 3).toUpperCase(Locale.ENGLISH);
		FileInfo info;

		if (ext.equals("IMG")) {
			info = imgInfo(inputName);
		} else if ("TYP".equals(ext)) {
			info = fileInfo(inputName, TYP_KIND);
		} else if (KNOWN_FILE_TYPE_EXT.contains(ext)) {
			info = fileInfo(inputName, APP_KIND);
		} else {
			info = new FileInfo(inputName, UNKNOWN_KIND);
		}

		return info;
	}

	/**
	 * A TYP file or a component file that goes into a .img (a TRE, LBL etc).
	 * The component files are not usually given on the command line like this
	 * but you can do.
	 * 
	 * @param inputName The input file name.
	 * @param kind The kind of file being added.
	 */
	private static FileInfo fileInfo(String inputName, FileKind kind) {
		FileInfo info = new FileInfo(inputName, kind);

		// Get the size of the file.
		File f = new File(inputName);
		info.fileSizes.add((int) f.length());

		if (inputName.toLowerCase().endsWith(".lbl")) {
			lblInfo(inputName, info);
		} else if (inputName.toLowerCase().endsWith(".typ")) {
			typInfo(inputName, info);
		}

		return info;
	}

	/**
	 * Read information from the TYP file that we might need when combining it with other files.
	 * @param filename The name of the file.
	 * @param info The information will be stored here.
	 */
	private static void typInfo(String filename, FileInfo info) {
		ImgChannel chan = new FileImgChannel(filename, "r");
		try {
			BufferedImgFileReader fr = new BufferedImgFileReader(chan);
			fr.position(0x15);
			info.setCodePage(fr.getChar());
		} finally {
			Utils.closeFile(chan);
		}
	}

	/**
	 * An IMG file, this involves real work. We have to read in the file and
	 * extract several pieces of information from it.
	 *
	 * @param inputName The name of the file.
	 * @return The information obtained.
	 * @throws FileNotFoundException If the file doesn't exist.
	 */
	private static FileInfo imgInfo(String inputName) throws FileNotFoundException {
		FileSystem imgFs = ImgFS.openFs(inputName);

		try {
			FileSystemParam params = imgFs.fsparam();
			log.info("Desc", params.getMapDescription());
			log.info("Blocksize", params.getBlockSize());

			FileInfo info = new FileInfo(inputName, UNKNOWN_KIND);
			info.setDescription(params.getMapDescription());

			File f = new File(inputName);
			String name = f.getName();
			int dot = name.lastIndexOf('.');
			if (dot < 0) {
				name = "0";
			} else {
				if (dot > name.length())
					dot = name.length();
				if (dot > 8)
					dot = 8;
				name = name.substring(0, dot);
			}
			info.setMapname(name);

			boolean hasTre = false;
			List<DirectoryEntry> entries = imgFs.list();
			for (DirectoryEntry ent : entries) {
				if (ent.isSpecial())
					continue;

				log.info("file", ent.getFullName());
				String ext = ent.getExt();

				if ("TRE".equals(ext)) {
					info.setTresize(ent.getSize());
					info.setInnername(ent.getName());

					treInfo(imgFs, ent, info);
					hasTre = true;
				} else if ("RGN".equals(ext)) {
					int size = ent.getSize();
					info.setRgnsize(size);
				} else if ("LBL".equals(ext)) {
					info.setLblsize(ent.getSize());
					lblInfo(imgFs, ent, info);
				} else if ("NET".equals(ext)) {
					info.setNetsize(ent.getSize());
				} else if ("NOD".equals(ext)) {
					info.setNodsize(ent.getSize());
				} else if ("MDR".equals(ext)) {
					// It is not actually a regular img file, so change the kind.
					info.setKind(MDR_KIND);
				} else if ("MPS".equals(ext)) {
					// This is a gmapsupp file containing several maps.
					info.setKind(GMAPSUPP_KIND);
					info.mpsName = ent.getFullName();
				}

				info.fileSizes.add(ent.getSize());
			}

			if (info.getKind() == UNKNOWN_KIND && hasTre)
				info.setKind(IMG_KIND);
			
			return info;
		} finally {
			imgFs.close();
		}
	}

	/**
	 * Obtain the information that we need from the TRE section.
	 * @param imgFs The filesystem
	 * @param ent The filename within the filesystem of the TRE file.
	 * @param info This is where the information will be saved.
	 * @throws FileNotFoundException If the file is not found in the filesystem.
	 */
	private static void treInfo(FileSystem imgFs, DirectoryEntry ent, FileInfo info) throws FileNotFoundException {
		TREFileReader treFile = null;
		try {
			ImgChannel treChan = imgFs.open(ent.getFullName(), "r");
			treFile = new TREFileReader(treChan);

			info.setBounds(treFile.getBounds());

			info.setCopyrights(treFile.getCopyrights());

			info.setHexname(((TREHeader) treFile.getHeader()).getMapId());
		} finally {
			Utils.closeFile(treFile);
		}
	}

	/**
	 * Obtain the information we need from a LBL file.
	 */
	private static void lblInfo(FileSystem imgFs, DirectoryEntry ent, FileInfo info) throws FileNotFoundException {
		ImgChannel chan = imgFs.open(ent.getFullName(), "r");
		lblInfo(chan, info);
	}

	private static void lblInfo(String filename, FileInfo info) {
		FileImgChannel r = new FileImgChannel(filename, "r");
		try {
			lblInfo(r, info);
		} finally {
			Utils.closeFile(r);
		}
	}

	private static void lblInfo(ImgChannel chan, FileInfo info) {
		LBLFileReader lblFile = new LBLFileReader(chan);

		info.setCodePage(lblFile.getCodePage());
		info.setSortOrderId(lblFile.getSortOrderId());
	}

	private void setBounds(Area area) {
		this.bounds = area;
	}

	public String getFilename() {
		return filename;
	}

	public boolean isImg() {
		return kind == IMG_KIND;
	}

	protected void setKind(FileKind kind) {
		this.kind = kind;
	}

	public FileKind getKind() {
		return kind;
	}

	/**
	 * Get the number header slots (512 byte entry) required to represent this file
	 * at the given block size.
	 * Each sub-file will need at least one block and so we go through each
	 * separately and round up for each and return the total.
	 *
	 * @param blockSize The block size.
	 * @return The number of 512 byte header entries that are needed for all the subfiles
	 * in this .img file.
	 */
	public int getNumHeaderEntries(int blockSize) {
		int totHeaderSlots = 0;
		for (int size : fileSizes) {
			// You use up one header slot for every 240 blocks with a minimum
			// of one slot
			int nblocks = (size + (blockSize-1)) / blockSize;
			totHeaderSlots += (nblocks + (ENTRY_SIZE - 1)) / ENTRY_SIZE;
		}
		return totHeaderSlots;
	}

	/**
	 * Get the number of blocks for all the sub-files of this file at the given block size.
	 * Note that a complete block is always used for a file.
	 *
	 * For TYP files and other files that do not have sub-files, then it is just the number of blocks
	 * for the complete file.
	 * 
	 * @param bs The block size at which to calculate the value.
	 * @return The number of blocks at the given size required to save all the sub-files of this file.
	 */
	public int getNumBlocks(int bs) {
		int totBlocks = 0;
		for (int size : fileSizes) {
			int nblocks = (size + (bs - 1)) / bs;
			totBlocks += nblocks;
		}
		return totBlocks;
	}

	public int getMapnameAsInt() {
		try {
			return Integer.valueOf(mapname);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	protected void setCopyrights(String[] copyrights) {
		this.copyrights = copyrights;
	}

	public String[] getCopyrights() {
		return copyrights;
	}

	public int getNetsize() {
		return netsize;
	}

	protected void setNetsize(int netsize) {
		this.netsize = netsize;
	}

	public int getNodsize() {
		return nodsize;
	}

	protected void setNodsize(int nodsize) {
		this.nodsize = nodsize;
	}

	public void setArgs(CommandArgs args) {
		this.args = args;
	}

	public String getFamilyName() {
		return args.get("family-name", "family name");
	}

	public String getSeriesName() {
		return args.get("series-name", "series name");
	}

	public int getFamilyId() {
		return args.get("family-id", CommandArgs.DEFAULT_FAMILYID);
	}

	public int getProductId() {
		return args.get("product-id", 1);
	}

	public Sort getSort() {
		Sort sort = SrtTextReader.sortForCodepage(codePage);
		if (sort == null)
			sort = args.getSort();
		return sort;
	}

	public String getOutputDir() {
		return args.getOutputDir();
	}

	public String getMpsName() {
		return mpsName;
	}

	public String getInnername() {
		return innername;
	}

	public void setInnername(String name) {
		this.innername = name;
	}

	public void setHexname(int hexname) {
		this.hexname = hexname;
	}

	public int getHexname() {
		return hexname;
	}

	public int getCodePage() {
		return codePage;
	}

	public void setCodePage(int codePage) {
		this.codePage = codePage;
	}

	public void setSortOrderId(int sortOrderId) {
		this.sortOrderId = sortOrderId;
	}

	public boolean hasSortOrder() {
		return sortOrderId != 0;
	}
}
