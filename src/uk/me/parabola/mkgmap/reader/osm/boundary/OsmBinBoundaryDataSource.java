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

import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.MultiPolygonFinishHook;
import uk.me.parabola.mkgmap.reader.osm.OsmReadingHooks;
import uk.me.parabola.mkgmap.reader.osm.bin.OsmBinMapDataSource;
import uk.me.parabola.util.EnhancedProperties;

public class OsmBinBoundaryDataSource 
	extends OsmBinMapDataSource 
	implements LoadableBoundaryDataSource {

	private BoundarySaver saver;

	protected void addBackground(boolean mapHasPolygon4B) {
		// do not add a background polygon
	}

	protected OsmReadingHooks[] getPossibleHooks() {
		return new OsmReadingHooks[] { new MultiPolygonFinishHook() };
	}

	protected void createElementSaver() {
		elementSaver = new BoundaryElementSaver(getConfig(), saver);
	}

	public Set<String> getUsedTags() {
		// return null => all tags are used
		return null;
	}

	protected void createConverter() {
		converter = new BoundaryConverter(saver);
	}

	private final EnhancedProperties props = new EnhancedProperties();

	protected EnhancedProperties getConfig() {
		return props;
	}

	public void setBoundarySaver(BoundarySaver saver) {
		this.saver = saver;
	}

}
