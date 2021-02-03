package imagescience.transform;

import imagescience.ImageScience;

import imagescience.image.Aspects;
import imagescience.image.Axes;
import imagescience.image.Borders;
import imagescience.image.ColorImage;
import imagescience.image.Coordinates;
import imagescience.image.Dimensions;
import imagescience.image.FloatImage;
import imagescience.image.Image;

import imagescience.shape.Point;

import imagescience.utility.FMath;
import imagescience.utility.Messenger;
import imagescience.utility.Progressor;
import imagescience.utility.Timer;

/** Affine transforms an image using different interpolation schemes.
	
	<p><b>References:</b></p>
	
	<p>[1] R. G. Keys, "Cubic Convolution Interpolation for Digital Image Processing", <em>IEEE Transactions on Acoustics, Speech, and Signal Processing</em>, vol. 29, no. 6, December 1981, pp. 1153-1160.</p>
	
	<p>[2] M. Unser, "Splines: A Perfect Fit for Signal and Image Processing", <em>IEEE Signal Processing Magazine</em>, vol. 16, no. 6, November 1999, pp. 22-38.</p>
	
	<p>[3] P. Thevenaz, T. Blu, M. Unser, "Interpolation Revisited", <em>IEEE Transactions on Medical Imaging</em>, vol. 19, no. 7, July 2000, pp.739-758.</p>
	
	<p>[4] E. Meijering, W. Niessen, M. Viergever, "Quantitative Evaluation of Convolution-Based Methods for Medical Image Interpolation", <em>Medical Image Analysis</em>, vol. 5, no. 2, June 2001, pp. 111-126.</p>
	
	<p>[5] T. Blu, P. Thevenaz, M. Unser, "MOMS: Maximal-Order Interpolation of Minimal Support", <em>IEEE Transactions on Image Processing</em>, vol. 10, no. 7, July 2001, pp. 1069-1080.</p>
*/
public class Affine {
	
	/** Nearest-neighbor interpolation. */
	public static final int NEAREST = 0;
	
	/** Linear interpolation. */
	public static final int LINEAR = 1;
	
	/** Cubic convolution interpolation [1]. */
	public static final int CUBIC = 2;
	
	/** Cubic B-spline interpolation [2,3,4]. */
	public static final int BSPLINE3 = 3;
	
	/** Cubic O-MOMS interpolation [5]. */
	public static final int OMOMS3 = 4;
	
	/** Quintic B-spline interpolation [2,3,4]. */
	public static final int BSPLINE5 = 5;
	
	/** Default constructor. */
	public Affine() { }
	
	/** Affine transforms an image.
		
		@param image The input image to be affine transformed. For images of type {@link ColorImage}, the color components are processed separately by the method.
		
		@param transform The affine transformation to be applied. The transformation is applied to every x-y-z subimage in a 5D image. The origin of the right-handed coordinate system in which the transformation is carried out is taken in the center of each subimage.
		
		@param interpolation The interpolation scheme to be used. Must be equal to one of the static fields of this class.
		
		@param adjust If {@code true}, the size of the output image is adjusted to fit the entire affine transformed image; if {@code false}, the size of the output image will be equal to that of the input image.
		
		@param resample If {@code true}, the output image is resampled isotropically, using as sampling interval the smallest of the x-, y-, and z-aspect sizes of the image; if {@code false}, the output image is sampled on the same grid as the input image.
		
		@param antialias If {@code true}, the method attempts to reduce the "stair-casing" effect at the transitions from image to background.
		
		@return A new image containing an affine transformed version of the input image. The returned image is of the same type as the input image.
		
		@throws IllegalArgumentException If {@code transform} is not invertible or if the requested {@code interpolation} scheme is not supported.
				
		@throws IllegalStateException If the x-, y-, or z-aspect size of {@code image} is less than or equal to {@code 0}.

		@throws NullPointerException If {@code image} or {@code transform} is {@code null}.
		
		@throws UnknownError If for any reason the output image can not be created. In most cases this will be due to insufficient free memory.
	*/
	public synchronized Image run(
		final Image image,
		final Transform transform,
		final int interpolation,
		final boolean adjust,
		final boolean resample,
		final boolean antialias
	) {
		
		messenger.log(ImageScience.prelude()+"Affine");
		
		// Initialize:
		final Timer timer = new Timer();
		timer.messenger.log(messenger.log());
		timer.start();
		
		initialize(image,transform,interpolation,adjust,resample,antialias);
		
		// Affine transform:
		messenger.log("Affine transforming "+image.type());
		Image affined = null;
		if (image instanceof ColorImage) {
			messenger.log("Processing RGB-color components separately");
			final ColorImage cimage = (ColorImage)image;
			
			progressor.range(0,0.33);
			component = " red component";
			messenger.log("Affine transforming"+component);
			cimage.component(ColorImage.RED);
			Image comp = cimage.get(); comp = affine(comp);
			final ColorImage caffined = new ColorImage(comp.dimensions());
			caffined.component(ColorImage.RED);
			caffined.set(comp);
			
			progressor.range(0.33,0.67);
			component = " green component";
			messenger.log("Affine transforming"+component);
			cimage.component(ColorImage.GREEN);
			comp = cimage.get(); comp = affine(comp);
			caffined.component(ColorImage.GREEN);
			caffined.set(comp);
			
			progressor.range(0.67,1);
			component = " blue component";
			messenger.log("Affine transforming"+component);
			cimage.component(ColorImage.BLUE);
			comp = cimage.get(); comp = affine(comp);
			caffined.component(ColorImage.BLUE);
			caffined.set(comp);
			
			affined = caffined;
			
		} else {
			component = "";
			progressor.range(0,1);
			affined = affine(image);
		}
		
		// Finish up:
		affined.name(image.name()+" affined");
		affined.aspects(new Aspects(ovx,ovy,ovz,image.aspects().t,image.aspects().c));
		timer.stop();
		
		return affined;
	}
	
	private Image affine(final Image image) {
		
		// Duplicate input image if no interpolation is needed:
		final boolean vxe = (ovx == ivx);
		final boolean vye = (ovy == ivy);
		final boolean vze = (ovz == ivz);
		if (fwd.identity() && vxe && vye && vze) {
			messenger.log("No interpolation is needed");
			messenger.log("Returning a copy of the input image");
			return image.duplicate();
		}
		
		// Make bordered version of input image:
		Image bordered = image;
		if (interpolation == LINEAR || interpolation == CUBIC) {
			messenger.log("Creating bordered copy of input image");
			bordered = image.border(borders);
		} else if (interpolation == BSPLINE3 || interpolation == OMOMS3 || interpolation == BSPLINE5) {
			messenger.log("Creating bordered floating-point copy of input image");
			bordered = new FloatImage(image,borders);
		}
		
		// Allocate memory for output image:
		messenger.log("Allocating memory for output image");
		final Image affined = Image.create(odims,image.type());
		
		// Transform using dedicated interpolation routine:
		switch (interpolation) {
			case NEAREST:
				if (doxy) affine_nearest_xy(bordered,affined);
				else affine_nearest_xyz(bordered,affined);
				break;
			case LINEAR:
				if (doxy) affine_linear_xy(bordered,affined);
				else affine_linear_xyz(bordered,affined);
				break;
			case CUBIC:
				if (doxy) affine_cubic_xy(bordered,affined);
				else affine_cubic_xyz(bordered,affined);
				break;
			case BSPLINE3:
				if (doxy) affine_bspline3_xy(bordered,affined);
				else affine_bspline3_xyz(bordered,affined);
				break;
			case OMOMS3:
				if (doxy) affine_omoms3_xy(bordered,affined);
				else affine_omoms3_xyz(bordered,affined);
				break;
			case BSPLINE5:
				if (doxy) affine_bspline5_xy(bordered,affined);
				else affine_bspline5_xyz(bordered,affined);
				break;
		}
		
		return affined;
	}
	
