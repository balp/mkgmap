/*
 * Copyright (C) 2010, 2011.
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
package uk.me.parabola.mkgmap.filters;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.MapSplitter;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapShape;

/**
 * Split polygon so that it does not exceed the limits of a subdivision.  The plan
 * here is simple, if its too big, then cut it in half.  As we always cut the largest
 * dimension, then we will soon enough have cut it down to be small enough.
 *
 * @author WanMil
 */
public class PolygonSubdivSizeSplitterFilter extends PolygonSplitterBase implements MapFilter {
	private static final Logger log = Logger.getLogger(PolygonSubdivSizeSplitterFilter.class);

	private int shift;

	/**
	 * Get the scale factor so that we don't over split.
	 *
	 * @param config configuration information, giving parameters of the map level
	 * that is being produced through this filter.
	 */
	public void init(FilterConfig config) {
		shift = config.getShift();
		if (shift > 15)
			shift = 16;
	}

	/**
	 * Split up polygons that exceeds the limits of a subdivision.
	 *
	 * @param element A map element, only polygons will be processed.
	 * @param next	This is used to pass the possibly transformed element onward.
	 */
	public void doFilter(MapElement element, MapFilterChain next) {
		assert element instanceof MapShape;
		MapShape shape = (MapShape) element;

		int maxSize = MAX_SIZE << shift;
		if (isSizeOk(shape, maxSize)) {
			// This is ok let it through and return.
			next.doFilter(element);
			return;
		}

		List<MapShape> outputs = new ArrayList<MapShape>();

		// Do an initial split
		split(shape, outputs);

		// Now check that all the resulting parts are also small enough.
		// NOTE: the end condition is changed from within the loop.
		for (int i = 0; i < outputs.size(); i++) {
			MapShape s = outputs.get(i);
			if (!isSizeOk(s, maxSize)) {
				// Not small enough, so remove it and split it again.  The resulting
				// pieces will be placed at the end of the list and will be
				// picked up later on.
				outputs.set(i, null);
				split(s, outputs);
			}
		}

		// Now add all to the chain.
		boolean first = true;
		for (MapShape s : outputs) {
			if (s == null)
				continue;
			if (first) {
				first = false;
				next.doFilter(s);
			} else
				next.addElement(s);
		}
	}

	private boolean isSizeOk(MapShape shape, int maxSize) {
		// do not cut the background shape
		if (shape.getType() == 0x4a)
			return true;
		
		
		// Estimate the size taken by lines and shapes as a constant plus
		// a factor based on the number of points.
		int numPoints = shape.getPoints().size();
		int numElements = 1 + ((numPoints - 1) / PolygonSplitterFilter.MAX_POINT_IN_ELEMENT);
		int size =  numElements * 11 + numPoints * 4;
		
		if (shape.hasExtendedType()) {
			if (size > MapSplitter.MAX_XT_SHAPES_SIZE) {
				log.debug("XTSize larger than", MapSplitter.MAX_XT_SHAPES_SIZE);
				return false;
			}
		} else if (size > MapSplitter.MAX_RGN_SIZE) {
			log.debug("RGN Size larger than", MapSplitter.MAX_RGN_SIZE);
			return false;
		}
	
		return shape.getBounds().getMaxDimension() < Math.min(maxSize, 0x7fff);
	}

}
