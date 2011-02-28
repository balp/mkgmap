/*
 * Copyright (C) 2009.
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
package uk.me.parabola.mkgmap.combiners;

import java.io.IOException;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.mdxfmt.MdxFile;
import uk.me.parabola.mkgmap.CommandArgs;

/**
 * Create the mdx file which is basically just a list of maps in a set.
 * It is required for use with the global index file (mdr).
 * 
 * @author Steve Ratcliffe
 */
public class MdxBuilder implements Combiner {
	private MdxFile mdx;
	private String mdxFilename;
	private String outputDir;

	public void init(CommandArgs args) {
		int familyId = args.get("family-id", CommandArgs.DEFAULT_FAMILYID);
		int productId = args.get("product-id", 1);

		mdxFilename = args.get("overview-mapname", "osm") + ".mdx";
		mdx = new MdxFile(familyId, productId);
		
		outputDir = args.getOutputDir();
	}

	public void onMapEnd(FileInfo info) {
		if (!info.isImg())
			return;

		mdx.addMap(info.getMapnameAsInt(), info.getHexname(),
				info.getFilename());
	}

	public void onFinish() {
		try {
			mdx.write(Utils.joinPath(outputDir, mdxFilename));
		} catch (IOException e) {
			throw new ExitException("Could not create MDX file", e);
		}
	}
}
