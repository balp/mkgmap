/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 01-Jan-2007
 */
package uk.me.parabola.imgfmt.app.trergn;

/**
 * Polygons just have a type (no subtype).
 * 
 * @author Steve Ratcliffe
 */
public class PolygonOverview extends Overview {

	public PolygonOverview(int type, int minResolution) {
		super(SHAPE_KIND, type, minResolution);
	}
}
