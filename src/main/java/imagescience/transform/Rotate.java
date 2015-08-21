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

import imagescience.utility.FMath;
import imagescience.utility.Messenger;
import imagescience.utility.Progressor;
import imagescience.utility.Timer;

/** Rotates an image using different interpolation schemes.
	
	<dt><b>References:</b></dt>
	
	<dd><table border="0" cellspacing="0" cellpadding="0">
	
	<tr><td valign="top">[1]</td><td width="10"></td><td>R. G. Keys, "Cubic Convolution Interpolation for Digital Image Processing", <em>IEEE Transactions on Acoustics, Speech, and Signal Processing</em>, vol. 29, no. 6, December 1981, pp. 1153-1160.</td></tr>

	<tr><td valign="top">[2]</td><td width="10"></td><td>M. Unser, "Splines: A Perfect Fit for Signal and Image Processing", <em>IEEE Signal Processing Magazine</em>, vol. 16, no. 6, November 1999, pp. 22-38.</td></tr>
	
	<tr><td valign="top">[3]</td><td width="10"></td><td>P. Thevenaz, T. Blu, M. Unser, "Interpolation Revisited", <em>IEEE Transactions on Medical Imaging</em>, vol. 19, no. 7, July 2000, pp.739-758.</td></tr>
	
	<tr><td valign="top">[4]</td><td width="10"></td><td>E. Meijering, W. Niessen, M. Viergever, "Quantitative Evaluation of Convolution-Based Methods for Medical Image Interpolation", <em>Medical Image Analysis</em>, vol. 5, no. 2, June 2001, pp. 111-126.</td></tr>
	
	<tr><td valign="top">[5]</td><td width="10"></td><td>T. Blu, P. Thevenaz, M. Unser, "MOMS: Maximal-Order Interpolation of Minimal Support", <em>IEEE Transactions on Image Processing</em>, vol. 10, no. 7, July 2001, pp. 1069-1080.</td></tr>
	
	</table></dd>
*/
public class Rotate {
	
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
	public Rotate() { }
	
	/** Rotates an image.
		
		@param image The input image to be rotated. For images of type {@link ColorImage}, the color components are processed separately by the method.
		
		@param zangle {@code yangle} - {@code xangle} - The rotation angles (in degrees) around the z-, y-, and x-axis of the image. The order of rotation around the different axes is the same as that of the parameters. Rotation is applied to every x-y-z subimage in a 5D image. The origin of the right-handed coordinate system in which the rotation is carried out is taken in the center of each subimage.
		
		@param interpolation The interpolation scheme to be used. Must be equal to one of the static fields of this class.
		
		@param adjust If {@code true}, the size of the output image is adjusted to fit the entire rotated image; if {@code false}, the size of the output image will be equal to that of the input image.
		
		@param resample If {@code true}, the output image is resampled isotropically, using as sampling interval the smallest of the x-, y-, and z-aspect sizes of the image; if {@code false}, the output image is sampled on the same grid as the input image.
		
		@param antialias If {@code true}, the method attempts to reduce the "stair-casing" effect at the transitions from image to background.
		
		@return A new image containing a rotated version of the input image. The returned image is of the same type as the input image.
		
		@throws IllegalArgumentException If the requested {@code interpolation} scheme is not supported.
		
		@throws IllegalStateException If the x-, y-, or z-aspect size of {@code image} is less than or equal to {@code 0}.
		
		@throws NullPointerException If {@code image} is {@code null}.
		
		@throws UnknownError If for any reason the output image can not be created. In most cases this will be due to insufficient free memory.
	*/
	public synchronized Image run(
		final Image image,
		final double zangle,
		final double yangle,
		final double xangle,
		final int interpolation,
		final boolean adjust,
		final boolean resample,
		final boolean antialias
	) {
		
		messenger.log(ImageScience.prelude()+"Rotate");
		
		// Initialize:
		final Timer timer = new Timer();
		timer.messenger.log(messenger.log());
		timer.start();
		
		initialize(image,zangle,yangle,xangle,interpolation,adjust,resample,antialias);
		
		// Rotate:
		messenger.log("Rotating "+image.type());
		Image rotated = null;
		if (image instanceof ColorImage) {
			messenger.log("Processing RGB-color components separately");
			final ColorImage cimage = (ColorImage)image;
			
			progressor.range(0,0.33);
			component = " red component";
			messenger.log("Rotating"+component);
			cimage.component(ColorImage.RED);
			Image comp = cimage.get(); comp = rotate(comp);
			final ColorImage crotated = new ColorImage(comp.dimensions());
			crotated.component(ColorImage.RED);
			crotated.set(comp);
			
			progressor.range(0.33,0.67);
			component = " green component";
			messenger.log("Rotating"+component);
			cimage.component(ColorImage.GREEN);
			comp = cimage.get(); comp = rotate(comp);
			crotated.component(ColorImage.GREEN);
			crotated.set(comp);
			
			progressor.range(0.67,1);
			component = " blue component";
			messenger.log("Rotating"+component);
			cimage.component(ColorImage.BLUE);
			comp = cimage.get(); comp = rotate(comp);
			crotated.component(ColorImage.BLUE);
			crotated.set(comp);
			
			rotated = crotated;
			
		} else {
			component = "";
			progressor.range(0,1);
			rotated = rotate(image);
		}
		
		// Finish up:
		rotated.name(image.name()+" rotated");
		rotated.aspects(new Aspects(ovx,ovy,ovz,image.aspects().t,image.aspects().c));
		timer.stop();
		
		return rotated;
	}
	
