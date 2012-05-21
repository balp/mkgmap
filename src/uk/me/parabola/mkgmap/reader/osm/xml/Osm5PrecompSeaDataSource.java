/*
 * Copyright (C) 2012.
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
package uk.me.parabola.mkgmap.reader.osm.xml;

import java.util.Collections;
import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.OsmReadingHooks;
import uk.me.parabola.mkgmap.reader.osm.PrecompSeaElementSaver;

public class Osm5PrecompSeaDataSource extends Osm5MapDataSource {

	private static final Set<String> coastlineTags = Collections.singleton("natural");
	
	protected void addBackground(boolean mapHasPolygon4B) {
		// do not add a background polygon
	}
	
	protected OsmReadingHooks[] getPossibleHooks() {
		// no hooks
		return new OsmReadingHooks[] {};
	}

	public Set<String> getUsedTags() {
		return coastlineTags;
	}

	protected void createElementSaver() {
		elementSaver = new PrecompSeaElementSaver(getConfig());
	}
}
