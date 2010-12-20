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
 * Create date: 02-Dec-2006
 */
package uk.me.parabola.imgfmt.fs;

import java.nio.channels.ByteChannel;

/**
 * An extension of ByteChannel that allows us to know the position.  It may
 * evolve to have several of the methods of the FileChannel class and plays
 * a similar role.
 * 
 * @author Steve Ratcliffe
 */
public interface ImgChannel extends  ByteChannel {

	/**
	 * Get the file position. Note that this is a logical position relative
	 * to the beginning of the file (the file within the .img file, not the
	 * beginning of the .img file itself).
	 *
	 * @return The offset in bytes from the beginning of the file.
	 */
	public long position();

	/**
	 * Set the position within the file.
	 *
	 * @param pos The position to set.
	 */
	public void position(long pos);
}