	private Image rotate(final Image image) {
		
		// Duplicate input image if no interpolation is needed:
		final boolean ax0 = (xangle == 0);
		final boolean ay0 = (yangle == 0);
		final boolean az0 = (zangle == 0);
		final boolean vxe = (ovx == ivx);
		final boolean vye = (ovy == ivy);
		final boolean vze = (ovz == ivz);
		if (ax0 && ay0 && az0 && vxe && vye && vze) {
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
		final Image rotated = Image.create(odims,image.type());
		
		// Rotate using dedicated interpolation routine:
		switch (interpolation) {
			case NEAREST:
				if (ax0 && ay0 && vze) rotate_nearest_z(bordered,rotated);
				else if (ax0 && az0 && vye) rotate_nearest_y(bordered,rotated);
				else if (ay0 && az0 && vxe) rotate_nearest_x(bordered,rotated);
				else rotate_nearest_zyx(bordered,rotated);
				break;
			case LINEAR:
				if (ax0 && ay0 && vze) rotate_linear_z(bordered,rotated);
				else if (ax0 && az0 && vye) rotate_linear_y(bordered,rotated);
				else if (ay0 && az0 && vxe) rotate_linear_x(bordered,rotated);
				else rotate_linear_zyx(bordered,rotated);
				break;
			case CUBIC:
				if (ax0 && ay0 && vze) rotate_cubic_z(bordered,rotated);
				else if (ax0 && az0 && vye) rotate_cubic_y(bordered,rotated);
				else if (ay0 && az0 && vxe) rotate_cubic_x(bordered,rotated);
				else rotate_cubic_zyx(bordered,rotated);
				break;
			case BSPLINE3:
				if (ax0 && ay0 && vze) rotate_bspline3_z(bordered,rotated);
				else if (ax0 && az0 && vye) rotate_bspline3_y(bordered,rotated);
				else if (ay0 && az0 && vxe) rotate_bspline3_x(bordered,rotated);
				else rotate_bspline3_zyx(bordered,rotated);
				break;
			case OMOMS3:
				if (ax0 && ay0 && vze) rotate_omoms3_z(bordered,rotated);
				else if (ax0 && az0 && vye) rotate_omoms3_y(bordered,rotated);
				else if (ay0 && az0 && vxe) rotate_omoms3_x(bordered,rotated);
				else rotate_omoms3_zyx(bordered,rotated);
				break;
			case BSPLINE5:
				if (ax0 && ay0 && vze) rotate_bspline5_z(bordered,rotated);
				else if (ax0 && az0 && vye) rotate_bspline5_y(bordered,rotated);
				else if (ay0 && az0 && vxe) rotate_bspline5_x(bordered,rotated);
				else rotate_bspline5_zyx(bordered,rotated);
				break;
		}
		
		return rotated;
	}
	
	private void initialize(
		final Image image,
		final double zangle,
		final double yangle,
		final double xangle,
		final int interpolation,
		final boolean adjust,
		final boolean resample,
		final boolean antialias
	) {
		
		// Store rotation angles:
		this.zangle = zangle;
		this.yangle = yangle;
		this.xangle = xangle;
		
		messenger.log("Rotation angle around z-axes: "+zangle);
		messenger.log("Rotation angle around y-axes: "+yangle);
		messenger.log("Rotation angle around x-axes: "+xangle);
		
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
		
		imaxx = idims.x - 1;
		imaxy = idims.y - 1;
		imaxz = idims.z - 1;
		
		ixc = 0.5*ivx*imaxx;
		iyc = 0.5*ivy*imaxy;
		izc = 0.5*ivz*imaxz;
		
		// Precompute sines and cosines of rotation angles:
		final double axrad = xangle*Math.PI/180;
		final double ayrad = yangle*Math.PI/180;
		final double azrad = zangle*Math.PI/180;
		
		cosax = Math.cos(axrad);
		sinax = Math.sin(axrad);
		cosay = Math.cos(ayrad);
		sinay = Math.sin(ayrad);
		cosaz = Math.cos(azrad);
		sinaz = Math.sin(azrad);
		
		// Precompute elements of inverse rotation matrix:
		invxx = cosay*cosaz;
		invxy = cosaz*sinax*sinay+cosax*sinaz;
		invxz = -cosax*cosaz*sinay+sinax*sinaz;
		
		invyx = -cosay*sinaz;
		invyy = cosax*cosaz-sinax*sinay*sinaz;
		invyz = cosaz*sinax+cosax*sinay*sinaz;
		
		invzx = sinay;
		invzy = -cosay*sinax;
		invzz = cosax*cosay;
		
		// Compute size and center of rotated image:
		double rminx = -0.5*ovx;
		double rminy = -0.5*ovy;
		double rminz = -0.5*ovz;
		double rmaxx = (imaxx + 0.5)*ovx;
		double rmaxy = (imaxy + 0.5)*ovy;
		double rmaxz = (imaxz + 0.5)*ovz;
		
		if (adjust) {
			messenger.log("Adjusting output image dimensions to fit result");
			
			// Compute rotated positions of eight corner points:
			final double x1 = -ixc - 0.5*ivx;
			final double y1 = -iyc - 0.5*ivy;
			final double z1 = -izc - 0.5*ivz;
			
			final double x2 = -x1;
			final double y2 = y1;
			final double z2 = z1;
			
			final double x3 = x1;
			final double y3 = -y1;
			final double z3 = z1;
			
			final double x4 = -x1;
			final double y4 = -y1;
			final double z4 = z1;
			
			final double x5 = x1;
			final double y5 = y1;
			final double z5 = -z1;
			
			final double x6 = -x1;
			final double y6 = y1;
			final double z6 = -z1;
			
			final double x7 = x1;
			final double y7 = -y1;
			final double z7 = -z1;
			
			final double x8 = -x1;
			final double y8 = -y1;
			final double z8 = -z1;
			
			// Use forward compound rotation matrix:
			final double dxx = cosay*cosaz;
			final double dyx = -cosay*sinaz;
			final double dzx = sinay;
			final double dxy = cosaz*sinax*sinay + cosax*sinaz;
			final double dyy = cosax*cosaz - sinax*sinay*sinaz;
			final double dzy = -cosay*sinax;
			final double dxz = -cosax*cosaz*sinay + sinax*sinaz;
			final double dyz = cosaz*sinax + cosax*sinay*sinaz;
			final double dzz = cosax*cosay;
			
			final double rx1 = x1*dxx + y1*dyx + z1*dzx;
			final double ry1 = x1*dxy + y1*dyy + z1*dzy;
			final double rz1 = x1*dxz + y1*dyz + z1*dzz;
			
			final double rx2 = x2*dxx + y2*dyx + z2*dzx;
			final double ry2 = x2*dxy + y2*dyy + z2*dzy;
			final double rz2 = x2*dxz + y2*dyz + z2*dzz;
			
			final double rx3 = x3*dxx + y3*dyx + z3*dzx;
			final double ry3 = x3*dxy + y3*dyy + z3*dzy;
			final double rz3 = x3*dxz + y3*dyz + z3*dzz;

			final double rx4 = x4*dxx + y4*dyx + z4*dzx;
			final double ry4 = x4*dxy + y4*dyy + z4*dzy;
			final double rz4 = x4*dxz + y4*dyz + z4*dzz;

			final double rx5 = x5*dxx + y5*dyx + z5*dzx;
			final double ry5 = x5*dxy + y5*dyy + z5*dzy;
			final double rz5 = x5*dxz + y5*dyz + z5*dzz;

			final double rx6 = x6*dxx + y6*dyx + z6*dzx;
			final double ry6 = x6*dxy + y6*dyy + z6*dzy;
			final double rz6 = x6*dxz + y6*dyz + z6*dzz;

			final double rx7 = x7*dxx + y7*dyx + z7*dzx;
			final double ry7 = x7*dxy + y7*dyy + z7*dzy;
			final double rz7 = x7*dxz + y7*dyz + z7*dzz;

			final double rx8 = x8*dxx + y8*dyx + z8*dzx;
			final double ry8 = x8*dxy + y8*dyy + z8*dzy;
			final double rz8 = x8*dxz + y8*dyz + z8*dzz;
			
			rminx = FMath.min(rx1,rx2,rx3,rx4,rx5,rx6,rx7,rx8);
			rminy = FMath.min(ry1,ry2,ry3,ry4,ry5,ry6,ry7,ry8);
			rminz = FMath.min(rz1,rz2,rz3,rz4,rz5,rz6,rz7,rz8);
			
			rmaxx = FMath.max(rx1,rx2,rx3,rx4,rx5,rx6,rx7,rx8);
			rmaxy = FMath.max(ry1,ry2,ry3,ry4,ry5,ry6,ry7,ry8);
			rmaxz = FMath.max(rz1,rz2,rz3,rz4,rz5,rz6,rz7,rz8);
			
		} else {
			messenger.log("Not adjusting image dimensions");
		}
		
		odims = new Dimensions(
			FMath.round((rmaxx - rminx)/ovx),
			FMath.round((rmaxy - rminy)/ovy),
			FMath.round((rmaxz - rminz)/ovz),
			idims.t,
			idims.c);
		
		oxc = 0.5*ovx*(odims.x - 1);
		oyc = 0.5*ovy*(odims.y - 1);
		ozc = 0.5*ovz*(odims.z - 1);
		
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
		
		// Set borders based on interpolation and resampling:
		int b = 0;
		switch (interpolation) {
			case NEAREST: b = 0; break;
			case LINEAR: b = 1; break;
			case CUBIC: b = 2; break;
			case BSPLINE3: b = 2; break;
			case OMOMS3: b = 2; break;
			case BSPLINE5: b = 3; break;
		}
		if (xangle == 0 && yangle == 0 && ovz == ivz) borders = new Borders(b,b,0);
		else if (xangle == 0 && zangle == 0 && ovy == ivy) borders = new Borders(b,0,b);
		else if (yangle == 0 && zangle == 0 && ovx == ivx) borders = new Borders(0,b,b);
		else borders = new Borders(b,b,b);
	}
	
	private void rotate_nearest_z(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Nearest-neighbor sampling in x-y");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdysinaz = ixc + dy*sinaz;
						final double ycdycosaz = iyc + dy*cosaz;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							ci.x = FMath.round((xcdysinaz + dx*cosaz)/ivx);
							ci.y = FMath.round((ycdycosaz - dx*sinaz)/ivy);
							if (ci.x < 0 || ci.x > imaxx ||
								ci.y < 0 || ci.y > imaxy) {
								row[x] = background;
							} else {
								row[x] = image.get(ci);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_nearest_y(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Nearest-neighbor sampling in x-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzsinay = ixc - dz*sinay;
					final double zcdzcosay = izc + dz*cosay;
					for (co.y=ci.y=0; co.y<odims.y; ++co.y, ++ci.y) {
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							ci.x = FMath.round((xcdzsinay + dx*cosay)/ivx);
							ci.z = FMath.round((zcdzcosay + dx*sinay)/ivz);
							if (ci.x < 0 || ci.x > imaxx ||
								ci.z < 0 || ci.z > imaxz) {
								row[x] = background;
							} else {
								row[x] = image.get(ci);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_nearest_x(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Nearest-neighbor sampling in y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double ycdzsinax = iyc + dz*sinax;
					final double zcdzcosax = izc + dz*cosax;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						ci.y = FMath.round((ycdzsinax + dy*cosax)/ivy);
						ci.z = FMath.round((zcdzcosax - dy*sinax)/ivz);
						ci.x = 0;
						if (ci.y < 0 || ci.y > imaxy ||
							ci.z < 0 || ci.z > imaxz) {
							for (int x=0; x<odims.x; ++x)
								row[x] = background;
						} else {
							for (int x=0; x<odims.x; ++x, ++ci.x)
								row[x] = image.get(ci);
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_nearest_zyx(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Nearest-neighbor sampling in x-y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzinvxz = ixc + dz*invxz;
					final double ycdzinvyz = iyc + dz*invyz;
					final double zcdzinvzz = izc + dz*invzz;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdzinvxzdyinvxy = xcdzinvxz + dy*invxy;
						final double ycdzinvyzdyinvyy = ycdzinvyz + dy*invyy;
						final double zcdzinvzzdyinvzy = zcdzinvzz + dy*invzy;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							ci.x = FMath.round((xcdzinvxzdyinvxy + dx*invxx)/ivx);
							ci.y = FMath.round((ycdzinvyzdyinvyy + dx*invyx)/ivy);
							ci.z = FMath.round((zcdzinvzzdyinvzy + dx*invzx)/ivz);
							if (ci.x < 0 || ci.x > imaxx ||
								ci.y < 0 || ci.y > imaxy ||
								ci.z < 0 || ci.z > imaxz) {
								row[x] = background;
							} else {
								row[x] = image.get(ci);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_linear_z(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Linear sampling in x-y");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdysinaz = ixc + dy*sinaz;
						final double ycdycosaz = iyc + dy*cosaz;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdysinaz + dx*cosaz)/ivx;
							final double ty = (ycdycosaz - dx*sinaz)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_linear_y(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Linear sampling in x-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzsinay = ixc - dz*sinay;
					final double zcdzcosay = izc + dz*cosay;
					for (co.y=ci.y=0; co.y<odims.y; ++co.y, ++ci.y) {
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzsinay + dx*cosay)/ivx;
							final double tz = (zcdzcosay + dx*sinay)/ivz;
							final int ix = FMath.floor(tx);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iz < -1 || iz > imaxz) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double zdiff = tz - iz;
								final double xmdiff = 1 - xdiff;
								final double zmdiff = 1 - zdiff;
								ci.x = borders.x + ix;
								ci.z = borders.z + iz;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.z;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci);
								row[x] = (
									zmdiff*xmdiff*in00 +
									zmdiff*xdiff*in01 +
									zdiff*xmdiff*in10 +
									zdiff*xdiff*in11
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_linear_x(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Linear sampling in y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double ycdzsinax = iyc + dz*sinax;
					final double zcdzcosax = izc + dz*cosax;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double ty = (ycdzsinax + dy*cosax)/ivy;
						final double tz = (zcdzcosax - dy*sinax)/ivz;
						final int iy = FMath.floor(ty);
						final int iz = FMath.floor(tz);
						if (iy < -1 || iy > imaxy ||
							iz < -1 || iz > imaxz) {
							for (int x=0; x<odims.x; ++x)
								row[x] = background;
						} else {
							final double ydiff = ty - iy;
							final double zdiff = tz - iz;
							final double ymdiff = 1 - ydiff;
							final double zmdiff = 1 - zdiff;
							ci.y = borders.y + iy;
							ci.z = borders.z + iz;
							ci.x = 0;
							for (int x=0; x<odims.x; ++x, ++ci.x) {
								final double in00 = image.get(ci); ++ci.y;
								final double in01 = image.get(ci); ++ci.z;
								final double in11 = image.get(ci); --ci.y;
								final double in10 = image.get(ci); --ci.z;
								row[x] = (
									zmdiff*ymdiff*in00 +
									zmdiff*ydiff*in01 +
									zdiff*ymdiff*in10 +
									zdiff*ydiff*in11
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_linear_zyx(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Linear sampling in x-y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzinvxz = ixc + dz*invxz;
					final double ycdzinvyz = iyc + dz*invyz;
					final double zcdzinvzz = izc + dz*invzz;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdzinvxzdyinvxy = xcdzinvxz + dy*invxy;
						final double ycdzinvyzdyinvyy = ycdzinvyz + dy*invyy;
						final double zcdzinvzzdyinvzy = zcdzinvzz + dy*invzy;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzinvxzdyinvxy + dx*invxx)/ivx;
							final double ty = (ycdzinvyzdyinvyy + dx*invyx)/ivy;
							final double tz = (zcdzinvzzdyinvzy + dx*invzx)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy ||
								iz < -1 || iz > imaxz) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_cubic_z(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic convolution sampling in x-y");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdysinaz = ixc + dy*sinaz;
						final double ycdycosaz = iyc + dy*cosaz;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdysinaz + dx*cosaz)/ivx;
							final double ty = (ycdycosaz - dx*sinaz)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_cubic_y(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic convolution sampling in x-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzsinay = ixc - dz*sinay;
					final double zcdzcosay = izc + dz*cosay;
					for (co.y=ci.y=0; co.y<odims.y; ++co.y, ++ci.y) {
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzsinay + dx*cosay)/ivx;
							final double tz = (zcdzcosay + dx*sinay)/ivz;
							final int ix = FMath.floor(tx);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iz < -1 || iz > imaxz) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = DM1O2*xdiff*xmdiff*xmdiff;
								final double wx00 = 1 + (D3O2*xdiff - D5O2)*xdiff*xdiff;
								final double wxp1 = 1 + (D3O2*xmdiff - D5O2)*xmdiff*xmdiff;
								final double wxp2 = DM1O2*xmdiff*xdiff*xdiff;
								final double zdiff = tz - iz;
								final double zmdiff = 1 - zdiff;
								final double wzm1 = DM1O2*zdiff*zmdiff*zmdiff;
								final double wz00 = 1 + (D3O2*zdiff - D5O2)*zdiff*zdiff;
								final double wzp1 = 1 + (D3O2*zmdiff - D5O2)*zmdiff*zmdiff;
								final double wzp2 = DM1O2*zmdiff*zdiff*zdiff;
								ci.x = borders.x + ix - 1;
								ci.z = borders.z + iz - 1;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.z;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.z;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci);
								row[x] = (
									wzm1*(wxm1*in00 + wx00*in01 + wxp1*in02 + wxp2*in03) +
									wz00*(wxm1*in10 + wx00*in11 + wxp1*in12 + wxp2*in13) +
									wzp1*(wxm1*in20 + wx00*in21 + wxp1*in22 + wxp2*in23) +
									wzp2*(wxm1*in30 + wx00*in31 + wxp1*in32 + wxp2*in33)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_cubic_x(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic convolution sampling in y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double ycdzsinax = iyc + dz*sinax;
					final double zcdzcosax = izc + dz*cosax;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double ty = (ycdzsinax + dy*cosax)/ivy;
						final double tz = (zcdzcosax - dy*sinax)/ivz;
						final int iy = FMath.floor(ty);
						final int iz = FMath.floor(tz);
						if (iy < -1 || iy > imaxy ||
							iz < -1 || iz > imaxz) {
							for (int x=0; x<odims.x; ++x)
								row[x] = background;
						} else {
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
							ci.y = borders.y + iy - 1;
							ci.z = borders.z + iz - 1;
							ci.x = 0;
							for (int x=0; x<odims.x; ++x, ++ci.x) {
								final double in00 = image.get(ci); ++ci.y;
								final double in01 = image.get(ci); ++ci.y;
								final double in02 = image.get(ci); ++ci.y;
								final double in03 = image.get(ci); ++ci.z;
								final double in13 = image.get(ci); --ci.y;
								final double in12 = image.get(ci); --ci.y;
								final double in11 = image.get(ci); --ci.y;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.y;
								final double in21 = image.get(ci); ++ci.y;
								final double in22 = image.get(ci); ++ci.y;
								final double in23 = image.get(ci); ++ci.z;
								final double in33 = image.get(ci); --ci.y;
								final double in32 = image.get(ci); --ci.y;
								final double in31 = image.get(ci); --ci.y;
								final double in30 = image.get(ci); ci.z -= 3;
								row[x] = (
									wzm1*(wym1*in00 + wy00*in01 + wyp1*in02 + wyp2*in03) +
									wz00*(wym1*in10 + wy00*in11 + wyp1*in12 + wyp2*in13) +
									wzp1*(wym1*in20 + wy00*in21 + wyp1*in22 + wyp2*in23) +
									wzp2*(wym1*in30 + wy00*in31 + wyp1*in32 + wyp2*in33)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_cubic_zyx(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic convolution sampling in x-y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		if (antialias) image.set(borders,background);
		else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzinvxz = ixc + dz*invxz;
					final double ycdzinvyz = iyc + dz*invyz;
					final double zcdzinvzz = izc + dz*invzz;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdzinvxzdyinvxy = xcdzinvxz + dy*invxy;
						final double ycdzinvyzdyinvyy = ycdzinvyz + dy*invyy;
						final double zcdzinvzzdyinvzy = zcdzinvzz + dy*invzy;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzinvxzdyinvxy + dx*invxx)/ivx;
							final double ty = (ycdzinvyzdyinvyy + dx*invyx)/ivy;
							final double tz = (zcdzinvzzdyinvzy + dx*invzx)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy ||
								iz < -1 || iz > imaxz) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline3_z(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic B-spline prefiltering and sampling in x-y");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline3(image,new Axes(true,true,false),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE3X0;
			if (idims.y == 1) scale /= BSPLINE3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdysinaz = ixc + dy*sinaz;
						final double ycdycosaz = iyc + dy*cosaz;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdysinaz + dx*cosaz)/ivx;
							final double ty = (ycdycosaz - dx*sinaz)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline3_y(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic B-spline prefiltering and sampling in x-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline3(image,new Axes(true,false,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE3X0;
			if (idims.z == 1) scale /= BSPLINE3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzsinay = ixc - dz*sinay;
					final double zcdzcosay = izc + dz*cosay;
					for (co.y=ci.y=0; co.y<odims.y; ++co.y, ++ci.y) {
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzsinay + dx*cosay)/ivx;
							final double tz = (zcdzcosay + dx*sinay)/ivz;
							final int ix = FMath.floor(tx);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iz < -1 || iz > imaxz) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = D1O6*xmdiff*xmdiff*xmdiff;
								final double wx00 = D2O3 + (D1O2*xdiff - 1)*xdiff*xdiff;
								final double wxp1 = D2O3 + (D1O2*xmdiff - 1)*xmdiff*xmdiff;
								final double wxp2 = D1O6*xdiff*xdiff*xdiff;
								final double zdiff = tz - iz;
								final double zmdiff = 1 - zdiff;
								final double wzm1 = D1O6*zmdiff*zmdiff*zmdiff;
								final double wz00 = D2O3 + (D1O2*zdiff - 1)*zdiff*zdiff;
								final double wzp1 = D2O3 + (D1O2*zmdiff - 1)*zmdiff*zmdiff;
								final double wzp2 = D1O6*zdiff*zdiff*zdiff;
								ci.x = borders.x + ix - 1;
								ci.z = borders.z + iz - 1;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.z;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.z;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci);
								row[x] = (
									wzm1*(wxm1*in00 + wx00*in01 + wxp1*in02 + wxp2*in03) +
									wz00*(wxm1*in10 + wx00*in11 + wxp1*in12 + wxp2*in13) +
									wzp1*(wxm1*in20 + wx00*in21 + wxp1*in22 + wxp2*in23) +
									wzp2*(wxm1*in30 + wx00*in31 + wxp1*in32 + wxp2*in33)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline3_x(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic B-spline prefiltering and sampling in y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline3(image,new Axes(false,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.y == 1) scale /= BSPLINE3X0;
			if (idims.z == 1) scale /= BSPLINE3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double ycdzsinax = iyc + dz*sinax;
					final double zcdzcosax = izc + dz*cosax;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double ty = (ycdzsinax + dy*cosax)/ivy;
						final double tz = (zcdzcosax - dy*sinax)/ivz;
						final int iy = FMath.floor(ty);
						final int iz = FMath.floor(tz);
						if (iy < -1 || iy > imaxy ||
							iz < -1 || iz > imaxz) {
							for (int x=0; x<odims.x; ++x)
								row[x] = background;
						} else {
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
							ci.y = borders.y + iy - 1;
							ci.z = borders.z + iz - 1;
							ci.x = 0;
							for (int x=0; x<odims.x; ++x, ++ci.x) {
								final double in00 = image.get(ci); ++ci.y;
								final double in01 = image.get(ci); ++ci.y;
								final double in02 = image.get(ci); ++ci.y;
								final double in03 = image.get(ci); ++ci.z;
								final double in13 = image.get(ci); --ci.y;
								final double in12 = image.get(ci); --ci.y;
								final double in11 = image.get(ci); --ci.y;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.y;
								final double in21 = image.get(ci); ++ci.y;
								final double in22 = image.get(ci); ++ci.y;
								final double in23 = image.get(ci); ++ci.z;
								final double in33 = image.get(ci); --ci.y;
								final double in32 = image.get(ci); --ci.y;
								final double in31 = image.get(ci); --ci.y;
								final double in30 = image.get(ci); ci.z -= 3;
								row[x] = (
									wzm1*(wym1*in00 + wy00*in01 + wyp1*in02 + wyp2*in03) +
									wz00*(wym1*in10 + wy00*in11 + wyp1*in12 + wyp2*in13) +
									wzp1*(wym1*in20 + wy00*in21 + wyp1*in22 + wyp2*in23) +
									wzp2*(wym1*in30 + wy00*in31 + wyp1*in32 + wyp2*in33)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline3_zyx(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic B-spline prefiltering and sampling in x-y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline3(image,new Axes(true,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE3X0;
			if (idims.y == 1) scale /= BSPLINE3X0;
			if (idims.z == 1) scale /= BSPLINE3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzinvxz = ixc + dz*invxz;
					final double ycdzinvyz = iyc + dz*invyz;
					final double zcdzinvzz = izc + dz*invzz;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdzinvxzdyinvxy = xcdzinvxz + dy*invxy;
						final double ycdzinvyzdyinvyy = ycdzinvyz + dy*invyy;
						final double zcdzinvzzdyinvzy = zcdzinvzz + dy*invzy;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzinvxzdyinvxy + dx*invxx)/ivx;
							final double ty = (ycdzinvyzdyinvyy + dx*invyx)/ivy;
							final double tz = (zcdzinvzzdyinvzy + dx*invzx)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy ||
								iz < -1 || iz > imaxz) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_omoms3_z(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic O-MOMS prefiltering and sampling in x-y");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.omoms3(image,new Axes(true,true,false),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic O-MOMS kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= OMOMS3X0;
			if (idims.y == 1) scale /= OMOMS3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdysinaz = ixc + dy*sinaz;
						final double ycdycosaz = iyc + dy*cosaz;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdysinaz + dx*cosaz)/ivx;
							final double ty = (ycdycosaz - dx*sinaz)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_omoms3_y(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic O-MOMS prefiltering and sampling in x-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.omoms3(image,new Axes(true,false,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic O-MOMS kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= OMOMS3X0;
			if (idims.z == 1) scale /= OMOMS3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzsinay = ixc - dz*sinay;
					final double zcdzcosay = izc + dz*cosay;
					for (co.y=ci.y=0; co.y<odims.y; ++co.y, ++ci.y) {
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzsinay + dx*cosay)/ivx;
							final double tz = (zcdzcosay + dx*sinay)/ivz;
							final int ix = FMath.floor(tx);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iz < -1 || iz > imaxz) {
								row[x] = background;
							} else {
								final double xdiff = tx - ix;
								final double xmdiff = 1 - xdiff;
								final double wxm1 = xmdiff*(D1O42 + D1O6*xmdiff*xmdiff);
								final double wx00 = D13O21 + xdiff*(D1O14 + xdiff*(D1O2*xdiff - 1));
								final double wxp1 = D13O21 + xmdiff*(D1O14 + xmdiff*(D1O2*xmdiff - 1));
								final double wxp2 = xdiff*(D1O42 + D1O6*xdiff*xdiff);
								final double zdiff = tz - iz;
								final double zmdiff = 1 - zdiff;
								final double wzm1 = zmdiff*(D1O42 + D1O6*zmdiff*zmdiff);
								final double wz00 = D13O21 + zdiff*(D1O14 + zdiff*(D1O2*zdiff - 1));
								final double wzp1 = D13O21 + zmdiff*(D1O14 + zmdiff*(D1O2*zmdiff - 1));
								final double wzp2 = zdiff*(D1O42 + D1O6*zdiff*zdiff);
								ci.x = borders.x + ix - 1;
								ci.z = borders.z + iz - 1;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.z;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.z;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci);
								row[x] = (
									wzm1*(wxm1*in00 + wx00*in01 + wxp1*in02 + wxp2*in03) +
									wz00*(wxm1*in10 + wx00*in11 + wxp1*in12 + wxp2*in13) +
									wzp1*(wxm1*in20 + wx00*in21 + wxp1*in22 + wxp2*in23) +
									wzp2*(wxm1*in30 + wx00*in31 + wxp1*in32 + wxp2*in33)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_omoms3_x(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic O-MOMS prefiltering and sampling in y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.omoms3(image,new Axes(false,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic O-MOMS kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.y == 1) scale /= OMOMS3X0;
			if (idims.z == 1) scale /= OMOMS3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double ycdzsinax = iyc + dz*sinax;
					final double zcdzcosax = izc + dz*cosax;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double ty = (ycdzsinax + dy*cosax)/ivy;
						final double tz = (zcdzcosax - dy*sinax)/ivz;
						final int iy = FMath.floor(ty);
						final int iz = FMath.floor(tz);
						if (iy < -1 || iy > imaxy ||
							iz < -1 || iz > imaxz) {
							for (int x=0; x<odims.x; ++x)
								row[x] = background;
						} else {
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
							ci.y = borders.y + iy - 1;
							ci.z = borders.z + iz - 1;
							ci.x = 0;
							for (int x=0; x<odims.x; ++x, ++ci.x) {
								final double in00 = image.get(ci); ++ci.y;
								final double in01 = image.get(ci); ++ci.y;
								final double in02 = image.get(ci); ++ci.y;
								final double in03 = image.get(ci); ++ci.z;
								final double in13 = image.get(ci); --ci.y;
								final double in12 = image.get(ci); --ci.y;
								final double in11 = image.get(ci); --ci.y;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.y;
								final double in21 = image.get(ci); ++ci.y;
								final double in22 = image.get(ci); ++ci.y;
								final double in23 = image.get(ci); ++ci.z;
								final double in33 = image.get(ci); --ci.y;
								final double in32 = image.get(ci); --ci.y;
								final double in31 = image.get(ci); --ci.y;
								final double in30 = image.get(ci); ci.z -= 3;
								row[x] = (
									wzm1*(wym1*in00 + wy00*in01 + wyp1*in02 + wyp2*in03) +
									wz00*(wym1*in10 + wy00*in11 + wyp1*in12 + wyp2*in13) +
									wzp1*(wym1*in20 + wy00*in21 + wyp1*in22 + wyp2*in23) +
									wzp2*(wym1*in30 + wy00*in31 + wyp1*in32 + wyp2*in33)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_omoms3_zyx(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Cubic O-MOMS prefiltering and sampling in x-y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.omoms3(image,new Axes(true,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the cubic O-MOMS kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= OMOMS3X0;
			if (idims.y == 1) scale /= OMOMS3X0;
			if (idims.z == 1) scale /= OMOMS3X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzinvxz = ixc + dz*invxz;
					final double ycdzinvyz = iyc + dz*invyz;
					final double zcdzinvzz = izc + dz*invzz;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdzinvxzdyinvxy = xcdzinvxz + dy*invxy;
						final double ycdzinvyzdyinvyy = ycdzinvyz + dy*invyy;
						final double zcdzinvzzdyinvzy = zcdzinvzz + dy*invzy;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzinvxzdyinvxy + dx*invxx)/ivx;
							final double ty = (ycdzinvyzdyinvyy + dx*invyx)/ivy;
							final double tz = (zcdzinvzzdyinvzy + dx*invzx)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy ||
								iz < -1 || iz > imaxz) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline5_z(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Quintic B-spline prefiltering and sampling in x-y");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline5(image,new Axes(true,true,false),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the quintic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE5X0;
			if (idims.y == 1) scale /= BSPLINE5X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0, ci.z=0; co.z<odims.z; ++co.z, ++ci.z) {
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdysinaz = ixc + dy*sinaz;
						final double ycdycosaz = iyc + dy*cosaz;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdysinaz + dx*cosaz)/ivx;
							final double ty = (ycdycosaz - dx*sinaz)/ivy;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy) {
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
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline5_y(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Quintic B-spline prefiltering and sampling in x-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline5(image,new Axes(true,false,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the quintic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE5X0;
			if (idims.z == 1) scale /= BSPLINE5X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzsinay = ixc - dz*sinay;
					final double zcdzcosay = izc + dz*cosay;
					for (co.y=ci.y=0; co.y<odims.y; ++co.y, ++ci.y) {
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzsinay + dx*cosay)/ivx;
							final double tz = (zcdzcosay + dx*sinay)/ivz;
							final int ix = FMath.floor(tx);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iz < -1 || iz > imaxz) {
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
								ci.z = borders.z + iz - 2;
								final double in00 = image.get(ci); ++ci.x;
								final double in01 = image.get(ci); ++ci.x;
								final double in02 = image.get(ci); ++ci.x;
								final double in03 = image.get(ci); ++ci.x;
								final double in04 = image.get(ci); ++ci.x;
								final double in05 = image.get(ci); ++ci.z;
								final double in15 = image.get(ci); --ci.x;
								final double in14 = image.get(ci); --ci.x;
								final double in13 = image.get(ci); --ci.x;
								final double in12 = image.get(ci); --ci.x;
								final double in11 = image.get(ci); --ci.x;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.x;
								final double in21 = image.get(ci); ++ci.x;
								final double in22 = image.get(ci); ++ci.x;
								final double in23 = image.get(ci); ++ci.x;
								final double in24 = image.get(ci); ++ci.x;
								final double in25 = image.get(ci); ++ci.z;
								final double in35 = image.get(ci); --ci.x;
								final double in34 = image.get(ci); --ci.x;
								final double in33 = image.get(ci); --ci.x;
								final double in32 = image.get(ci); --ci.x;
								final double in31 = image.get(ci); --ci.x;
								final double in30 = image.get(ci); ++ci.z;
								final double in40 = image.get(ci); ++ci.x;
								final double in41 = image.get(ci); ++ci.x;
								final double in42 = image.get(ci); ++ci.x;
								final double in43 = image.get(ci); ++ci.x;
								final double in44 = image.get(ci); ++ci.x;
								final double in45 = image.get(ci); ++ci.z;
								final double in55 = image.get(ci); --ci.x;
								final double in54 = image.get(ci); --ci.x;
								final double in53 = image.get(ci); --ci.x;
								final double in52 = image.get(ci); --ci.x;
								final double in51 = image.get(ci); --ci.x;
								final double in50 = image.get(ci);
								row[x] = (
									wzm2*(wxm2*in00 + wxm1*in01 + wx00*in02 + wxp1*in03 + wxp2*in04 + wxp3*in05) +
									wzm1*(wxm2*in10 + wxm1*in11 + wx00*in12 + wxp1*in13 + wxp2*in14 + wxp3*in15) +
									wz00*(wxm2*in20 + wxm1*in21 + wx00*in22 + wxp1*in23 + wxp2*in24 + wxp3*in25) +
									wzp1*(wxm2*in30 + wxm1*in31 + wx00*in32 + wxp1*in33 + wxp2*in34 + wxp3*in35) +
									wzp2*(wxm2*in40 + wxm1*in41 + wx00*in42 + wxp1*in43 + wxp2*in44 + wxp3*in45) +
									wzp3*(wxm2*in50 + wxm1*in51 + wx00*in52 + wxp1*in53 + wxp2*in54 + wxp3*in55)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline5_x(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Quintic B-spline prefiltering and sampling in y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline5(image,new Axes(false,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the quintic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.y == 1) scale /= BSPLINE5X0;
			if (idims.z == 1) scale /= BSPLINE5X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[] row = new double[odims.x];
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double ycdzsinax = iyc + dz*sinax;
					final double zcdzcosax = izc + dz*cosax;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double ty = (ycdzsinax + dy*cosax)/ivy;
						final double tz = (zcdzcosax - dy*sinax)/ivz;
						final int iy = FMath.floor(ty);
						final int iz = FMath.floor(tz);
						if (iy < -1 || iy > imaxy ||
							iz < -1 || iz > imaxz) {
							for (int x=0; x<odims.x; ++x)
								row[x] = background;
						} else {
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
							ci.y = borders.y + iy - 2;
							ci.z = borders.z + iz - 2;
							ci.x = 0;
							for (int x=0; x<odims.x; ++x, ++ci.x) {
								final double in00 = image.get(ci); ++ci.y;
								final double in01 = image.get(ci); ++ci.y;
								final double in02 = image.get(ci); ++ci.y;
								final double in03 = image.get(ci); ++ci.y;
								final double in04 = image.get(ci); ++ci.y;
								final double in05 = image.get(ci); ++ci.z;
								final double in15 = image.get(ci); --ci.y;
								final double in14 = image.get(ci); --ci.y;
								final double in13 = image.get(ci); --ci.y;
								final double in12 = image.get(ci); --ci.y;
								final double in11 = image.get(ci); --ci.y;
								final double in10 = image.get(ci); ++ci.z;
								final double in20 = image.get(ci); ++ci.y;
								final double in21 = image.get(ci); ++ci.y;
								final double in22 = image.get(ci); ++ci.y;
								final double in23 = image.get(ci); ++ci.y;
								final double in24 = image.get(ci); ++ci.y;
								final double in25 = image.get(ci); ++ci.z;
								final double in35 = image.get(ci); --ci.y;
								final double in34 = image.get(ci); --ci.y;
								final double in33 = image.get(ci); --ci.y;
								final double in32 = image.get(ci); --ci.y;
								final double in31 = image.get(ci); --ci.y;
								final double in30 = image.get(ci); ++ci.z;
								final double in40 = image.get(ci); ++ci.y;
								final double in41 = image.get(ci); ++ci.y;
								final double in42 = image.get(ci); ++ci.y;
								final double in43 = image.get(ci); ++ci.y;
								final double in44 = image.get(ci); ++ci.y;
								final double in45 = image.get(ci); ++ci.z;
								final double in55 = image.get(ci); --ci.y;
								final double in54 = image.get(ci); --ci.y;
								final double in53 = image.get(ci); --ci.y;
								final double in52 = image.get(ci); --ci.y;
								final double in51 = image.get(ci); --ci.y;
								final double in50 = image.get(ci); ci.z -= 5;
								row[x] = (
									wzm2*(wym2*in00 + wym1*in01 + wy00*in02 + wyp1*in03 + wyp2*in04 + wyp3*in05) +
									wzm1*(wym2*in10 + wym1*in11 + wy00*in12 + wyp1*in13 + wyp2*in14 + wyp3*in15) +
									wz00*(wym2*in20 + wym1*in21 + wy00*in22 + wyp1*in23 + wyp2*in24 + wyp3*in25) +
									wzp1*(wym2*in30 + wym1*in31 + wy00*in32 + wyp1*in33 + wyp2*in34 + wyp3*in35) +
									wzp2*(wym2*in40 + wym1*in41 + wy00*in42 + wyp1*in43 + wyp2*in44 + wyp3*in45) +
									wzp3*(wym2*in50 + wym1*in51 + wy00*in52 + wyp1*in53 + wyp2*in54 + wyp3*in55)
								);
							}
						}
						rotated.set(co,row);
						progressor.step();
					}
				}
			}
		}
		progressor.stop();
	}
	
	private void rotate_bspline5_zyx(final Image image, final Image rotated) {
		
		// Initialization:
		messenger.log("Quintic B-spline prefiltering and sampling in x-y-z");
		progressor.status("Rotating"+component+"...");
		progressor.steps(odims.c*odims.t*odims.z*odims.y);
		prefilter.bspline5(image,new Axes (true,true,true),borders);
		
		if (antialias) {
			// If any of the dimensions equals one, the prefiltering
			// operation will not have been carried out in that dimension.
			// Subsequent application of the quintic B-spline kernel in that
			// dimension will result in an overall down-scaling of the
			// grey-values, which should be corrected for:
			double scale = 1;
			if (idims.x == 1) scale /= BSPLINE5X0;
			if (idims.y == 1) scale /= BSPLINE5X0;
			if (idims.z == 1) scale /= BSPLINE5X0;
			if (scale != 1) {
				messenger.log("Correction scaling with factor "+scale);
				image.multiply(scale);
			}
			image.set(borders,background);
			
		} else image.mirror(borders);
		
		// Rotate using the inverse of the rotation matrix:
		final Coordinates ci = new Coordinates();
		final Coordinates co = new Coordinates();
		final double[][][] ain = new double[6][6][6];
		final double[] row = new double[odims.x];
		image.axes(Axes.X+Axes.Y+Axes.Z);
		rotated.axes(Axes.X);
		
		progressor.start();
		for (co.c=ci.c=0; co.c<odims.c; ++co.c, ++ci.c) {
			for (co.t=ci.t=0; co.t<odims.t; ++co.t, ++ci.t) {
				for (co.z=0; co.z<odims.z; ++co.z) {
					final double dz = co.z*ovz - ozc;
					final double xcdzinvxz = ixc + dz*invxz;
					final double ycdzinvyz = iyc + dz*invyz;
					final double zcdzinvzz = izc + dz*invzz;
					for (co.y=0; co.y<odims.y; ++co.y) {
						final double dy = co.y*ovy - oyc;
						final double xcdzinvxzdyinvxy = xcdzinvxz + dy*invxy;
						final double ycdzinvyzdyinvyy = ycdzinvyz + dy*invyy;
						final double zcdzinvzzdyinvzy = zcdzinvzz + dy*invzy;
						for (int x=0; x<odims.x; ++x) {
							final double dx = x*ovx - oxc;
							final double tx = (xcdzinvxzdyinvxy + dx*invxx)/ivx;
							final double ty = (ycdzinvyzdyinvyy + dx*invyx)/ivy;
							final double tz = (zcdzinvzzdyinvzy + dx*invzx)/ivz;
							final int ix = FMath.floor(tx);
							final int iy = FMath.floor(ty);
							final int iz = FMath.floor(tz);
							if (ix < -1 || ix > imaxx ||
								iy < -1 || iy > imaxy ||
								iz < -1 || iz > imaxz) {
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
										wyp3*(wxm2*ain[2][5][0] + wxm1*ain[2][5][1] + wx00*ain[2][5][2] + wxp1*ain[2][5][3] + wxp2*ain[2][5][4] + wxp3*ain[2][5][5])) +
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
						rotated.set(co,row);
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
	
	private int imaxx, imaxy, imaxz;
	private int interpolation;
	
	private double ixc, iyc, izc;
	private double ivx, ivy, ivz;
	private double oxc, oyc, ozc;
	private double ovx, ovy, ovz;
	private double zangle, yangle, xangle;
	private double cosax, sinax, cosay, sinay, cosaz, sinaz;
	private double invxx, invxy, invxz, invyx, invyy, invyz, invzx, invzy, invzz;
	
	private Borders borders;
	
	private boolean antialias;
	
	private String component = "";
	
	private final static double D1O2 = 1.0/2.0;
	private final static double D1O4 = 1.0/4.0;
	private final static double D1O6 = 1.0/6.0;
	private final static double D1O12 = 1.0/12.0;
	private final static double D1O14 = 1.0/14.0;
	private final static double D1O24 = 1.0/24.0;
	private final static double D1O42 = 1.0/42.0;
	private final static double D1O120 = 1.0/120.0;
	private final static double D2O3 = 2.0/3.0;
	private final static double D3O2 = 3.0/2.0;
	private final static double D5O2 = 5.0/2.0;
	private final static double D11O20 = 11.0/20.0;
	private final static double D13O21 = 13.0/21.0;
	private final static double DM1O2 = -1.0/2.0;
	
	private final static double BSPLINE3X0 = 0.666666666667;
	private final static double BSPLINE5X0 = 0.55;
	private final static double OMOMS3X0 = 0.619047619048;
	
}
