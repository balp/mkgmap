/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.Version;
import uk.me.parabola.util.Java2DConverter;

public class BoundarySaver {
	private static final Logger log = Logger.getLogger(BoundarySaver.class);

	public static final String LEGACY_DATA_FORMAT = ""; // legacy code just wrote the svn release or "svn"
	public static final String RAW_DATA_FORMAT = "RAW";
	public static final String QUADTREE_DATA_FORMAT = "QUADTREE";
	public static final int CURRENT_RECORD_ID = 1;
	
	public static final double RESET_DELTA = Double.POSITIVE_INFINITY; 

	private final File boundaryDir;
	private final String dataFormat;
	private uk.me.parabola.imgfmt.app.Area bbox;
	private final HashSet<String> writtenFileNames;

	private int minLat = Integer.MAX_VALUE;
	private int minLong = Integer.MAX_VALUE;
	private int maxLat = Integer.MIN_VALUE;
	private int maxLong = Integer.MIN_VALUE;
	
	private static final class StreamInfo {
		File file;
		String boundsKey;
		OutputStream stream;
		int lastAccessNo;

		public StreamInfo() {
			this.lastAccessNo = 0;
		}

		public boolean isOpen() {
			return stream != null;
		}

		public void close() {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException exp) {
					log.error(exp);
				}
			}
			stream = null;
		}
	}

	private int lastAccessNo = 0;
	private final List<StreamInfo> openStreams = new ArrayList<StreamInfo>();
	/** keeps the open streams */
	private final Map<String, StreamInfo> streams;
	private boolean createEmptyFiles = false;

	public BoundarySaver(File boundaryDir, String mode) {
		this.boundaryDir = boundaryDir;
		if (boundaryDir.exists() && boundaryDir.isDirectory() == false){
			log.error("output target exists and is not a directory");
			System.exit(-1);
		}
		this.dataFormat = mode;
		this.streams = new HashMap<String, StreamInfo>();
		this.writtenFileNames = new HashSet<String>();
	}

	/**
	 * Saves the given BoundaryQuadTree to a stream  
	 * @param bqt the BoundaryQuadTree
	 * @param boundsFileName the file name  
	 */
	public void saveQuadTree(BoundaryQuadTree bqt, String boundsFileName) {
		String[] parts = boundsFileName.split("[_" + Pattern.quote(".") + "]");
		String key = boundsFileName;
		if (parts.length >= 3) {
			key = parts[1] + "_" + parts[2];
		}

		try {
			StreamInfo streamInfo = getStream(key);
			if (streamInfo != null && streamInfo.isOpen()) {
				bqt.save(streamInfo.stream);
				writtenFileNames.add(boundsFileName);
			}
		} catch (Exception exp) {
			log.error("Cannot write boundary: " + exp, exp);
		}

		tidyStreams();

	}

	public void addBoundary(Boundary boundary) {
		Map<String, Area> splitBounds = splitArea(boundary.getArea());
		for (Entry<String, Area> split : splitBounds.entrySet()) {
			saveToFile(split.getKey(),
					new Boundary(split.getValue(), boundary.getTags(), boundary
							.getId()));
		}
	}

	public HashSet<String> end() {
		if (isCreateEmptyFiles() && getBbox() != null) {
			// a bounding box is set => fill the gaps with empty files
			for (int latSplit = BoundaryUtil.getSplitBegin(getBbox()
					.getMinLat()); latSplit <= BoundaryUtil
					.getSplitBegin(getBbox().getMaxLat()); latSplit += BoundaryUtil.RASTER) {
				for (int lonSplit = BoundaryUtil.getSplitBegin(getBbox()
						.getMinLong()); lonSplit <= BoundaryUtil
						.getSplitBegin(getBbox().getMaxLong()); lonSplit += BoundaryUtil.RASTER) {
					String key = BoundaryUtil.getKey(latSplit, lonSplit);

					// check if the stream already exist but do no open it
					StreamInfo stream = getStream(key, false);
					if (stream == null) {
						// it does not exist => create a new one to write out
						// the common header of the boundary file
						stream = getStream(key);
					}

					// close the stream if it is open
					if (stream.isOpen())
						stream.close();
					streams.remove(key);
				}
			}
		}

		// close the rest of the streams
		for (StreamInfo streamInfo : streams.values()) {
			streamInfo.close();
		}
		streams.clear();
		openStreams.clear();
		return writtenFileNames;
	}

	private void tidyStreams() {
		if (openStreams.size() < 100) {
			return;
		}

		Collections.sort(openStreams, new Comparator<StreamInfo>() {

			public int compare(StreamInfo o1, StreamInfo o2) {
				return o1.lastAccessNo - o2.lastAccessNo;
			}
		});

		log.debug(openStreams.size(), "open streams.");
		List<StreamInfo> closingStreams = openStreams.subList(0,
				openStreams.size() - 80);
		// close and remove the streams from the open list
		for (StreamInfo streamInfo : closingStreams) {
			log.debug("Closing", streamInfo.file);
			streamInfo.close();
		}
		closingStreams.clear();
		log.debug("Remaining", openStreams.size(), "open streams.");
	}

	private Map<String, Area> splitArea(Area areaToSplit) {
		return splitArea(areaToSplit, new HashMap<String, Area>());
	}
	
	/**
	 * Split a given area into the raster tiles. 
	 * @param areaToSplit the area
	 * @param splits a map the splitted tiles are added to
	 * @return the map with the splitted tiles
	 */
	private Map<String, Area> splitArea(Area areaToSplit, Map<String, Area> splits) {
		if (areaToSplit.isEmpty())
			return splits;
		
		// use high precision bounds with later rounding to avoid some little rounding
		// errors (49999.99999999 instead of 50000.0)
		Rectangle2D areaBounds = areaToSplit.getBounds2D();
		int sMinLong = BoundaryUtil.getSplitBegin((int)Math.round(areaBounds.getMinX()));
		int sMinLat = BoundaryUtil.getSplitBegin((int)Math.round(areaBounds.getMinY()));
		int sMaxLong = BoundaryUtil.getSplitEnd((int)Math.round(areaBounds.getMaxX()));
		int sMaxLat = BoundaryUtil.getSplitEnd((int)Math.round(areaBounds.getMaxY()));

		int dLon = sMaxLong- sMinLong;
		int dLat = sMaxLat - sMinLat;
		if (dLon > BoundaryUtil.RASTER || dLat > BoundaryUtil.RASTER) {
			// split into two halves
			Area a1;
			Area a2;
			if (dLon > dLat) {
				int midLon = BoundaryUtil.getSplitEnd(sMinLong+dLon/2);
				a1 = new Area(new Rectangle(sMinLong, sMinLat, midLon-sMinLong, dLat));
				a2 = new Area(new Rectangle(midLon, sMinLat, sMaxLong-midLon, dLat));
			} else {
				int midLat = BoundaryUtil.getSplitEnd(sMinLat+dLat/2);
				a1 = new Area(new Rectangle(sMinLong, sMinLat, dLon, midLat-sMinLat));
				a2 = new Area(new Rectangle(sMinLong, midLat, dLon, sMaxLat-midLat));
			}

			// intersect with the both halves
			// and split both halves recursively

			a1.intersect(areaToSplit);
			splitArea(a1, splits);
			// a1 is no longer needed => GC
			a1 = null;
			
			a2.intersect(areaToSplit);
			splitArea(a2, splits);
			
		} else {
			// the area fully fits into one raster tile
			splits.put(BoundaryUtil.getKey(sMinLat, sMinLong), areaToSplit);
		}
		return splits;

	}

	private void openStream(StreamInfo streamInfo, boolean newFile) {
		if (streamInfo.file.getParentFile().exists() == false
				&& streamInfo.file.getParentFile() != null)
			streamInfo.file.getParentFile().mkdirs();
		FileOutputStream fileStream = null;
		try {
			fileStream = new FileOutputStream(streamInfo.file, !newFile);
			streamInfo.stream = new BufferedOutputStream(fileStream);
			openStreams.add(streamInfo);
			if (newFile) {
				writeDefaultInfos(streamInfo.stream);

				String[] keyParts = streamInfo.boundsKey.split(Pattern
						.quote("_"));
				int lat = Integer.valueOf(keyParts[0]);
				int lon = Integer.valueOf(keyParts[1]);
				if (lat < minLat) {
					minLat = lat;
					log.debug("New min Lat:", minLat);
				}
				if (lat > maxLat) {
					maxLat = lat;
					log.debug("New max Lat:", maxLat);
				}
				if (lon < minLong) {
					minLong = lon;
					log.debug("New min Lon:", minLong);
				}
				if (lon > maxLong) {
					maxLong = lon;
					log.debug("New max Long:", maxLong);
				}
			}

		} catch (IOException exp) {
			log.error("Cannot save boundary: " + exp);
			if (fileStream != null) {
				try {
					fileStream.close();
				} catch (Throwable thr) {
				}
			}
		}
	}

	private StreamInfo getStream(String filekey) {
		return getStream(filekey, true);
	}

	private StreamInfo getStream(String filekey, boolean autoopen) {
		StreamInfo stream = streams.get(filekey);
		if (autoopen) {
			if (stream == null) {
				log.debug("Create stream for", filekey);
				stream = new StreamInfo();
				stream.boundsKey = filekey;
				stream.file = new File(boundaryDir, "bounds_" + filekey
						+ ".bnd");
				streams.put(filekey, stream);
				openStream(stream, true);
			} else if (stream.isOpen() == false) {
				openStream(stream, false);
			}
		}

		if (stream != null) {
			stream.lastAccessNo = ++lastAccessNo;
		}
		return stream;
	}

	private void writeDefaultInfos(OutputStream stream) throws IOException {
		DataOutputStream dos = new DataOutputStream(stream);
		dos.writeUTF("BND");
		dos.writeLong(System.currentTimeMillis());
		
		// write the header part 2
		// write it first to a byte array to be able to calculate the length of the header
		ByteArrayOutputStream headerStream = new ByteArrayOutputStream();
		DataOutputStream headerDataStream = new DataOutputStream(headerStream);
		headerDataStream.writeUTF(dataFormat);
		headerDataStream.writeInt(CURRENT_RECORD_ID);
		headerDataStream.writeUTF(Version.VERSION);
		headerDataStream.close();
		
		byte[] header2 = headerStream.toByteArray();
		// write the length of the header part 2 so that it is possible to add
		// values in the future
		dos.writeInt(header2.length);
		dos.write(header2);
		dos.flush();
	}

	private void saveToFile(String filekey, Boundary boundary) {
		try {
			StreamInfo streamInfo = getStream(filekey);
			if (streamInfo != null && streamInfo.isOpen()) {
				writeRawFormat(streamInfo.stream, boundary);
			}
		} catch (Exception exp) {
			log.error("Cannot write boundary: " + exp, exp);
		}

		tidyStreams();
	}

	/**
	 * Write a boundary to a given stream. 
	 * @param stream the already opened OutputStream
	 * @param boundary the boundary 
	 */
	private void writeRawFormat(OutputStream stream, Boundary boundary) {
		ByteArrayOutputStream oneItemStream = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(oneItemStream);
		if (dataFormat == QUADTREE_DATA_FORMAT) {
			log.error("wrong format for write, must use BoundaryQuadTree.save() ");
			System.exit(1);
		}
		try {
			dos.writeUTF(boundary.getId());
			
			// write the tags
			int noOfTags = boundary.getTags().size();
			dos.writeInt(noOfTags);

			Iterator<Entry<String, String>> tagIter = boundary.getTags()
					.entryIterator();
			while (tagIter.hasNext()) {
				Entry<String, String> tag = tagIter.next();
				dos.writeUTF(tag.getKey());
				dos.writeUTF(tag.getValue());
				noOfTags--;
			}
			assert noOfTags == 0 : "Remaining tags: " + noOfTags + " size: "
					+ boundary.getTags().size() + " "
					+ boundary.getTags().toString();

			writeArea(dos,boundary.getArea());
			dos.close();

			// now start to write into the real stream 

			// first write the bounding box so that is possible to skip the
			// complete entry
			uk.me.parabola.imgfmt.app.Area bbox = Java2DConverter
					.createBbox(boundary.getArea());
			DataOutputStream dOutStream = new DataOutputStream(stream);
			dOutStream.writeInt(bbox.getMinLat());
			dOutStream.writeInt(bbox.getMinLong());
			dOutStream.writeInt(bbox.getMaxLat());
			dOutStream.writeInt(bbox.getMaxLong());

			// write the size of the boundary block so that it is possible to
			// skip it
			byte[] data = oneItemStream.toByteArray();
			assert data.length > 0 : "bSize is not > 0 : " + data.length;
			dOutStream.writeInt(data.length);

			// write the boundary block
			dOutStream.write(data);
			dOutStream.flush();

		} catch (IOException exp) {
			log.error(exp.toString());
		}

	}

	/**
	 * Write area to stream with Double precision. The coordinates
	 * are saved as varying length doubles with delta coding. 
	 * @param dos the already opened DataOutputStream 
	 * @param area the area (can be non-singular)
	 * @throws IOException
	 */
	public static void writeArea(DataOutputStream dos, Area area) throws IOException{
		double[] res = new double[6];
		double[] lastRes = new double[2];
		List<Integer> pairs = new LinkedList<Integer>();
		// step 1: count parts
		PathIterator pit = area.getPathIterator(null);
		int prevType = -1;
		int len = 0;
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);
			if (type != PathIterator.SEG_LINETO && prevType == PathIterator.SEG_LINETO){
				pairs.add(len);
				len = 0;
			}
			if (type == PathIterator.SEG_LINETO)
				len++;
			prevType = type;
			pit.next();
		}
		
		// 2nd pass: write the data
		pit = area.getPathIterator(null);
		prevType = -1;
		
		dos.writeInt(pit.getWindingRule());
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);
			if (type != prevType)
				dos.writeInt(type);
			switch (type) {
			case PathIterator.SEG_LINETO:
				if (prevType != type){
					len = pairs.remove(0);
					dos.writeInt(len);
				}
				// no break
			case PathIterator.SEG_MOVETO: 
				len--;
				for (int i = 0; i < 2; i++){
					double delta = res[i] - lastRes[i];
					if (delta + lastRes[i] != res[i]){
						// handle rounding error in delta processing
						// write POSITIVE_INFINITY to signal that next value is
						// not delta coded
						//System.out.println("reset " + i ) ;
						writeVarDouble(dos, BoundarySaver.RESET_DELTA);
						delta = res[i];
					}
					lastRes[i] = res[i];
					writeVarDouble(dos, delta);
				}
				break;
			case PathIterator.SEG_CLOSE:
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}

			prevType = type;
			pit.next();
		}
		if (len != 0){
			log.error("len not zero " + len);
		}
		dos.writeInt(-1); // isDone flag
	}
	
	public uk.me.parabola.imgfmt.app.Area getBbox() {
		if (bbox == null) {
			bbox = new uk.me.parabola.imgfmt.app.Area(minLat, minLong, maxLat,
					maxLong);
			log.error("Calculate bbox to " + bbox);
		}
		return bbox;
	}

	public void setBbox(uk.me.parabola.imgfmt.app.Area bbox) {
		if (bbox.isEmpty()) {
			log.warn("Do not use bounding box because it's empty");
			this.bbox = null;
		} else {
			this.bbox = bbox;
			log.info("Set bbox: " + bbox.getMinLat() + " " + bbox.getMinLong()
					+ " " + bbox.getMaxLat() + " " + bbox.getMaxLong());
		}
	}

	public boolean isCreateEmptyFiles() {
		return createEmptyFiles;
	}

	/**
	 * Sets if empty bounds files should be created for areas without any
	 * boundary. Typically these are sea areas or areas not included in the OSM
	 * file.
	 * 
	 * @param createEmptyFiles
	 *            <code>true</code> create bounds files for uncovered areas;
	 *            <code>false</code> create bounds files only for areas
	 *            containing boundary information
	 */
	public void setCreateEmptyFiles(boolean createEmptyFiles) {
		this.createEmptyFiles = createEmptyFiles;
	}

	/**
	 * Write a varying length double. A typical double value requires only ~ 20 bits 
	 * (the left ones). As in o5m format we use the leftmost bit of a byte to signal
	 * that a further byte is to read, the remaining 7 bits are used to store the value.
	 * Many values are stored within 3 bytes, but some may require 10 bytes  
	 * (64 bits = 9*7 + 1) . We start with the highest bits of the long value that
	 * represents the double.
	 *    
	 * @param dos the already opened OutputStream
	 * @param val the double value to be written
	 * @throws IOException
	 */
	private static void writeVarDouble(OutputStream dos, double val) throws IOException{
		long v64 = Double.doubleToRawLongBits(val);
		if (v64 == 0){
			dos.write(0);
			return;
		}
		byte[] buffer = new byte[12];
		int numBytes = 0;
		while(v64 != 0){
			v64 = (v64 << 7) | (v64 >>> -7); // rotate left 7 bits
			buffer[numBytes++] = (byte)(v64 & 0x7f|0x80L);
			v64 &= 0xffffffffffffff80L;
		}
		
		buffer[numBytes-1] &= 0x7f;
		dos.write(buffer, 0, numBytes);
	}  

}
