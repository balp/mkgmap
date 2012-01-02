/*
 * Copyright (C) 2006, 2012.
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
package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.Locator;
import uk.me.parabola.mkgmap.build.LocatorUtil;
import uk.me.parabola.mkgmap.reader.osm.boundary.Boundary;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryUtil;
import uk.me.parabola.util.ElementQuadTree;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.GpxCreator;

public class LocationHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(LocationHook.class);

	private ElementSaver saver;
	private final List<String> nameTags = new ArrayList<String>();
	private Locator locator;
	private final Set<String> autofillOptions = new HashSet<String>();
	
	private File boundaryDir;

	private static final Pattern COMMA_OR_SEMICOLON_PATTERN = Pattern
			.compile("[,;]+");
	
	public static final String BOUNDS_OPTION = "bounds";
	
	private static File checkedBoundaryDir;
	private static boolean checkBoundaryDirOk;
	
	private final static Hashtable<String, String> mkgmapTags = new Hashtable<String, String>() {
		{
			put("admin_level=1", "mkgmap:admin_level1");
			put("admin_level=2", "mkgmap:admin_level2");
			put("admin_level=3", "mkgmap:admin_level3");
			put("admin_level=4", "mkgmap:admin_level4");
			put("admin_level=5", "mkgmap:admin_level5");
			put("admin_level=6", "mkgmap:admin_level6");
			put("admin_level=7", "mkgmap:admin_level7");
			put("admin_level=8", "mkgmap:admin_level8");
			put("admin_level=9", "mkgmap:admin_level9");
			put("admin_level=10", "mkgmap:admin_level10");
			put("admin_level=11", "mkgmap:admin_level11");
			put("postal_code", "mkgmap:postcode");
		}
	};

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		if (props.containsKey("index") == false) {
			log.info("Disable LocationHook because index option is not set.");
			return false;
		}

		this.locator = new Locator(props);
		
		this.saver = saver;

		autofillOptions.addAll(LocatorUtil.parseAutofillOption(props));

		if (autofillOptions.isEmpty()) {
			log.info("Disable LocationHook because no location-autofill option set.");
			return false;
		}
		
		nameTags.addAll(LocatorUtil.getNameTags(props));

		if (autofillOptions.contains(BOUNDS_OPTION)) {
			boundaryDir = new File(props.getProperty("bounds", "bounds"));
			long t1 = System.currentTimeMillis();

			synchronized (BOUNDS_OPTION) {
				// checking of the boundary dir is expensive
				// check once only and reuse the result
				if (boundaryDir.equals(checkedBoundaryDir)) {
					if (checkBoundaryDirOk == false) {
						log.error("Disable LocationHook because boundary directory is unusable. Dir: "+boundaryDir);
						return false;
					}
				} else {
					checkedBoundaryDir = boundaryDir;
					checkBoundaryDirOk = false;
					
					if (boundaryDir.exists() == false) {
						log.error("Disable LocationHook because boundary directory does not exist. Dir: "
								+ boundaryDir);
						return false;
					}
					
					// boundaryDir.list() is much quicker than boundaryDir.listFiles(FileFilter)
					String[] boundaryFiles = boundaryDir.list();
					if (boundaryFiles == null || boundaryFiles.length == 0) {
						log.error("Disable LocationHook because boundary directory contains no boundary files. Dir: "
								+ boundaryDir);
						return false;
					}
					boolean boundsFileFound = false;
					for (String boundsFile : boundaryFiles) {
						if (boundsFile.endsWith(".bnd")) {
							boundsFileFound = true;
							break;
						}
					}
					if (boundsFileFound == false) {
						log.error("Disable LocationHook because boundary directory contains no boundary files. Dir: "
								+ boundaryDir);
						return false;						
					}
					
					// passed all checks => boundaries are ok
					checkBoundaryDirOk = true;
				}
			}
			log.info("Checking bounds dir took "
					+ (System.currentTimeMillis() - t1) + " ms");
		}
		return true;
	}
	
	/**
	 * Retrieve a list of all elements for which the boundary assignment should be performed.
	 * @return a list of elements (points + ways + shapes)
	 */
	private List<Element> getLocationRelevantElements() {
		List<Element> elemList = new ArrayList<Element>(saver.getNodes().size()+saver.getWays().size());

		// add all nodes that might be converted to a garmin node (tagcount > 0)
		for (Node node : saver.getNodes().values()) {
			if (node.getTagCount() > 0) {
				elemList.add(node);
			}
		}

		// add all ways that might be converted to a garmin way (tagcount > 0)
		// and save all polygons that contains location information
		for (Way way : saver.getWays().values()) {
			if (way.getTagCount() > 0) {
				elemList.add(way);
			}
		}
		return elemList;
	}
	
	/**
	 * Loads the preprocessed boundaries that intersects the bounding box of the tile.
	 * The bounds are sorted in descending order of admin_levels.
	 * @return the preprocessed bounds
	 */
	private List<Boundary> loadBoundaries() {
		long tb1 = System.currentTimeMillis();
		// Load the boundaries that intersect with the bounding box of the tile
		List<Boundary> boundaries = BoundaryUtil.loadBoundaries(boundaryDir,
				saver.getBoundingBox());
		
		long tb2 = System.currentTimeMillis();
		log.info("Loading boundaries took "+(tb2-tb1)+" ms");
		
		// go through all boundaries, check if the necessary tags are available
		// and standardize the country name to the 3 letter ISO code
		ListIterator<Boundary> bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
			Boundary b = bIter.next();
			
			String name = getName(b.getTags());
			
			String zip =null;
			if (b.getTags().get("postal_code") != null || "postal_code".equals(b.getTags().get("boundary")))
				zip = getZip(b.getTags());
			
			if (name == null && zip == null) {
				log.warn("Cannot process boundary element because it contains no name and no zip tag. "+b.getTags());

				bIter.remove();
				continue;
			}

			if ("2".equals(b.getTags().get("admin_level"))) {
				String isoCode = locator.addCountry(b.getTags());
				if (isoCode != null) {
					name = isoCode;
				} else {
					log.warn("Country name",name,"not in locator config. Country may not be assigned correctly.");
				}
				log.debug("Coded:",name);
			}
			if (name != null)
				b.getTags().put("mkgmap:bname", name);
			
			if (zip != null)
				b.getTags().put("mkgmap:bzip", zip);
		}

		// Sort them by the admin level
		Collections.sort(boundaries, new BoundaryLevelCollator());
		// Reverse the sorting because we want to start with the highest admin level (11)
		Collections.reverse(boundaries);
		
		return boundaries;
	}
	
	private void assignPreprocBounds() {
		long t1 = System.currentTimeMillis();
		
		// create the quadtree
		List<Element> elemList = getLocationRelevantElements();
		log.info("Creating quadlist took "+(System.currentTimeMillis()-t1)+" ms");

		ElementQuadTree quadTree = new ElementQuadTree(saver.getBoundingBox(), elemList);
		elemList = null;

		long tb1 = System.currentTimeMillis();
		log.info("Creating quadtree took "+(tb1-t1)+" ms");
		
		List<Boundary> boundaries = loadBoundaries();
		
		if (boundaries.isEmpty()) {
			log.info("Do not continue with LocationHook because no valid boundaries are available.");
			return;
		}

//		log.info("Quadtree depth: "+quadTree.getDepth());
//		log.info("Quadtree coords: "+quadTree.getCoordSize());

		// Map the boundaryid to the boundary for fast access
		Map<String, Boundary> boundaryById = new HashMap<String, Boundary>();
		
		Set<String> availableLevels = new TreeSet<String>();
		for (Boundary b : boundaries) {
			boundaryById.put(b.getTags().get("mkgmap:boundaryid"), b);

			String admin_level = b.getTags().get("admin_level");
			String admMkgmapTag = mkgmapTags.get("admin_level=" + admin_level);
			String zipMkgmapTag = mkgmapTags.get("postal_code");

			String admName = b.getTags().get("mkgmap:bname");
			String zip = b.getTags().get("mkgmap:bzip");
			
			if (admName != null && admMkgmapTag != null) {
				availableLevels.add(admMkgmapTag);
			}
			if (zip != null && zipMkgmapTag!=null)
			{
				availableLevels.add(zipMkgmapTag);
			}
		}
		
		// put all available levels into a list with inverted sort order
		// this contains all levels that are not fully processed
		List<String> remainingLevels = new ArrayList<String>();
		if (availableLevels.contains(mkgmapTags.get("postal_code"))) {
			remainingLevels.add(mkgmapTags.get("postal_code"));
		}
		for (int level = 11; level >= 1; level--) {
			if (availableLevels.contains(mkgmapTags.get("admin_level="+level))) {
				remainingLevels.add(mkgmapTags.get("admin_level="+level));
			}			
		}		

		String currLevel = remainingLevels.remove(0);
		log.debug("First level:",currLevel);
		
		ListIterator<Boundary> bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
			Boundary boundary = bIter.next();
			String admin_level = boundary.getTags().get("admin_level");
			String admMkgmapTag = mkgmapTags.get("admin_level=" + admin_level);
			String zipMkgmapTag = mkgmapTags.get("postal_code");

			String admName = boundary.getTags().get("mkgmap:bname");
			String zip = boundary.getTags().get("mkgmap:bzip");
			
			if (admMkgmapTag == null && zip == null) {
				log.error("Cannot find any location relevant tag for " + boundary.getTags());
				continue;
			}

			// check if the list of remaining levels is still up to date
			while (((admName != null && currLevel.equals(admMkgmapTag)) || (zip != null && currLevel.equals(zipMkgmapTag)))==false) {
				if (log.isDebugEnabled()) {
					log.debug("Finish current level:",currLevel);
					log.debug("admname:",admName,"admMkgmapTag:",admMkgmapTag);
					log.debug("zip:",zip,"zipMkgmapTag:",zipMkgmapTag);
					log.debug("Next boundary:",boundary.getTags());
				}
				if (remainingLevels.isEmpty()) {
					log.error("All levels are finished. Remaining boundaries "+boundaries.size()+". Remaining coords: "+quadTree.getCoordSize());
					return;
				} else {
					currLevel = remainingLevels.remove(0);
					log.debug("Next level:",currLevel," Remaining:",remainingLevels);
				}
			}

			// defines which tags can be assigned by this boundary
			Map<String, String> boundarySetTags = new HashMap<String,String>();
			if (admName != null && admMkgmapTag != null) {
				boundarySetTags.put(admMkgmapTag, admName);
			}
			if (zip != null && zipMkgmapTag != null) {
				boundarySetTags.put(zipMkgmapTag, zip);
			}
			
			// check in which other boundaries this boundary lies in
			String liesIn = boundary.getTags().get("mkgmap:lies_in");
			if (liesIn != null) {
				// the common format of mkgmap:lies_in is:
				// mkgmap:lies_in=2:r19884;4:r20039;6:r998818
				String[] relBounds = liesIn.split(Pattern.quote(";"));
				for (String relBound : relBounds) {
					String[] relParts = relBound.split(Pattern.quote(":"));
					if (relParts.length != 2) {
						log.error("Wrong mkgmap:lies_in format. Value: " +liesIn);
						continue;
					}
					Boundary bAdditional = boundaryById.get(relParts[1]);
					if (bAdditional == null) {
						log.warn("Referenced boundary not available: "+boundary.getTags()+" refs "+relParts[1]);
						continue;
					}
					String addAdmin_level = bAdditional.getTags().get("admin_level");
					String addAdmMkgmapTag = mkgmapTags.get("admin_level=" + addAdmin_level);
					String addZipMkgmapTag = mkgmapTags.get("postal_code");

					String addAdmName = bAdditional.getTags().get("mkgmap:bname");
					String addZip = bAdditional.getTags().get("mkgmap:bzip");
					
					if (addAdmMkgmapTag != null
							&& addAdmName != null
							&& boundarySetTags.containsKey(addAdmMkgmapTag) == false) {
						boundarySetTags.put(addAdmMkgmapTag, addAdmName);
					}
					if (addZipMkgmapTag != null
							&& addZip != null
							&& boundarySetTags.containsKey(addZipMkgmapTag) == false) {
						boundarySetTags.put(addZipMkgmapTag, addZip);
					}
				}
			}
			
			// search for all elements in the boundary area
			Set<Element> elemsInLocation = quadTree.get(boundary.getArea());
			
			// create list of required tags that are not set by this boundary 
			List<String> requiredTags = new ArrayList<String>();
			for (String requiredTag : remainingLevels){
				if (boundarySetTags.containsKey(requiredTag) == false)
					requiredTags.add(requiredTag);
			}
			
			for (Element elem : elemsInLocation) {
				// tag the element with all tags referenced by the boundary
				for (Entry<String,String> bTag : boundarySetTags.entrySet()) {
					if (elem.getTag(bTag.getKey()) == null) {
						elem.addTag(bTag.getKey(), bTag.getValue());
						if (log.isDebugEnabled()) {
							log.debug("Add tag", admMkgmapTag, "=", admName, "to",
									elem.toTagString());
						}
					}
				}
				
				// check if the element is already tagged with all remaining boundary levels
				// in this case the element can be removed from further processing
				if (hasAllTags(elem, requiredTags)){
					if (log.isDebugEnabled()) {
						log.debug("Elem finish: "+elem.kind()+elem.getId()+" "+elem.toTagString());
					}
					quadTree.remove(elem);
				}
			}
			bIter.remove();
			
			if (quadTree.isEmpty()) {
				if (log.isInfoEnabled()) {
					log.info("Finish Location Hook: Remaining boundaries: "+boundaries.size());
				}
				return;
			}
		}
		if (log.isDebugEnabled()) {
			Collection<Element> unassigned =  quadTree.get(new Area(-90.0d, -180.0d, 90.0d, 180.0d));
			Set<Coord> unCoords = new HashSet<Coord>();
			for (Element e : unassigned) {
				log.debug(e.getId()+" "+e.toTagString());
				if (e instanceof Node)
					unCoords.add(((Node) e).getLocation());
				else if ( e instanceof Way) {
					unCoords.addAll(((Way) e).getPoints());
				}
			}
			GpxCreator.createGpx(GpxCreator.getGpxBaseName()+"unassigned", new ArrayList<Coord>(), new ArrayList<Coord>(unCoords));
			log.debug("Finish Location Hook. Unassigned elements: "+unassigned.size());
		}
	}
	
 	/**
	 * Check if all tags listed in tags are already set in this element.
 	 * @param element the OSM element
	 * @param tags a list of tags 
	 * @return true if all tags are set
 	 */
	private boolean hasAllTags(Element element, Collection<String> tags) {
		if (tags.isEmpty()) 
			return true;
		for (String locTag : tags) {
			if (element.getTag(locTag) == null) 
				return false;
 		}
		return true;
 	}

	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Starting with location hook");

		if (autofillOptions.contains(BOUNDS_OPTION)) {
			assignPreprocBounds();
		}

		long dt = (System.currentTimeMillis() - t1);
		log.info("Location hook finished in "+
				dt+ " ms");
	}


	/** 
	 * These tags are used to retrieve the name of admin_level=2 boundaries. They need to
	 * be handled special because their name is changed to the 3 letter ISO code using
	 * the Locator class and the LocatorConfig.xml file. 
	 */
	private static final String[] LEVEL2_NAMES = new String[]{"name","name:en","int_name"};
	
	private String getName(Tags tags) {
		if ("2".equals(tags.get("admin_level"))) {
			for (String enNameTag : LEVEL2_NAMES)
			{
				String nameTagValue = tags.get(enNameTag);
				if (nameTagValue == null) {
					continue;
				}

				String[] nameParts = COMMA_OR_SEMICOLON_PATTERN.split(nameTagValue);
				if (nameParts.length == 0) {
					continue;
				}
				return nameParts[0].trim().intern();
			}
		}
		
		for (String nameTag : nameTags) {
			String nameTagValue = tags.get(nameTag);
			if (nameTagValue == null) {
				continue;
			}

			String[] nameParts = COMMA_OR_SEMICOLON_PATTERN.split(nameTagValue);
			if (nameParts.length == 0) {
				continue;
			}
			return nameParts[0].trim().intern();
		}
		return null;
	}

	private String getZip(Tags tags) {
		String zip = tags.get("postal_code");
		if (zip == null) {
			String name = tags.get("name"); 
			if (name == null) {
				name = getName(tags);
			}
			if (name != null) {
				String[] nameParts = name.split(Pattern.quote(" "));
				if (nameParts.length > 0) {
					zip = nameParts[0].trim();
				}
			}
		}
		return zip;
	}

	public Set<String> getUsedTags() {
		Set<String> tags = new HashSet<String>();
		tags.add("boundary");
		tags.add("admin_level");
		tags.add("postal_code");
		tags.addAll(nameTags);
		return tags;
	}
	
	private class BoundaryLevelCollator implements Comparator<Boundary> {

		public int compare(Boundary o1, Boundary o2) {
			if (o1 == o2) {
				return 0;
			}

			String adminLevel1 = o1.getTags().get("admin_level");
			String adminLevel2 = o2.getTags().get("admin_level");

			if (getName(o1.getTags()) == null) {
				// admin_level tag is set but no valid name available
				adminLevel1= null;
			}
			if (getName(o2.getTags()) == null) {
				// admin_level tag is set but no valid name available
				adminLevel2= null;
			}
			
			int admCmp =  compareAdminLevels(adminLevel1, adminLevel2);
			if (admCmp != 0) {
				return admCmp;
			}
			
			if (getAdminLevel(adminLevel1) == 2) {
				// prefer countries that are known by the Locator
				String iso1 = locator.getCountryISOCode(o1.getTags());
				String iso2 = locator.getCountryISOCode(o2.getTags());
				if (iso1 != null && iso2 == null) {
					return 1;
				} else if (iso1==null && iso2 != null) {
					return -1;
				}
			}
			
			boolean post1set = getZip(o1.getTags()) != null;
			boolean post2set = getZip(o2.getTags()) != null;
			
			if (post1set) {
				return (post2set ? 0 : 1);
			} else {
				return (post2set ? -1 : 0);
			}
		}
		
		private static final int UNSET_ADMIN_LEVEL = 100;
		
		private int getAdminLevel(String level) {
			if (level == null) {
				return UNSET_ADMIN_LEVEL;
			}
			try {
				return Integer.valueOf(level);
			} catch (NumberFormatException nfe) {
				return UNSET_ADMIN_LEVEL;
			}
		}

		public int compareAdminLevels(String level1, String level2) {
			int l1 = getAdminLevel(level1);
			int l2 = getAdminLevel(level2);
			if (l1 == l2) {
				return 0;
			} else if (l1 > l2) {
				return 1;
			} else {
				return -1;
			}
		}
	}

}