	private void initialize(
		final Image image,
		final Transform transform,
		final int interpolation,
		final boolean adjust,
		final boolean resample,
		final boolean antialias
	) {
		
		// Copy and adjust transform:
		fwd = transform.duplicate();
		
		if (adjust) {
			messenger.log("Adjusting output image dimensions to fit result");
			messenger.log("Therefore ignoring any translation components");
			fwd.axt = fwd.ayt = fwd.azt = 0;
		}
		
		messenger.log("Forward affine transformation matrix:");
		messenger.log(fwd.string());
		
		final double det = fwd.determinant();
		if (det == 0) throw new IllegalArgumentException("Non-invertible transformation matrix");
		bwd = fwd.duplicate();
		bwd.invert();
		
		messenger.log("Backward affine transformation matrix:");
		messenger.log(bwd.string());
		
		// Set voxel dimensions:
		ovx = ivx = image.aspects().x;
		ovy = ivy = image.aspects().y;
		ovz = ivz = image.aspects().z;
		if (ivx <= 0) throw new IllegalStateException("Aspect ratio in x-dimension less than or equal to 0");
		if (ivy <= 0) throw new IllegalStateException("Aspect ratio in y-dimension less than or equal to 0");
		if (ivz <= 0) throw new IllegalStateException("Aspect ratio in z-dimension less than or equal to 0");
		if (resample) {
			double vmin = (ivx < ivy) ? ivx : ivy;
			vmin = (ivz < vmin) ? ivz : vmin;
			ovx = ovy = ovz = vmin;
		}
		
		// Compute center of input image:
		idims = image.dimensions();
		ipmax = new Point(idims.x-1,idims.y-1,idims.z-1);
		ipc = new Point(0.5*ivx*ipmax.x,0.5*ivy*ipmax.y,0.5*ivz*ipmax.z);
		
		// Compute size and center of transformed image:
		final Point pmin = new Point(-0.5*ovx,-0.5*ovy,-0.5*ovz);
		final Point pmax = new Point((ipmax.x+0.5)*ovx,(ipmax.y+0.5)*ovy,(ipmax.z+0.5)*ovz);
		
		if (adjust) {
			// Compute transformed positions of eight corner points:
			final Point p1 = new Point(-ipc.x-0.5*ivx,-ipc.y-0.5*ivy,-ipc.z-0.5*ivz);
			final Point p2 = new Point(-p1.x,p1.y,p1.z);
			final Point p3 = new Point(p1.x,-p1.y,p1.z);
			final Point p4 = new Point(-p1.x,-p1.y,p1.z);
			final Point p5 = new Point(p1.x,p1.y,-p1.z);
			final Point p6 = new Point(-p1.x,p1.y,-p1.z);
			final Point p7 = new Point(p1.x,-p1.y,-p1.z);
			final Point p8 = new Point(-p1.x,-p1.y,-p1.z);
			
			// Use forward transformation matrix:
			fwd.transform(p1); fwd.transform(p2); fwd.transform(p3); fwd.transform(p4);
			fwd.transform(p5); fwd.transform(p6); fwd.transform(p7); fwd.transform(p8);
			
			pmin.x = FMath.min(p1.x,p2.x,p3.x,p4.x,p5.x,p6.x,p7.x,p8.x);
			pmin.y = FMath.min(p1.y,p2.y,p3.y,p4.y,p5.y,p6.y,p7.y,p8.y);
			pmin.z = FMath.min(p1.z,p2.z,p3.z,p4.z,p5.z,p6.z,p7.z,p8.z);
			
			pmax.x = FMath.max(p1.x,p2.x,p3.x,p4.x,p5.x,p6.x,p7.x,p8.x);
			pmax.y = FMath.max(p1.y,p2.y,p3.y,p4.y,p5.y,p6.y,p7.y,p8.y);
			pmax.z = FMath.max(p1.z,p2.z,p3.z,p4.z,p5.z,p6.z,p7.z,p8.z);
			
		} else {
			messenger.log("Not adjusting image dimensions");
		}
		
		int odimsx = FMath.round((pmax.x - pmin.x)/ovx); if (odimsx < 1) odimsx = 1;
		int odimsy = FMath.round((pmax.y - pmin.y)/ovy); if (odimsy < 1) odimsy = 1;
		int odimsz = FMath.round((pmax.z - pmin.z)/ovz); if (odimsz < 1) odimsz = 1;
		odims = new Dimensions(odimsx,odimsy,odimsz,idims.t,idims.c);
		opc = new Point(0.5*ovx*(odims.x-1),0.5*ovy*(odims.y-1),0.5*ovz*(odims.z-1));
		
		messenger.log("Input image dimensions: (x,y,z,t,c) = ("+idims.x+","+idims.y+","+idims.z+","+idims.t+","+idims.c+")");
		messenger.log("Output image dimensions: (x,y,z,t,c) = ("+odims.x+","+odims.y+","+odims.z+","+odims.t+","+odims.c+")");
		
		messenger.log("Resampling output image on "+(resample?"isotropic":"input")+" grid");
		messenger.log("Input voxel dimensions: (x,y,z) = ("+ivx+","+ivy+","+ivz+")");
		messenger.log("Output voxel dimensions: (x,y,z) = ("+ovx+","+ovy+","+ovz+")");
		
		// Store anti-alias choice:
		this.antialias = antialias;
		if (antialias) messenger.log("Anti-aliasing image-background transitions");
		
		// Check if requested type of interpolation is applicable:
		messenger.log("Selecting "+schemes(interpolation));
		if (interpolation < NEAREST || interpolation > BSPLINE5)
			throw new IllegalArgumentException("Non-supported interpolation scheme");
		this.interpolation = interpolation;
		
		// Show background filling value:
		messenger.log("Background filling with value "+background);
		
		// Set borders based on interpolation and transformation:
		int b = 0;
		switch (interpolation) {
			case NEAREST: b = 0; break;
			case LINEAR: b = 1; break;
			case CUBIC: b = 2; break;
			case BSPLINE3: b = 2; break;
			case OMOMS3: b = 2; break;
			case BSPLINE5: b = 3; break;
		}
		doxy = (fwd.azx == 0 && fwd.azy == 0 && fwd.azz == 1 && fwd.azt == 0 && ovz == ivz);
		if (doxy) borders = new Borders(b,b,0);
		else borders = new Borders(b,b,b);
	}
	
