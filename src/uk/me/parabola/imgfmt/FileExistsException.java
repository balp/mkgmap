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
package uk.me.parabola.imgfmt;

import java.io.IOException;

/**
 * Thrown when a file already exists and would be overwritten.
 * 
 * @author Steve Ratcliffe
 */
public class FileExistsException extends IOException {

	/**
	 * Constructs a new exception with the specified detail message.  The
	 * cause is not initialized, and may subsequently be initialized by
	 * a call to {@link #initCause}.
	 *
	 * @param message the detail message. The detail message is saved for
	 *                later retrieval by the {@link #getMessage()} method.
	 */
	public FileExistsException(String message) {
		super(message);
	}
}
