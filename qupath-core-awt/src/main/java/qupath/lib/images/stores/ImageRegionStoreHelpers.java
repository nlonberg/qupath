/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.stores;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

/**
 * Helper methods related to image region storage.
 * 
 * Provides a standard method of tiling an image, which are used by the viewer.
 * 
 * In cases where viewing tiles are cached, this makes it possible to find out
 * what the tile boundaries are... and perhaps adjust requests accordingly to match with cached tiles.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageRegionStoreHelpers {
	
	/**
	 * Given ImageServer, determine the boundaries of the image tiles that would be needed to construct 
	 * the image for a specified region.
	 * 
	 * @param server The ImageServer from which the tiles would be requested
	 * @param request The region for which all required tiles should be requested
	 * @param regions regions The list to which requests should be added, or null if a new list should be created
	 * @return The list of requests - identical to the one provided as an input parameter, unless this was null
	 */
	public static List<RegionRequest> getTilesToRequest(ImageServer<?> server, RegionRequest request, List<RegionRequest> regions) {
		return getTilesToRequest(server, AwtTools.getBounds(request), request.getDownsample(), request.getZ(), request.getT(), regions);
	}
	
	/**
	 * Given ImageServer, determine the boundaries of the image tiles that would be needed to paint
	 * a specified shape (defined by coordinates in the full-resolution image space).
	 * The downsampleFactor is used to determine the resolution at which to request the tiles.
	 * 
	 * @param server The ImageServer from which the tiles would be requested
	 * @param clipShape The requested shape, defined in the full-resolution image space
	 * @param downsampleFactor The downsampleFactor determining the resolution at which tiles should be requested
	 * @param zPosition The zPosition from which to request tiles
	 * @param tPosition The tPosition from which to request tiles
	 * @param regions regions The list to which requests should be added, or null if a new list should be created
	 * @return The list of requests - identical to the one provided as an input parameter, unless this was null
	 */
	public static List<RegionRequest> getTilesToRequest(ImageServer<?> server, Shape clipShape, double downsampleFactor, int zPosition, int tPosition, List<RegionRequest> regions) {
		return getTilesToRequest(server, clipShape, downsampleFactor, zPosition, tPosition, -1, -1, regions);
	}
	

	/**
	 * Given ImageServer, determine the boundaries of the image tiles that would be needed to paint
	 * a specified shape (defined by coordinates in the full-resolution image space).
	 * The downsampleFactor is used to determine the resolution at which to request the tiles.
	 * 
	 * @param server The ImageServer from which the tiles would be requested
	 * @param clipShape The requested shape, defined in the full-resolution image space
	 * @param downsampleFactor The downsampleFactor determining the resolution at which tiles should be requested
	 * @param zPosition The zPosition from which to request tiles
	 * @param tPosition The tPosition from which to request tiles
	 * @param tileWidth Specific tile width (overrides preferred width in the server if > 0)
	 * @param tileHeight Specific tile width (overrides preferred height in the server if > 0)
	 * @param regions regions The list to which requests should be added, or null if a new list should be created
	 * @return The list of requests - identical to the one provided as an input parameter, unless this was null
	 */
	public static List<RegionRequest> getTilesToRequest(ImageServer<?> server, Shape clipShape, double downsampleFactor, int zPosition, int tPosition,
			int tileWidth, int tileHeight, List<RegionRequest> regions) {

		if (regions == null)
			regions = new ArrayList<>();

		double downsamplePreferred = server.getPreferredDownsampleFactor(downsampleFactor);

		// Determine what the tile size will be in the original image space for the requested downsample
		// Aim for a round number - preferred downsamples can be a bit off due to rounding
		if (tileWidth <= 0)
			tileWidth = server.getPreferredTileWidth();
		if (tileHeight <= 0)
			tileHeight = server.getPreferredTileHeight();
		// If the preferred sizes are out of range, use defaults
		if (tileWidth <= 0)
			tileWidth = 256;
		if (tileHeight <= 0)
			tileHeight = 256;
		//		System.out.println("Tile size: " + tileWidth + ", " + tileHeight);
		int tileWidthForLevel;
		int tileHeightForLevel;
		if (GeneralTools.almostTheSame(downsamplePreferred, (int)(downsamplePreferred + .5), 0.001)) {
			tileWidthForLevel = (int)(tileWidth * (int)(downsamplePreferred + .5) + .5);
			tileHeightForLevel = (int)(tileHeight * (int)(downsamplePreferred + .5) + .5);
		}
		else {
			tileWidthForLevel = (int)(tileWidth * downsamplePreferred + .5);
			tileHeightForLevel = (int)(tileHeight * downsamplePreferred + .5);
		}

		// Get the current bounds
		//		Shape clipShapeVisible = getDisplayedClipShape(clip);
		Rectangle boundsVisible;
		if (clipShape instanceof Rectangle)
			boundsVisible = (Rectangle)clipShape;
		else
			boundsVisible = clipShape.getBounds();

		// Get the starting indices, shifted to actual tile boundaries
		int xStart = (int)Math.max(0, (int)(boundsVisible.x / tileWidthForLevel) * tileWidthForLevel);
		int yStart = (int)Math.max(0, (int)(boundsVisible.y / tileHeightForLevel) * tileHeightForLevel);
		// Determine the visible image dimensions at the current downsample
		double visibleWidth = boundsVisible.width;
		double visibleHeight = boundsVisible.height;

		int serverWidth = server.getWidth();
		int serverHeight = server.getHeight();

		// Get the ending image indices (non-inclusive), again shifted to actual tile boundaries or the image end
		int xEnd = (int)Math.min(serverWidth, Math.ceil(boundsVisible.x + visibleWidth));
		int yEnd = (int)Math.min(serverHeight, Math.ceil(boundsVisible.y + visibleHeight));
		
		// Try to ensure that we have at least one full tile
		if (serverWidth - xStart < tileWidthForLevel && serverWidth >= tileWidthForLevel)
			xStart = serverWidth - tileWidthForLevel;
		if (serverHeight - yStart < tileHeightForLevel && serverHeight >= tileHeightForLevel)
			yStart = serverHeight - tileHeightForLevel;

		//			// Loop through and create the tiles
		//			for (int yy = yStart; yy < yEnd; yy += tileHeightForLevel) {
		//				for (int xx = xStart; xx < xEnd; xx += tileWidthForLevel) {
		//					
		//					RegionRequest request = RegionRequest.createInstance(server.getPath(), downsamplePreferred, xx, yy, (int)Math.min(serverWidth, (xx+tileWidthForLevel))-xx,
		//							(int)Math.min(serverHeight, (yy+tileHeightForLevel))-yy, zPosition, tPosition);
		//					
		//					// Check if this is worth loading - might be outside the clip bounds
		//					if (clipShape != null && !clipShape.intersects(request.getBounds()))
		//						continue;
		//					
		//					// Add region to the list
		//					regions.add(request);
		//				}			
		//			}


		// Loop through and create the tile requests
		// Here, I've attempted to request central regions first in order to improve the perception of image loading
		int nx = (int)Math.ceil((double)(xEnd - xStart) / tileWidthForLevel);
		int ny = (int)Math.ceil((double)(yEnd - yStart) / tileHeightForLevel);

		int xc = nx/2;
		int yc = ny/2;
		int maxDisplacement = Math.max(nx - xc, ny - yc);

		for (int d = 0; d <= maxDisplacement; d++) {

			for (int yi = -d; yi <= d; yi++) {
				for (int xi = -d; xi <= d; xi++) {

					if ((Math.abs(xi) != d && Math.abs(yi) != d) || xc + xi < 0 || yc + yi < 0)
						continue;
					
					// Create central region
					int xx = xStart + (xc + xi) * tileWidthForLevel;
					int yy = yStart + (yc + yi) * tileHeightForLevel;

					int ww = tileWidthForLevel;
					int hh = tileHeightForLevel;
					
					// Check, if we have a partial tile - if so, then skip
					// Otherwise, if we have a tile that is right next to the image boundary, expand it to include the rest of the image
					int xRemainder = serverWidth - (xx + tileWidthForLevel);
					if (xRemainder < 0 && nx > 1) {
						continue;
					} else if (xRemainder < tileWidthForLevel) {
						ww = serverWidth - xx;
					}
					int yRemainder = serverHeight - (yy + tileHeightForLevel);
					if (yRemainder < 0 && ny > 1) {
						continue;
					} else if (yRemainder < tileHeightForLevel) {
						hh = serverHeight - yy;
					}

					RegionRequest request = RegionRequest.createInstance(server.getPath(), downsamplePreferred, xx, yy,
							ww, hh,
							zPosition, tPosition);

					// Check if this is worth loading - might be outside the clip bounds
					if (clipShape == null || clipShape.intersects(request.getX(), request.getY(), request.getWidth(), request.getHeight()))
						regions.add(request);
				}
			}

		}

		
		
//		String s = "";
//		Point2D pCenter = new Point2D.Double(clipShape.getBounds2D().getCenterX(), clipShape.getBounds2D().getCenterY());
//		for (RegionRequest region : regions) {
//			s += pCenter.distance(region.getBounds().getCenterX(), region.getBounds().getCenterY()) + ", ";
//		}
//		System.out.println(s);
		
//		System.out.println(regions.size() + ", " + new HashSet<>(regions).size());
		

		return regions;
	}

	/**
	 * Given a PathImageServer, determine the boundaries of the image tile that contains specified x, y coordinates.
	 * The downsampleFactor is used to determine the resolution at which to request the tiles.
	 * 
	 * @param server The PathImageServer from which the tiles would be requested
	 * @param clipShape The requested shape, defined in the full-resolution image space
	 * @param downsampleFactor The downsampleFactor determining the resolution at which tiles should be requested
	 * @param requests The list to which requests should be added, or null if a new list should be created
	 * @return The list of requests - identical to the one provided as an input parameter, unless this was null
	 */
	public static RegionRequest getTileRequest(ImageServer<BufferedImage> server, double x, double y, double downsampleFactor, int zPosition, int tPosition) {

		int serverWidth = server.getWidth();
		int serverHeight = server.getHeight();
		if (x < 0 || y < 0 || x >= serverWidth || y >= serverHeight)
			return null;

		double downsamplePreferred = server.getPreferredDownsampleFactor(downsampleFactor);

		// Determine what the tile size will be in the original image space for the requested downsample
		// Aim for a round number - preferred downsamples can be a bit off due to rounding
		int tileWidth = server.getPreferredTileWidth();
		int tileHeight = server.getPreferredTileHeight();
		if (tileWidth < 0)
			tileWidth = 256;
		if (tileHeight < 0)
			tileHeight = 256;
		//		System.out.println("Tile size: " + tileWidth + ", " + tileHeight);
		int tileWidthForLevel;
		int tileHeightForLevel;
		if (GeneralTools.almostTheSame(downsamplePreferred, (int)(downsamplePreferred + .5), 0.001)) {
			tileWidthForLevel = (int)(tileWidth * (int)(downsamplePreferred + .5) + .5);
			tileHeightForLevel = (int)(tileHeight * (int)(downsamplePreferred + .5) + .5);
		}
		else {
			tileWidthForLevel = (int)(tileWidth * downsamplePreferred + .5);
			tileHeightForLevel = (int)(tileHeight * downsamplePreferred + .5);
		}

		// Get the starting indices, shifted to actual tile boundaries
		int xx = (int)(x / tileWidthForLevel) * tileWidthForLevel;
		int yy = (int)(y / tileHeightForLevel) * tileHeightForLevel;

		RegionRequest request = RegionRequest.createInstance(server.getPath(), downsamplePreferred, xx, yy, (int)Math.min(serverWidth, (xx+tileWidthForLevel))-xx,
				(int)Math.min(serverHeight, (yy+tileHeightForLevel))-yy, zPosition, tPosition);
		return request;
	}

}