	private void affine_nearest_xy(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Nearest-neighbor sampling in x-y");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							ci.x = FMath.round((ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx);
							ci.y = FMath.round((ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy);
							if (ci.x < 0 || ci.x > ipmax.x ||
								ci.y < 0 || ci.y > ipmax.y) {
								row[x] = background;
							} else {
								row[x] = image.get(ci);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_nearest_xyz(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Nearest-neighbor sampling in x-y-z");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		
		// Affine transform using the backward transformation matrix: (note that
		// when this method is called, the border size of image is 0)
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							ci.x = FMath.round((ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx);
							ci.y = FMath.round((ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy);
							ci.z = FMath.round((ipc.z + dx*bwd.azx + dy*bwd.azy + dz*bwd.azz + bwd.azt)/ivz);
							if (ci.x < 0 || ci.x > ipmax.x ||
								ci.y < 0 || ci.y > ipmax.y ||
								ci.z < 0 || ci.z > ipmax.z) {
								row[x] = background;
							} else {
								row[x] = image.get(ci);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_linear_xy(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Linear sampling in x-y");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double ydiff = ty - iy;
								final double xmdiff = 1 - xdiff;
								final double ymdiff = 1 - ydiff;
								ci.x = borders.x + ix;
								ci.y = borders.y + iy;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.y;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci);
								row[x] = (
									ymdiff*xmdiff*in00 +
									ymdiff*xdiff*in01 +
									ydiff*xmdiff*in10 +
									ydiff*xdiff*in11
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_linear_xyz(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Linear sampling in x-y-z");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final double tz = (ipc.z + dx*bwd.azx + dy*bwd.azy + dz*bwd.azz + bwd.azt)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y ||
								tz < -1 || iz > ipmax.z) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double ydiff = ty - iy;
								final double zdiff = tz - iz;
								final double xmdiff = 1 - xdiff;
								final double ymdiff = 1 - ydiff;
								final double zmdiff = 1 - zdiff;
								ci.x = borders.x + ix;
								ci.y = borders.y + iy;
								ci.z = borders.z + iz;
								final double in000 = image.get(ci); ++ci.x;
								final double in001 = image.get(ci); ++ci.y;
								final double in011 = image.get(ci); --ci.x;
								final double in010 = image.get(ci); ++ci.z;
								final double in110 = image.get(ci); ++ci.x;
								final double in111 = image.get(ci); --ci.y;
								final double in101 = image.get(ci); --ci.x;
								final double in100 = image.get(ci);
								row[x] = (
									zmdiff*ymdiff*xmdiff*in000 +
									zmdiff*ymdiff*xdiff*in001 +
									zmdiff*ydiff*xmdiff*in010 +
									zmdiff*ydiff*xdiff*in011 +
									zdiff*ymdiff*xmdiff*in100 +
									zdiff*ymdiff*xdiff*in101 +
									zdiff*ydiff*xmdiff*in110 +
									zdiff*ydiff*xdiff*in111
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_cubic_xy(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Cubic convolution sampling in x-y");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = DM1O2*xdiff*xmdiff*xmdiff;
								final double wx00 = 1 + (D3O2*xdiff - D5O2)*xdiff*xdiff;
								final double wxp1 = 1 + (D3O2*xmdiff - D5O2)*xmdiff*xmdiff;
								final double wxp2 = DM1O2*xmdiff*xdiff*xdiff;
								final double ydiff = ty - iy;
								final double ymdiff = 1 - ydiff;
								final double wym1 = DM1O2*ydiff*ymdiff*ymdiff;
								final double wy00 = 1 + (D3O2*ydiff - D5O2)*ydiff*ydiff;
								final double wyp1 = 1 + (D3O2*ymdiff - D5O2)*ymdiff*ymdiff;
								final double wyp2 = DM1O2*ymdiff*ydiff*ydiff;
								ci.x = borders.x + ix - 1;
								ci.y = borders.y + iy - 1;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.y;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.y;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.y;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci);
								row[x] = (
									wym1*(wxm1*in00 + wx00*in01 + wxp1*in02 + wxp2*in03) +
									wy00*(wxm1*in10 + wx00*in11 + wxp1*in12 + wxp2*in13) +
									wyp1*(wxm1*in20 + wx00*in21 + wxp1*in22 + wxp2*in23) +
									wyp2*(wxm1*in30 + wx00*in31 + wxp1*in32 + wxp2*in33)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_cubic_xyz(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Cubic convolution sampling in x-y-z");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final double tz = (ipc.z + dx*bwd.azx + dy*bwd.azy + dz*bwd.azz + bwd.azt)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y ||
								tz < -1 || iz > ipmax.z) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = DM1O2*xdiff*xmdiff*xmdiff;
								final double wx00 = 1 + (D3O2*xdiff - D5O2)*xdiff*xdiff;
								final double wxp1 = 1 + (D3O2*xmdiff - D5O2)*xmdiff*xmdiff;
								final double wxp2 = DM1O2*xmdiff*xdiff*xdiff;
								final double ydiff = ty - iy;
								final double ymdiff = 1 - ydiff;
								final double wym1 = DM1O2*ydiff*ymdiff*ymdiff;
								final double wy00 = 1 + (D3O2*ydiff - D5O2)*ydiff*ydiff;
								final double wyp1 = 1 + (D3O2*ymdiff - D5O2)*ymdiff*ymdiff;
								final double wyp2 = DM1O2*ymdiff*ydiff*ydiff;
								final double zdiff = tz - iz;
								final double zmdiff = 1 - zdiff;
								final double wzm1 = DM1O2*zdiff*zmdiff*zmdiff;
								final double wz00 = 1 + (D3O2*zdiff - D5O2)*zdiff*zdiff;
								final double wzp1 = 1 + (D3O2*zmdiff - D5O2)*zmdiff*zmdiff;
								final double wzp2 = DM1O2*zmdiff*zdiff*zdiff;
								ci.x = borders.x + ix - 1;
								ci.y = borders.y + iy - 1;
								ci.z = borders.z + iz - 1;
								final double in000 = image.get(ci); ++ci.x;
								final double in001 = image.get(ci); ++ci.x;
								final double in002 = image.get(ci); ++ci.x;
								final double in003 = image.get(ci); ++ci.y;
								final double in013 = image.get(ci); --ci.x;
								final double in012 = image.get(ci); --ci.x;
								final double in011 = image.get(ci); --ci.x;
								final double in010 = image.get(ci); ++ci.y;
								final double in020 = image.get(ci); ++ci.x;
								final double in021 = image.get(ci); ++ci.x;
								final double in022 = image.get(ci); ++ci.x;
								final double in023 = image.get(ci); ++ci.y;
								final double in033 = image.get(ci); --ci.x;
								final double in032 = image.get(ci); --ci.x;
								final double in031 = image.get(ci); --ci.x;
								final double in030 = image.get(ci); ++ci.z;
								final double in130 = image.get(ci); ++ci.x;
								final double in131 = image.get(ci); ++ci.x;
								final double in132 = image.get(ci); ++ci.x;
								final double in133 = image.get(ci); --ci.y;
								final double in123 = image.get(ci); --ci.x;
								final double in122 = image.get(ci); --ci.x;
								final double in121 = image.get(ci); --ci.x;
								final double in120 = image.get(ci); --ci.y;
								final double in110 = image.get(ci); ++ci.x;
								final double in111 = image.get(ci); ++ci.x;
								final double in112 = image.get(ci); ++ci.x;
								final double in113 = image.get(ci); --ci.y;
								final double in103 = image.get(ci); --ci.x;
								final double in102 = image.get(ci); --ci.x;
								final double in101 = image.get(ci); --ci.x;
								final double in100 = image.get(ci); ++ci.z;
								final double in200 = image.get(ci); ++ci.x;
								final double in201 = image.get(ci); ++ci.x;
								final double in202 = image.get(ci); ++ci.x;
								final double in203 = image.get(ci); ++ci.y;
								final double in213 = image.get(ci); --ci.x;
								final double in212 = image.get(ci); --ci.x;
								final double in211 = image.get(ci); --ci.x;
								final double in220 = image.get(ci); ++ci.x;
								final double in210 = image.get(ci); ++ci.y;
								final double in221 = image.get(ci); ++ci.x;
								final double in222 = image.get(ci); ++ci.x;
								final double in223 = image.get(ci); ++ci.y;
								final double in233 = image.get(ci); --ci.x;
								final double in232 = image.get(ci); --ci.x;
								final double in231 = image.get(ci); --ci.x;
								final double in230 = image.get(ci); ++ci.z;
								final double in330 = image.get(ci); ++ci.x;
								final double in331 = image.get(ci); ++ci.x;
								final double in332 = image.get(ci); ++ci.x;
								final double in333 = image.get(ci); --ci.y;
								final double in323 = image.get(ci); --ci.x;
								final double in322 = image.get(ci); --ci.x;
								final double in321 = image.get(ci); --ci.x;
								final double in320 = image.get(ci); --ci.y;
								final double in310 = image.get(ci); ++ci.x;
								final double in311 = image.get(ci); ++ci.x;
								final double in312 = image.get(ci); ++ci.x;
								final double in313 = image.get(ci); --ci.y;
								final double in303 = image.get(ci); --ci.x;
								final double in302 = image.get(ci); --ci.x;
								final double in301 = image.get(ci); --ci.x;
								final double in300 = image.get(ci);
								row[x] = (
									wzm1*(
										wym1*(wxm1*in000 + wx00*in001 + wxp1*in002 + wxp2*in003) +
										wy00*(wxm1*in010 + wx00*in011 + wxp1*in012 + wxp2*in013) +
										wyp1*(wxm1*in020 + wx00*in021 + wxp1*in022 + wxp2*in023) +
										wyp2*(wxm1*in030 + wx00*in031 + wxp1*in032 + wxp2*in033)
									) +
									wz00*(
										wym1*(wxm1*in100 + wx00*in101 + wxp1*in102 + wxp2*in103) +
										wy00*(wxm1*in110 + wx00*in111 + wxp1*in112 + wxp2*in113) +
										wyp1*(wxm1*in120 + wx00*in121 + wxp1*in122 + wxp2*in123) +
										wyp2*(wxm1*in130 + wx00*in131 + wxp1*in132 + wxp2*in133)
									) +
									wzp1*(
										wym1*(wxm1*in200 + wx00*in201 + wxp1*in202 + wxp2*in203) +
										wy00*(wxm1*in210 + wx00*in211 + wxp1*in212 + wxp2*in213) +
										wyp1*(wxm1*in220 + wx00*in221 + wxp1*in222 + wxp2*in223) +
										wyp2*(wxm1*in230 + wx00*in231 + wxp1*in232 + wxp2*in233)
									) +
									wzp2*(
										wym1*(wxm1*in300 + wx00*in301 + wxp1*in302 + wxp2*in303) +
										wy00*(wxm1*in310 + wx00*in311 + wxp1*in312 + wxp2*in313) +
										wyp1*(wxm1*in320 + wx00*in321 + wxp1*in322 + wxp2*in323) +
										wyp2*(wxm1*in330 + wx00*in331 + wxp1*in332 + wxp2*in333)
									)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_bspline3_xy(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Cubic B-spline prefiltering and sampling in x-y");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline3(image,new Axes(true,true,false),borders);
		
		if (antialias) {
			// If any of the dimensions equals 1, the prefiltering operation
			// will not have been carried out in that dimension. Subsequent
			// application of the cubic B-spline kernel in that dimension will
			// result in an overall down-scaling of the grey-values, which
			// should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE3X0;
			if (idims.y == 1) scale /= BSPLINE3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
		}
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = D1O6*xmdiff*xmdiff*xmdiff;
								final double wx00 = D2O3 + (D1O2*xdiff - 1)*xdiff*xdiff;
								final double wxp1 = D2O3 + (D1O2*xmdiff - 1)*xmdiff*xmdiff;
								final double wxp2 = D1O6*xdiff*xdiff*xdiff;
								final double ydiff = ty - iy;
								final double ymdiff = 1 - ydiff;
								final double wym1 = D1O6*ymdiff*ymdiff*ymdiff;
								final double wy00 = D2O3 + (D1O2*ydiff - 1)*ydiff*ydiff;
								final double wyp1 = D2O3 + (D1O2*ymdiff - 1)*ymdiff*ymdiff;
								final double wyp2 = D1O6*ydiff*ydiff*ydiff;
								ci.x = borders.x + ix - 1;
								ci.y = borders.y + iy - 1;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.y;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.y;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.y;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci);
								row[x] = (
									wym1*(wxm1*in00 + wx00*in01 + wxp1*in02 + wxp2*in03) +
									wy00*(wxm1*in10 + wx00*in11 + wxp1*in12 + wxp2*in13) +
									wyp1*(wxm1*in20 + wx00*in21 + wxp1*in22 + wxp2*in23) +
									wyp2*(wxm1*in30 + wx00*in31 + wxp1*in32 + wxp2*in33)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_bspline3_xyz(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Cubic B-spline prefiltering and sampling in x-y-z");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline3(image,new Axes(true,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals 1, the prefiltering operation
			// will not have been carried out in that dimension.  Subsequent
			// application of the cubic B-spline kernel in that dimension will
			// result in an overall down-scaling of the grey-values, which
			// should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE3X0;
			if (idims.y == 1) scale /= BSPLINE3X0;
			if (idims.z == 1) scale /= BSPLINE3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
		}
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final double tz = (ipc.z + dx*bwd.azx + dy*bwd.azy + dz*bwd.azz + bwd.azt)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y ||
								tz < -1 || iz > ipmax.z) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = D1O6*xmdiff*xmdiff*xmdiff;
								final double wx00 = D2O3 + (D1O2*xdiff - 1)*xdiff*xdiff;
								final double wxp1 = D2O3 + (D1O2*xmdiff - 1)*xmdiff*xmdiff;
								final double wxp2 = D1O6*xdiff*xdiff*xdiff;
								final double ydiff = ty - iy;
								final double ymdiff = 1 - ydiff;
								final double wym1 = D1O6*ymdiff*ymdiff*ymdiff;
								final double wy00 = D2O3 + (D1O2*ydiff - 1)*ydiff*ydiff;
								final double wyp1 = D2O3 + (D1O2*ymdiff - 1)*ymdiff*ymdiff;
								final double wyp2 = D1O6*ydiff*ydiff*ydiff;
								final double zdiff = tz - iz;
								final double zmdiff = 1 - zdiff;
								final double wzm1 = D1O6*zmdiff*zmdiff*zmdiff;
								final double wz00 = D2O3 + (D1O2*zdiff - 1)*zdiff*zdiff;
								final double wzp1 = D2O3 + (D1O2*zmdiff - 1)*zmdiff*zmdiff;
								final double wzp2 = D1O6*zdiff*zdiff*zdiff;
								ci.x = borders.x + ix - 1;
								ci.y = borders.y + iy - 1;
								ci.z = borders.z + iz - 1;
								final double in000 = image.get(ci); ++ci.x;
								final double in001 = image.get(ci); ++ci.x;
								final double in002 = image.get(ci); ++ci.x;
								final double in003 = image.get(ci); ++ci.y;
								final double in013 = image.get(ci); --ci.x;
								final double in012 = image.get(ci); --ci.x;
								final double in011 = image.get(ci); --ci.x;
								final double in010 = image.get(ci); ++ci.y;
								final double in020 = image.get(ci); ++ci.x;
								final double in021 = image.get(ci); ++ci.x;
								final double in022 = image.get(ci); ++ci.x;
								final double in023 = image.get(ci); ++ci.y;
								final double in033 = image.get(ci); --ci.x;
								final double in032 = image.get(ci); --ci.x;
								final double in031 = image.get(ci); --ci.x;
								final double in030 = image.get(ci); ++ci.z;
								final double in130 = image.get(ci); ++ci.x;
								final double in131 = image.get(ci); ++ci.x;
								final double in132 = image.get(ci); ++ci.x;
								final double in133 = image.get(ci); --ci.y;
								final double in123 = image.get(ci); --ci.x;
								final double in122 = image.get(ci); --ci.x;
								final double in121 = image.get(ci); --ci.x;
								final double in120 = image.get(ci); --ci.y;
								final double in110 = image.get(ci); ++ci.x;
								final double in111 = image.get(ci); ++ci.x;
								final double in112 = image.get(ci); ++ci.x;
								final double in113 = image.get(ci); --ci.y;
								final double in103 = image.get(ci); --ci.x;
								final double in102 = image.get(ci); --ci.x;
								final double in101 = image.get(ci); --ci.x;
								final double in100 = image.get(ci); ++ci.z;
								final double in200 = image.get(ci); ++ci.x;
								final double in201 = image.get(ci); ++ci.x;
								final double in202 = image.get(ci); ++ci.x;
								final double in203 = image.get(ci); ++ci.y;
								final double in213 = image.get(ci); --ci.x;
								final double in212 = image.get(ci); --ci.x;
								final double in211 = image.get(ci); --ci.x;
								final double in210 = image.get(ci); ++ci.y;
								final double in220 = image.get(ci); ++ci.x;
								final double in221 = image.get(ci); ++ci.x;
								final double in222 = image.get(ci); ++ci.x;
								final double in223 = image.get(ci); ++ci.y;
								final double in233 = image.get(ci); --ci.x;
								final double in232 = image.get(ci); --ci.x;
								final double in231 = image.get(ci); --ci.x;
								final double in230 = image.get(ci); ++ci.z;
								final double in330 = image.get(ci); ++ci.x;
								final double in331 = image.get(ci); ++ci.x;
								final double in332 = image.get(ci); ++ci.x;
								final double in333 = image.get(ci); --ci.y;
								final double in323 = image.get(ci); --ci.x;
								final double in322 = image.get(ci); --ci.x;
								final double in321 = image.get(ci); --ci.x;
								final double in320 = image.get(ci); --ci.y;
								final double in310 = image.get(ci); ++ci.x;
								final double in311 = image.get(ci); ++ci.x;
								final double in312 = image.get(ci); ++ci.x;
								final double in313 = image.get(ci); --ci.y;
								final double in303 = image.get(ci); --ci.x;
								final double in302 = image.get(ci); --ci.x;
								final double in301 = image.get(ci); --ci.x;
								final double in300 = image.get(ci);
								row[x] = (
									wzm1*(
										wym1*(wxm1*in000 + wx00*in001 + wxp1*in002 + wxp2*in003) +
										wy00*(wxm1*in010 + wx00*in011 + wxp1*in012 + wxp2*in013) +
										wyp1*(wxm1*in020 + wx00*in021 + wxp1*in022 + wxp2*in023) +
										wyp2*(wxm1*in030 + wx00*in031 + wxp1*in032 + wxp2*in033)
									) +
									wz00*(
										wym1*(wxm1*in100 + wx00*in101 + wxp1*in102 + wxp2*in103) +
										wy00*(wxm1*in110 + wx00*in111 + wxp1*in112 + wxp2*in113) +
										wyp1*(wxm1*in120 + wx00*in121 + wxp1*in122 + wxp2*in123) +
										wyp2*(wxm1*in130 + wx00*in131 + wxp1*in132 + wxp2*in133)
									) +
									wzp1*(
										wym1*(wxm1*in200 + wx00*in201 + wxp1*in202 + wxp2*in203) +
										wy00*(wxm1*in210 + wx00*in211 + wxp1*in212 + wxp2*in213) +
										wyp1*(wxm1*in220 + wx00*in221 + wxp1*in222 + wxp2*in223) +
										wyp2*(wxm1*in230 + wx00*in231 + wxp1*in232 + wxp2*in233)
									) +
									wzp2*(
										wym1*(wxm1*in300 + wx00*in301 + wxp1*in302 + wxp2*in303) +
										wy00*(wxm1*in310 + wx00*in311 + wxp1*in312 + wxp2*in313) +
										wyp1*(wxm1*in320 + wx00*in321 + wxp1*in322 + wxp2*in323) +
										wyp2*(wxm1*in330 + wx00*in331 + wxp1*in332 + wxp2*in333)
									)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_omoms3_xy(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Cubic O-MOMS prefiltering and sampling in x-y");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.omoms3(image,new Axes(true,true,false),borders);
		
		if (antialias) {
			// If any of the dimensions equals 1, the prefiltering operation
			// will not have been carried out in that dimension. Subsequent
			// application of the cubic O-MOMS kernel in that dimension will
			// result in an overall down-scaling of the grey-values, which
			// should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= OMOMS3X0;
			if (idims.y == 1) scale /= OMOMS3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
		}
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = xmdiff*(D1O42 + D1O6*xmdiff*xmdiff);
								final double wx00 = D13O21 + xdiff*(D1O14 + xdiff*(D1O2*xdiff - 1));
								final double wxp1 = D13O21 + xmdiff*(D1O14 + xmdiff*(D1O2*xmdiff - 1));
								final double wxp2 = xdiff*(D1O42 + D1O6*xdiff*xdiff);
								final double ydiff = ty - iy;
								final double ymdiff = 1 - ydiff;
								final double wym1 = ymdiff*(D1O42 + D1O6*ymdiff*ymdiff);
								final double wy00 = D13O21 + ydiff*(D1O14 + ydiff*(D1O2*ydiff - 1));
								final double wyp1 = D13O21 + ymdiff*(D1O14 + ymdiff*(D1O2*ymdiff - 1));
								final double wyp2 = ydiff*(D1O42 + D1O6*ydiff*ydiff);
								ci.x = borders.x + ix - 1;
								ci.y = borders.y + iy - 1;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.y;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.y;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.y;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci);
								row[x] = (
									wym1*(wxm1*in00 + wx00*in01 + wxp1*in02 + wxp2*in03) +
									wy00*(wxm1*in10 + wx00*in11 + wxp1*in12 + wxp2*in13) +
									wyp1*(wxm1*in20 + wx00*in21 + wxp1*in22 + wxp2*in23) +
									wyp2*(wxm1*in30 + wx00*in31 + wxp1*in32 + wxp2*in33)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_omoms3_xyz(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Cubic O-MOMS prefiltering and sampling in x-y-z");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.omoms3(image,new Axes(true,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals 1, the prefiltering operation
			// will not have been carried out in that dimension. Subsequent
			// application of the cubic O-MOMS kernel in that dimension will
			// result in an overall down-scaling of the grey-values, which
			// should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= OMOMS3X0;
			if (idims.y == 1) scale /= OMOMS3X0;
			if (idims.z == 1) scale /= OMOMS3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
		}
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final double tz = (ipc.z + dx*bwd.azx + dy*bwd.azy + dz*bwd.azz + bwd.azt)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y ||
								tz < -1 || iz > ipmax.z) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = xmdiff*(D1O42 + D1O6*xmdiff*xmdiff);
								final double wx00 = D13O21 + xdiff*(D1O14 + xdiff*(D1O2*xdiff - 1));
								final double wxp1 = D13O21 + xmdiff*(D1O14 + xmdiff*(D1O2*xmdiff - 1));
								final double wxp2 = xdiff*(D1O42 + D1O6*xdiff*xdiff);
								final double ydiff = ty - iy;
								final double ymdiff = 1 - ydiff;
								final double wym1 = ymdiff*(D1O42 + D1O6*ymdiff*ymdiff);
								final double wy00 = D13O21 + ydiff*(D1O14 + ydiff*(D1O2*ydiff - 1));
								final double wyp1 = D13O21 + ymdiff*(D1O14 + ymdiff*(D1O2*ymdiff - 1));
								final double wyp2 = ydiff*(D1O42 + D1O6*ydiff*ydiff);
								final double zdiff = tz - iz;
								final double zmdiff = 1 - zdiff;
								final double wzm1 = zmdiff*(D1O42 + D1O6*zmdiff*zmdiff);
								final double wz00 = D13O21 + zdiff*(D1O14 + zdiff*(D1O2*zdiff - 1));
								final double wzp1 = D13O21 + zmdiff*(D1O14 + zmdiff*(D1O2*zmdiff - 1));
								final double wzp2 = zdiff*(D1O42 + D1O6*zdiff*zdiff);
								ci.x = borders.x + ix - 1;
								ci.y = borders.y + iy - 1;
								ci.z = borders.z + iz - 1;
								final double in000 = image.get(ci); ++ci.x;
								final double in001 = image.get(ci); ++ci.x;
								final double in002 = image.get(ci); ++ci.x;
								final double in003 = image.get(ci); ++ci.y;
								final double in013 = image.get(ci); --ci.x;
								final double in012 = image.get(ci); --ci.x;
								final double in011 = image.get(ci); --ci.x;
								final double in010 = image.get(ci); ++ci.y;
								final double in020 = image.get(ci); ++ci.x;
								final double in021 = image.get(ci); ++ci.x;
								final double in022 = image.get(ci); ++ci.x;
								final double in023 = image.get(ci); ++ci.y;
								final double in033 = image.get(ci); --ci.x;
								final double in032 = image.get(ci); --ci.x;
								final double in031 = image.get(ci); --ci.x;
								final double in030 = image.get(ci); ++ci.z;
								final double in130 = image.get(ci); ++ci.x;
								final double in131 = image.get(ci); ++ci.x;
								final double in132 = image.get(ci); ++ci.x;
								final double in133 = image.get(ci); --ci.y;
								final double in123 = image.get(ci); --ci.x;
								final double in122 = image.get(ci); --ci.x;
								final double in121 = image.get(ci); --ci.x;
								final double in120 = image.get(ci); --ci.y;
								final double in110 = image.get(ci); ++ci.x;
								final double in111 = image.get(ci); ++ci.x;
								final double in112 = image.get(ci); ++ci.x;
								final double in113 = image.get(ci); --ci.y;
								final double in103 = image.get(ci); --ci.x;
								final double in102 = image.get(ci); --ci.x;
								final double in101 = image.get(ci); --ci.x;
								final double in100 = image.get(ci); ++ci.z;
								final double in200 = image.get(ci); ++ci.x;
								final double in201 = image.get(ci); ++ci.x;
								final double in202 = image.get(ci); ++ci.x;
								final double in203 = image.get(ci); ++ci.y;
								final double in213 = image.get(ci); --ci.x;
								final double in212 = image.get(ci); --ci.x;
								final double in211 = image.get(ci); --ci.x;
								final double in210 = image.get(ci); ++ci.y;
								final double in220 = image.get(ci); ++ci.x;
								final double in221 = image.get(ci); ++ci.x;
								final double in222 = image.get(ci); ++ci.x;
								final double in223 = image.get(ci); ++ci.y;
								final double in233 = image.get(ci); --ci.x;
								final double in232 = image.get(ci); --ci.x;
								final double in231 = image.get(ci); --ci.x;
								final double in230 = image.get(ci); ++ci.z;
								final double in330 = image.get(ci); ++ci.x;
								final double in331 = image.get(ci); ++ci.x;
								final double in332 = image.get(ci); ++ci.x;
								final double in333 = image.get(ci); --ci.y;
								final double in323 = image.get(ci); --ci.x;
								final double in322 = image.get(ci); --ci.x;
								final double in321 = image.get(ci); --ci.x;
								final double in320 = image.get(ci); --ci.y;
								final double in310 = image.get(ci); ++ci.x;
								final double in311 = image.get(ci); ++ci.x;
								final double in312 = image.get(ci); ++ci.x;
								final double in313 = image.get(ci); --ci.y;
								final double in303 = image.get(ci); --ci.x;
								final double in302 = image.get(ci); --ci.x;
								final double in301 = image.get(ci); --ci.x;
								final double in300 = image.get(ci);
								row[x] = (
									wzm1*(
										wym1*(wxm1*in000 + wx00*in001 + wxp1*in002 + wxp2*in003) +
										wy00*(wxm1*in010 + wx00*in011 + wxp1*in012 + wxp2*in013) +
										wyp1*(wxm1*in020 + wx00*in021 + wxp1*in022 + wxp2*in023) +
										wyp2*(wxm1*in030 + wx00*in031 + wxp1*in032 + wxp2*in033)
									) +
									wz00*(
										wym1*(wxm1*in100 + wx00*in101 + wxp1*in102 + wxp2*in103) +
										wy00*(wxm1*in110 + wx00*in111 + wxp1*in112 + wxp2*in113) +
										wyp1*(wxm1*in120 + wx00*in121 + wxp1*in122 + wxp2*in123) +
										wyp2*(wxm1*in130 + wx00*in131 + wxp1*in132 + wxp2*in133)
									) +
									wzp1*(
										wym1*(wxm1*in200 + wx00*in201 + wxp1*in202 + wxp2*in203) +
										wy00*(wxm1*in210 + wx00*in211 + wxp1*in212 + wxp2*in213) +
										wyp1*(wxm1*in220 + wx00*in221 + wxp1*in222 + wxp2*in223) +
										wyp2*(wxm1*in230 + wx00*in231 + wxp1*in232 + wxp2*in233)
									) +
									wzp2*(
										wym1*(wxm1*in300 + wx00*in301 + wxp1*in302 + wxp2*in303) +
										wy00*(wxm1*in310 + wx00*in311 + wxp1*in312 + wxp2*in313) +
										wyp1*(wxm1*in320 + wx00*in321 + wxp1*in322 + wxp2*in323) +
										wyp2*(wxm1*in330 + wx00*in331 + wxp1*in332 + wxp2*in333)
									)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_bspline5_xy(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Quintic B-spline prefiltering and sampling in x-y");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline5(image,new Axes(true,true,false),borders);
		
		if (antialias) {
			// If any of the dimensions equals 1, the prefiltering operation
			// will not have been carried out in that dimension. Subsequent
			// application of the quintic B-spline kernel in that dimension will
			// result in an overall down-scaling of the grey-values, which
			// should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE5X0;
			if (idims.y == 1) scale /= BSPLINE5X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
		}
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xdiff2 = xdiff*xdiff;
								final double xmdiff = 1 - xdiff;
								final double xmdiff2 = xmdiff*xmdiff;
								final double wxm2 = D1O120*xmdiff2*xmdiff2*xmdiff;
								final double wxm1 = D1O120 + D1O24*xmdiff*(1 + xmdiff*(2 + xmdiff*(2 + xmdiff - xmdiff2)));
								final double wx00 = D11O20 + xdiff2*((D1O4 - D1O12*xdiff)*xdiff2 - D1O2);
								final double wxp1 = D11O20 + xmdiff2*((D1O4 - D1O12*xmdiff)*xmdiff2 - D1O2);
								final double wxp2 = D1O120 + D1O24*xdiff*(1 + xdiff*(2 + xdiff*(2 + xdiff - xdiff2)));
								final double wxp3 = D1O120*xdiff2*xdiff2*xdiff;
								final double ydiff = ty - iy;
								final double ydiff2 = ydiff*ydiff;
								final double ymdiff = 1 - ydiff;
								final double ymdiff2 = ymdiff*ymdiff;
								final double wym2 = D1O120*ymdiff2*ymdiff2*ymdiff;
								final double wym1 = D1O120 + D1O24*ymdiff*(1 + ymdiff*(2 + ymdiff*(2 + ymdiff - ymdiff2)));
								final double wy00 = D11O20 + ydiff2*((D1O4 - D1O12*ydiff)*ydiff2 - D1O2);
								final double wyp1 = D11O20 + ymdiff2*((D1O4 - D1O12*ymdiff)*ymdiff2 - D1O2);
								final double wyp2 = D1O120 + D1O24*ydiff*(1 + ydiff*(2 + ydiff*(2 + ydiff - ydiff2)));
								final double wyp3 = D1O120*ydiff2*ydiff2*ydiff;
								ci.x = borders.x + ix - 2;
								ci.y = borders.y + iy - 2;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.x;
								final double in04 = image.get(ci); ++ci.x;
								final double in05 = image.get(ci); ++ci.y;
								final double in15 = image.get(ci); --ci.x;
								final double in14 = image.get(ci); --ci.x;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.y;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.x;
								final double in24 = image.get(ci); ++ci.x;
								final double in25 = image.get(ci); ++ci.y;
								final double in35 = image.get(ci); --ci.x;
								final double in34 = image.get(ci); --ci.x;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci); ++ci.y;
								final double in40 = image.get(ci); ++ci.x;
								final double in41 = image.get(ci); ++ci.x;
								final double in42 = image.get(ci); ++ci.x;
								final double in43 = image.get(ci); ++ci.x;
								final double in44 = image.get(ci); ++ci.x;
								final double in45 = image.get(ci); ++ci.y;
								final double in55 = image.get(ci); --ci.x;
								final double in54 = image.get(ci); --ci.x;
								final double in53 = image.get(ci); --ci.x;
								final double in52 = image.get(ci); --ci.x;
								final double in51 = image.get(ci); --ci.x;
								final double in50 = image.get(ci);
								row[x] = (
									wym2*(wxm2*in00 + wxm1*in01 + wx00*in02 + wxp1*in03 + wxp2*in04 + wxp3*in05) +
									wym1*(wxm2*in10 + wxm1*in11 + wx00*in12 + wxp1*in13 + wxp2*in14 + wxp3*in15) +
									wy00*(wxm2*in20 + wxm1*in21 + wx00*in22 + wxp1*in23 + wxp2*in24 + wxp3*in25) +
									wyp1*(wxm2*in30 + wxm1*in31 + wx00*in32 + wxp1*in33 + wxp2*in34 + wxp3*in35) +
									wyp2*(wxm2*in40 + wxm1*in41 + wx00*in42 + wxp1*in43 + wxp2*in44 + wxp3*in45) +
									wyp3*(wxm2*in50 + wxm1*in51 + wx00*in52 + wxp1*in53 + wxp2*in54 + wxp3*in55)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void affine_bspline5_xyz(final Image image, final Image affined) {
		
		// Initialization:
		messenger.log("Quintic B-spline prefiltering and sampling in x-y-z");
		progressor.status("Affine transforming"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline5(image,new Axes(true,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals 1, the prefiltering operation
			// will not have been carried out in that dimension. Subsequent
			// application of the quintic B-spline kernel in that dimension will
			// result in an overall down-scaling of the grey-values, which
			// should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE5X0;
			if (idims.y == 1) scale /= BSPLINE5X0;
			if (idims.z == 1) scale /= BSPLINE5X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
		}
		else image.mirror(borders);
		
		// Affine transform using the backward transformation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[][][] ain = new double[6][6][6];
		final double[] row = new double[odims.x];
		image.axes(Axes.X+Axes.Y+Axes.Z);
		affined.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - opc.z;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - opc.y;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - opc.x;
							final double tx = (ipc.x + dx*bwd.axx + dy*bwd.axy + dz*bwd.axz + bwd.axt)/ivx;
							final double ty = (ipc.y + dx*bwd.ayx + dy*bwd.ayy + dz*bwd.ayz + bwd.ayt)/ivy;
							final double tz = (ipc.z + dx*bwd.azx + dy*bwd.azy + dz*bwd.azz + bwd.azt)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (tx < -1 || ix > ipmax.x ||
								ty < -1 || iy > ipmax.y ||
								tz < -1 || iz > ipmax.z) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xdiff2 = xdiff*xdiff;
								final double xmdiff = 1 - xdiff;
								final double xmdiff2 = xmdiff*xmdiff;
								final double wxm2 = D1O120*xmdiff2*xmdiff2*xmdiff;
								final double wxm1 = D1O120 + D1O24*xmdiff*(1 + xmdiff*(2 + xmdiff*(2 + xmdiff - xmdiff2)));
								final double wx00 = D11O20 + xdiff2*((D1O4 - D1O12*xdiff)*xdiff2 - D1O2);
								final double wxp1 = D11O20 + xmdiff2*((D1O4 - D1O12*xmdiff)*xmdiff2 - D1O2);
								final double wxp2 = D1O120 + D1O24*xdiff*(1 + xdiff*(2 + xdiff*(2 + xdiff - xdiff2)));
								final double wxp3 = D1O120*xdiff2*xdiff2*xdiff;
								final double ydiff = ty - iy;
								final double ydiff2 = ydiff*ydiff;
								final double ymdiff = 1 - ydiff;
								final double ymdiff2 = ymdiff*ymdiff;
								final double wym2 = D1O120*ymdiff2*ymdiff2*ymdiff;
								final double wym1 = D1O120 + D1O24*ymdiff*(1 + ymdiff*(2 + ymdiff*(2 + ymdiff - ymdiff2)));
								final double wy00 = D11O20 + ydiff2*((D1O4 - D1O12*ydiff)*ydiff2 - D1O2);
								final double wyp1 = D11O20 + ymdiff2*((D1O4 - D1O12*ymdiff)*ymdiff2 - D1O2);
								final double wyp2 = D1O120 + D1O24*ydiff*(1 + ydiff*(2 + ydiff*(2 + ydiff - ydiff2)));
								final double wyp3 = D1O120*ydiff2*ydiff2*ydiff;
								final double zdiff = tz - iz;
								final double zdiff2 = zdiff*zdiff;
								final double zmdiff = 1 - zdiff;
								final double zmdiff2 = zmdiff*zmdiff;
								final double wzm2 = D1O120*zmdiff2*zmdiff2*zmdiff;
								final double wzm1 = D1O120 + D1O24*zmdiff*(1 + zmdiff*(2 + zmdiff*(2 + zmdiff - zmdiff2)));
								final double wz00 = D11O20 + zdiff2*((D1O4 - D1O12*zdiff)*zdiff2 - D1O2);
								final double wzp1 = D11O20 + zmdiff2*((D1O4 - D1O12*zmdiff)*zmdiff2 - D1O2);
								final double wzp2 = D1O120 + D1O24*zdiff*(1 + zdiff*(2 + zdiff*(2 + zdiff - zdiff2)));
								final double wzp3 = D1O120*zdiff2*zdiff2*zdiff;
								ci.x = borders.x + ix - 2;
								ci.y = borders.y + iy - 2;
								ci.z = borders.z + iz - 2;
								image.get(ci,ain);
								row[x] = (
									wzm2*(
										wym2*(wxm2*ain[0][0][0] + wxm1*ain[0][0][1] + wx00*ain[0][0][2] + wxp1*ain[0][0][3] + wxp2*ain[0][0][4] + wxp3*ain[0][0][5]) +
										wym1*(wxm2*ain[0][1][0] + wxm1*ain[0][1][1] + wx00*ain[0][1][2] + wxp1*ain[0][1][3] + wxp2*ain[0][1][4] + wxp3*ain[0][1][5]) +
										wy00*(wxm2*ain[0][2][0] + wxm1*ain[0][2][1] + wx00*ain[0][2][2] + wxp1*ain[0][2][3] + wxp2*ain[0][2][4] + wxp3*ain[0][2][5]) +
										wyp1*(wxm2*ain[0][3][0] + wxm1*ain[0][3][1] + wx00*ain[0][3][2] + wxp1*ain[0][3][3] + wxp2*ain[0][3][4] + wxp3*ain[0][3][5]) +
										wyp2*(wxm2*ain[0][4][0] + wxm1*ain[0][4][1] + wx00*ain[0][4][2] + wxp1*ain[0][4][3] + wxp2*ain[0][4][4] + wxp3*ain[0][4][5]) +
										wyp3*(wxm2*ain[0][5][0] + wxm1*ain[0][5][1] + wx00*ain[0][5][2] + wxp1*ain[0][5][3] + wxp2*ain[0][5][4] + wxp3*ain[0][5][5])
									) +
									wzm1*(
										wym2*(wxm2*ain[1][0][0] + wxm1*ain[1][0][1] + wx00*ain[1][0][2] + wxp1*ain[1][0][3] + wxp2*ain[1][0][4] + wxp3*ain[1][0][5]) +
										wym1*(wxm2*ain[1][1][0] + wxm1*ain[1][1][1] + wx00*ain[1][1][2] + wxp1*ain[1][1][3] + wxp2*ain[1][1][4] + wxp3*ain[1][1][5]) +
										wy00*(wxm2*ain[1][2][0] + wxm1*ain[1][2][1] + wx00*ain[1][2][2] + wxp1*ain[1][2][3] + wxp2*ain[1][2][4] + wxp3*ain[1][2][5]) +
										wyp1*(wxm2*ain[1][3][0] + wxm1*ain[1][3][1] + wx00*ain[1][3][2] + wxp1*ain[1][3][3] + wxp2*ain[1][3][4] + wxp3*ain[1][3][5]) +
										wyp2*(wxm2*ain[1][4][0] + wxm1*ain[1][4][1] + wx00*ain[1][4][2] + wxp1*ain[1][4][3] + wxp2*ain[1][4][4] + wxp3*ain[1][4][5]) +
										wyp3*(wxm2*ain[1][5][0] + wxm1*ain[1][5][1] + wx00*ain[1][5][2] + wxp1*ain[1][5][3] + wxp2*ain[1][5][4] + wxp3*ain[1][5][5])
									) +
									wz00*(
										wym2*(wxm2*ain[2][0][0] + wxm1*ain[2][0][1] + wx00*ain[2][0][2] + wxp1*ain[2][0][3] + wxp2*ain[2][0][4] + wxp3*ain[2][0][5]) +
										wym1*(wxm2*ain[2][1][0] + wxm1*ain[2][1][1] + wx00*ain[2][1][2] + wxp1*ain[2][1][3] + wxp2*ain[2][1][4] + wxp3*ain[2][1][5]) +
										wy00*(wxm2*ain[2][2][0] + wxm1*ain[2][2][1] + wx00*ain[2][2][2] + wxp1*ain[2][2][3] + wxp2*ain[2][2][4] + wxp3*ain[2][2][5]) +
										wyp1*(wxm2*ain[2][3][0] + wxm1*ain[2][3][1] + wx00*ain[2][3][2] + wxp1*ain[2][3][3] + wxp2*ain[2][3][4] + wxp3*ain[2][3][5]) +
										wyp2*(wxm2*ain[2][4][0] + wxm1*ain[2][4][1] + wx00*ain[2][4][2] + wxp1*ain[2][4][3] + wxp2*ain[2][4][4] + wxp3*ain[2][4][5]) +
										wyp3*(wxm2*ain[2][5][0] + wxm1*ain[2][5][1] + wx00*ain[2][5][2] + wxp1*ain[2][5][3] + wxp2*ain[2][5][4] + wxp3*ain[2][5][5])
									) +
									wzp1*(
										wym2*(wxm2*ain[3][0][0] + wxm1*ain[3][0][1] + wx00*ain[3][0][2] + wxp1*ain[3][0][3] + wxp2*ain[3][0][4] + wxp3*ain[3][0][5]) +
										wym1*(wxm2*ain[3][1][0] + wxm1*ain[3][1][1] + wx00*ain[3][1][2] + wxp1*ain[3][1][3] + wxp2*ain[3][1][4] + wxp3*ain[3][1][5]) +
										wy00*(wxm2*ain[3][2][0] + wxm1*ain[3][2][1] + wx00*ain[3][2][2] + wxp1*ain[3][2][3] + wxp2*ain[3][2][4] + wxp3*ain[3][2][5]) +
										wyp1*(wxm2*ain[3][3][0] + wxm1*ain[3][3][1] + wx00*ain[3][3][2] + wxp1*ain[3][3][3] + wxp2*ain[3][3][4] + wxp3*ain[3][3][5]) +
										wyp2*(wxm2*ain[3][4][0] + wxm1*ain[3][4][1] + wx00*ain[3][4][2] + wxp1*ain[3][4][3] + wxp2*ain[3][4][4] + wxp3*ain[3][4][5]) +
										wyp3*(wxm2*ain[3][5][0] + wxm1*ain[3][5][1] + wx00*ain[3][5][2] + wxp1*ain[3][5][3] + wxp2*ain[3][5][4] + wxp3*ain[3][5][5])
									) +
									wzp2*(
										wym2*(wxm2*ain[4][0][0] + wxm1*ain[4][0][1] + wx00*ain[4][0][2] + wxp1*ain[4][0][3] + wxp2*ain[4][0][4] + wxp3*ain[4][0][5]) +
										wym1*(wxm2*ain[4][1][0] + wxm1*ain[4][1][1] + wx00*ain[4][1][2] + wxp1*ain[4][1][3] + wxp2*ain[4][1][4] + wxp3*ain[4][1][5]) +
										wy00*(wxm2*ain[4][2][0] + wxm1*ain[4][2][1] + wx00*ain[4][2][2] + wxp1*ain[4][2][3] + wxp2*ain[4][2][4] + wxp3*ain[4][2][5]) +
										wyp1*(wxm2*ain[4][3][0] + wxm1*ain[4][3][1] + wx00*ain[4][3][2] + wxp1*ain[4][3][3] + wxp2*ain[4][3][4] + wxp3*ain[4][3][5]) +
										wyp2*(wxm2*ain[4][4][0] + wxm1*ain[4][4][1] + wx00*ain[4][4][2] + wxp1*ain[4][4][3] + wxp2*ain[4][4][4] + wxp3*ain[4][4][5]) +
										wyp3*(wxm2*ain[4][5][0] + wxm1*ain[4][5][1] + wx00*ain[4][5][2] + wxp1*ain[4][5][3] + wxp2*ain[4][5][4] + wxp3*ain[4][5][5])
									) +
									wzp3*(
										wym2*(wxm2*ain[5][0][0] + wxm1*ain[5][0][1] + wx00*ain[5][0][2] + wxp1*ain[5][0][3] + wxp2*ain[5][0][4] + wxp3*ain[5][0][5]) +
										wym1*(wxm2*ain[5][1][0] + wxm1*ain[5][1][1] + wx00*ain[5][1][2] + wxp1*ain[5][1][3] + wxp2*ain[5][1][4] + wxp3*ain[5][1][5]) +
										wy00*(wxm2*ain[5][2][0] + wxm1*ain[5][2][1] + wx00*ain[5][2][2] + wxp1*ain[5][2][3] + wxp2*ain[5][2][4] + wxp3*ain[5][2][5]) +
										wyp1*(wxm2*ain[5][3][0] + wxm1*ain[5][3][1] + wx00*ain[5][3][2] + wxp1*ain[5][3][3] + wxp2*ain[5][3][4] + wxp3*ain[5][3][5]) +
										wyp2*(wxm2*ain[5][4][0] + wxm1*ain[5][4][1] + wx00*ain[5][4][2] + wxp1*ain[5][4][3] + wxp2*ain[5][4][4] + wxp3*ain[5][4][5]) +
										wyp3*(wxm2*ain[5][5][0] + wxm1*ain[5][5][1] + wx00*ain[5][5][2] + wxp1*ain[5][5][3] + wxp2*ain[5][5][4] + wxp3*ain[5][5][5])
									)
								);
							}
						}
						affined.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private String schemes(final int interpolation) {
		
		switch (interpolation) {
			case NEAREST: return "nearest-neighbor interpolation";
			case LINEAR: return "linear interpolation";
			case CUBIC: return "cubic convolution interpolation";
			case BSPLINE3: return "cubic B-spline interpolation";
			case OMOMS3: return "cubic O-MOMS interpolation";
			case BSPLINE5: return "quintic B-spline interpolation";
		}
		
		return "unknown interpolation";
	}
	
	/** The value used for background filling. The default value is {@code 0}. */
	public double background = 0;
	
	/** The object used for message displaying. */
	public final Messenger messenger = new Messenger();
	
	/** The object used for progress displaying. */
	public final Progressor progressor = new Progressor();
	
	private final Prefilter prefilter = new Prefilter();
	
	private Dimensions idims, odims;
	
	private Transform fwd, bwd;
	
	private String component = "";
	
	private Borders borders;
	
	private int interpolation;
	
	private double ivx, ivy, ivz;
	private double ovx, ovy, ovz;
	
	private Point ipc, opc, ipmax;
	
	private boolean doxy, antialias;
	
	private final double D1O2 = 1.0/2.0;
	private final double D1O4 = 1.0/4.0;
	private final double D1O6 = 1.0/6.0;
	private final double D1O12 = 1.0/12.0;
	private final double D1O14 = 1.0/14.0;
	private final double D1O24 = 1.0/24.0;
	private final double D1O42 = 1.0/42.0;
	private final double D1O120 = 1.0/120.0;
	private final double D2O3 = 2.0/3.0;
	private final double D3O2 = 3.0/2.0;
	private final double D5O2 = 5.0/2.0;
	private final double D11O20 = 11.0/20.0;
	private final double D13O21 = 13.0/21.0;
	private final double DM1O2 = -1.0/2.0;
	
	private final double BSPLINE3X0 = 0.666666666667;
	private final double BSPLINE5X0 = 0.55;
	private final double OMOMS3X0 = 0.619047619048;
	
}
