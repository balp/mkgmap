/**
 * Copyright (C) 2006 Steve Ratcliffe
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Author: steve
 * Date: 22-Dec-2006
 */

package uk.me.parabola.imgfmt.app.trergn;

/**
 * A Polygon on a garmin map is pretty much treated like a line.
 *
 * @author Steve Ratcliffe
 */
public class Polygon extends Polyline {
	public Polygon(Subdivision div) {
		super(div);
	}
}
