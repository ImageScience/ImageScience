package imagescience.transform;

import imagescience.ImageScience;

import imagescience.image.Aspects;
import imagescience.image.Axes;
import imagescience.image.Borders;
import imagescience.image.ColorImage;
import imagescience.image.Coordinates;
import imagescience.image.Dimensions;
import imagescience.image.Image;

import imagescience.utility.FMath;
import imagescience.utility.Messenger;
import imagescience.utility.Progressor;
import imagescience.utility.Timer;

/** Scales an image using different interpolation schemes.
	
	<p><b>References:</b></p>
	
	<p>[1] R. G. Keys, "Cubic Convolution Interpolation for Digital Image Processing", <em>IEEE Transactions on Acoustics, Speech, and Signal Processing</em>, vol. 29, no. 6, December 1981, pp. 1153-1160.</p>
	
	<p>[2] M. Unser, "Splines: A Perfect Fit for Signal and Image Processing", <em>IEEE Signal Processing Magazine</em>, vol. 16, no. 6, November 1999, pp. 22-38.</p>
	
	<p>[3] P. Thevenaz, T. Blu, M. Unser, "Interpolation Revisited", <em>IEEE Transactions on Medical Imaging</em>, vol. 19, no. 7, July 2000, pp.739-758.</p>
	
	<p>[4] E. Meijering, W. Niessen, M. Viergever, "Quantitative Evaluation of Convolution-Based Methods for Medical Image Interpolation", <em>Medical Image Analysis</em>, vol. 5, no. 2, June 2001, pp. 111-126.</p>
	
	<p>[5] T. Blu, P. Thevenaz, M. Unser, "MOMS: Maximal-Order Interpolation of Minimal Support", <em>IEEE Transactions on Image Processing</em>, vol. 10, no. 7, July 2001, pp. 1069-1080.</p>
*/
public class Scale {
	
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
	public Scale() { }
	
	/** Scales an image.
		
		@param image The input image to be scaled. For images of type {@link ColorImage}, the color components are processed separately by the method.
		
		@param xfactor The scaling factor in the x-dimension.
		
		@param yfactor The scaling factor in the y-dimension.
		
		@param zfactor The scaling factor in the z-dimension.
		
		@param tfactor The scaling factor in the t-dimension.
		
		@param cfactor The scaling factor in the c-dimension.
		
		@param interpolation The interpolation scheme to be used. Must be equal to one of the static fields of this class.
		
		@return A new image containing a scaled version of the input image. The returned image is of the same type as the input image.
		
		@throws IllegalArgumentException If any of the scaling factors is less than or equal to {@code 0}, or if the requested {@code interpolation} scheme is not supported.
		
		@throws IllegalStateException If the x-, y-, or z-aspect size of {@code image} is less than or equal to {@code 0}.
		
		@throws NullPointerException If {@code image} is {@code null}.
		
		@throws UnknownError If for any reason the output image can not be created. In most cases this will be due to insufficient free memory.
	*/
	public synchronized Image run(
		final Image image,
		final double xfactor,
		final double yfactor,
		final double zfactor,
		final double tfactor,
		final double cfactor,
		final int interpolation
	) {
		
		messenger.log(ImageScience.prelude()+"Scale");
		
		// Initialize:
		final Timer timer = new Timer();
		timer.messenger.log(messenger.log());
		timer.start();
		
		initialize(image,xfactor,yfactor,zfactor,tfactor,cfactor,interpolation);
		
		// Scale:
		messenger.log("Scaling "+image.type());
		Image scaled = null;
		if (image instanceof ColorImage) {
			messenger.log("Processing RGB-color components separately");
			final ColorImage colimage = (ColorImage)image;
			
			progressor.range(0,0.33);
			component = " red component";
			messenger.log("Scaling"+component);
			colimage.component(ColorImage.RED);
			Image comp = colimage.get(); comp = scale(comp);
			final ColorImage cscaled = new ColorImage(comp.dimensions());
			cscaled.component(ColorImage.RED);
			cscaled.set(comp);
			
			progressor.range(0.33,0.67);
			component = " green component";
			messenger.log("Scaling"+component);
			colimage.component(ColorImage.GREEN);
			comp = colimage.get(); comp = scale(comp);
			cscaled.component(ColorImage.GREEN);
			cscaled.set(comp);
			
			progressor.range(0.67,1);
			component = " blue component";
			messenger.log("Scaling"+component);
			colimage.component(ColorImage.BLUE);
			comp = colimage.get(); comp = scale(comp);
			cscaled.component(ColorImage.BLUE);
			cscaled.set(comp);
			
			scaled = cscaled;
			
		} else {
			component = "";
			progressor.range(0,1);
			scaled = scale(image);
		}
		
		// Finish up:
		scaled.name(image.name()+" scaled");
		scaled.aspects(image.aspects().duplicate());
		timer.stop();
		
		return scaled;
	}
	
	private Image scale(final Image image) {
		
		// No need to scale if all factors are unity:
		if (xfactor == 1 && yfactor == 1 && zfactor == 1 && tfactor == 1 && cfactor == 1) {
			messenger.log("All scaling factors are unity");
			messenger.log("Returning a copy of the input image");
			return image.duplicate();
		}
		
		final Dimensions indims = image.dimensions();
		Image ximage = null, yimage = null, zimage = null, timage = null, cimage = null;
		
		progressor.steps(
			(xfactor != 1 ? indims.c*indims.t*indims.z*indims.y*newdims.x : 0) +
			(yfactor != 1 ? indims.c*indims.t*indims.z*newdims.y*newdims.x : 0) +
			(zfactor != 1 ? indims.c*indims.t*newdims.z*newdims.y*newdims.x : 0) +
			(tfactor != 1 ? indims.c*newdims.t*newdims.z*newdims.y*newdims.x : 0) +
			(cfactor != 1 ? newdims.c*newdims.t*newdims.z*newdims.y*newdims.x : 0)
		);
		progressor.status("Scaling...");
		progressor.start();
		
		// Scaling in x-dimension: *********************************************
		if (xfactor == 1) {
			messenger.log("Skipping scaling in x-dimension");
			ximage = image;
		} else {
			logus("Scaling"+component+" in x-dimension");
			initialize(xscheme,newdims.x,xfactor,xshift);
			final double[] ain = new double[indims.x + 2*borders.x];
			final double[] anew = new double[newdims.x];
			final Coordinates cin = new Coordinates(); cin.x = -borders.x;
			final Coordinates cnew = new Coordinates();
			final int maxbx = indims.x + 2*borders.x - 1;
			ximage = Image.create(new Dimensions(newdims.x,indims.y,indims.z,indims.t,indims.c),image.type());
			ximage.axes(Axes.X); image.axes(Axes.X);
			
			switch (xscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in x-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									for (int x=0; x<newdims.x; ++x)
										anew[x] = ain[pos[x]];
									ximage.set(cnew,anew);
									progressor.step(newdims.x);
								}
								break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in x-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									ain[0] = ain[2]; ain[maxbx] = ain[maxbx-2];
									for (int x=0; x<newdims.x; ++x) {
										final int lpos = borders.x + pos[x];
										anew[x] = (
											kernel[x][0]*ain[lpos] +
											kernel[x][1]*ain[lpos+1]
										);
									}
									ximage.set(cnew,anew);
									progressor.step(newdims.x);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in x-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									ain[0] = ain[4]; ain[maxbx] = ain[maxbx-4];
									ain[1] = ain[3]; ain[maxbx-1] = ain[maxbx-3];
									for (int x=0; x<newdims.x; ++x) {
										final int lpos = borders.x + pos[x];
										anew[x] = (
											kernel[x][0]*ain[lpos-1] +
											kernel[x][1]*ain[lpos] +
											kernel[x][2]*ain[lpos+1] +
											kernel[x][3]*ain[lpos+2]
										);
									}
									ximage.set(cnew,anew);
									progressor.step(newdims.x);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in x-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									prefilter.bspline3(ain,borders.x);
									ain[0] = ain[4]; ain[maxbx] = ain[maxbx-4];
									ain[1] = ain[3]; ain[maxbx-1] = ain[maxbx-3];
									for (int x=0; x<newdims.x; ++x) {
										final int lpos = borders.x + pos[x];
										anew[x] = (
											kernel[x][0]*ain[lpos-1] +
											kernel[x][1]*ain[lpos] +
											kernel[x][2]*ain[lpos+1] +
											kernel[x][3]*ain[lpos+2]
										);
									}
									ximage.set(cnew,anew);
									progressor.step(newdims.x);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in x-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									prefilter.omoms3(ain,borders.x);
									ain[0] = ain[4]; ain[maxbx] = ain[maxbx-4];
									ain[1] = ain[3]; ain[maxbx-1] = ain[maxbx-3];
									for (int x=0; x<newdims.x; ++x) {
										final int lpos = borders.x + pos[x];
										anew[x] = (
											kernel[x][0]*ain[lpos-1] +
											kernel[x][1]*ain[lpos] +
											kernel[x][2]*ain[lpos+1] +
											kernel[x][3]*ain[lpos+2]
										);
									}
									ximage.set(cnew,anew);
									progressor.step(newdims.x);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in x-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									prefilter.bspline5(ain,borders.x);
									ain[0] = ain[6]; ain[maxbx] = ain[maxbx-6];
									ain[1] = ain[5]; ain[maxbx-1] = ain[maxbx-5];
									ain[2] = ain[4]; ain[maxbx-2] = ain[maxbx-4];
									for (int x=0; x<newdims.x; ++x) {
										final int lpos = borders.x + pos[x];
										anew[x] = (
											kernel[x][0]*ain[lpos-2] +
											kernel[x][1]*ain[lpos-1] +
											kernel[x][2]*ain[lpos] +
											kernel[x][3]*ain[lpos+1] +
											kernel[x][4]*ain[lpos+2] +
											kernel[x][5]*ain[lpos+3]
										);
									}
									ximage.set(cnew,anew);
									progressor.step(newdims.x);
								}
					break;
				}
			}
		}
		
		// Scaling in y-dimension: *********************************************
		if (yfactor == 1) {
			messenger.log("Skipping scaling in y-dimension");
			yimage = ximage;
		} else {
			logus("Scaling"+component+" in y-dimension");
			initialize(yscheme,newdims.y,yfactor,yshift);
			final double[] ain = new double[indims.y + 2*borders.y];
			final double[] anew = new double[newdims.y];
			final Coordinates cin = new Coordinates(); cin.y = -borders.y;
			final Coordinates cnew = new Coordinates();
			final int maxby = indims.y + 2*borders.y - 1;
			yimage = Image.create(new Dimensions(newdims.x,newdims.y,indims.z,indims.t,indims.c),image.type());
			yimage.axes(Axes.Y); ximage.axes(Axes.Y);
			
			switch (yscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in y-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									for (int y=0; y<newdims.y; ++y)
										anew[y] = ain[pos[y]];
									yimage.set(cnew,anew);
									progressor.step(newdims.y);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in y-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									ain[0] = ain[2]; ain[maxby] = ain[maxby-2];
									for (int y=0; y<newdims.y; ++y) {
										final int lpos = borders.y + pos[y];
										anew[y] = (
											kernel[y][0]*ain[lpos] +
											kernel[y][1]*ain[lpos+1]
										);
									}
									yimage.set(cnew,anew);
									progressor.step(newdims.y);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in y-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									ain[0] = ain[4]; ain[maxby] = ain[maxby-4];
									ain[1] = ain[3]; ain[maxby-1] = ain[maxby-3];
									for (int y=0; y<newdims.y; ++y) {
										final int lpos = borders.y + pos[y];
										anew[y] = (
											kernel[y][0]*ain[lpos-1] +
											kernel[y][1]*ain[lpos] +
											kernel[y][2]*ain[lpos+1] +
											kernel[y][3]*ain[lpos+2]
										);
									}
									yimage.set(cnew,anew);
									progressor.step(newdims.y);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in y-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									prefilter.bspline3(ain,borders.y);
									ain[0] = ain[4]; ain[maxby] = ain[maxby-4];
									ain[1] = ain[3]; ain[maxby-1] = ain[maxby-3];
									for (int y=0; y<newdims.y; ++y) {
										final int lpos = borders.y + pos[y];
										anew[y] = (
											kernel[y][0]*ain[lpos-1] +
											kernel[y][1]*ain[lpos] +
											kernel[y][2]*ain[lpos+1] +
											kernel[y][3]*ain[lpos+2]
										);
									}
									yimage.set(cnew,anew);
									progressor.step(newdims.y);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in y-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									prefilter.omoms3(ain,borders.y);
									ain[0] = ain[4]; ain[maxby] = ain[maxby-4];
									ain[1] = ain[3]; ain[maxby-1] = ain[maxby-3];
									for (int y=0; y<newdims.y; ++y) {
										final int lpos = borders.y + pos[y];
										anew[y] = (
											kernel[y][0]*ain[lpos-1] +
											kernel[y][1]*ain[lpos] +
											kernel[y][2]*ain[lpos+1] +
											kernel[y][3]*ain[lpos+2]
										);
									}
									yimage.set(cnew,anew);
									progressor.step(newdims.y);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in y-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=0, cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									prefilter.bspline5(ain,borders.y);
									ain[0] = ain[6]; ain[maxby] = ain[maxby-6];
									ain[1] = ain[5]; ain[maxby-1] = ain[maxby-5];
									ain[2] = ain[4]; ain[maxby-2] = ain[maxby-4];
									for (int y=0; y<newdims.y; ++y) {
										final int lpos = borders.y + pos[y];
										anew[y] = (
											kernel[y][0]*ain[lpos-2] +
											kernel[y][1]*ain[lpos-1] +
											kernel[y][2]*ain[lpos] +
											kernel[y][3]*ain[lpos+1] +
											kernel[y][4]*ain[lpos+2] +
											kernel[y][5]*ain[lpos+3]
										);
									}
									yimage.set(cnew,anew);
									progressor.step(newdims.y);
								}
					break;
				}
			}
		}
		// Disconnect handle so that the GC might release the memory:
		ximage = null;
		
		// Scaling in z-dimension: *********************************************
		if (zfactor == 1) {
			messenger.log("Skipping scaling in z-dimension");
			zimage = yimage;
		} else {
			logus("Scaling"+component+" in z-dimension");
			initialize(zscheme,newdims.z,zfactor,zshift);
			final double[] ain = new double[indims.z + 2*borders.z];
			final double[] anew = new double[newdims.z];
			final Coordinates cin = new Coordinates(); cin.z = -borders.z;
			final Coordinates cnew = new Coordinates();
			final int maxbz = indims.z + 2*borders.z - 1;
			zimage = Image.create(new Dimensions(newdims.x,newdims.y,newdims.z,indims.t,indims.c),image.type());
			zimage.axes(Axes.Z); yimage.axes(Axes.Z);
			
			switch (zscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in z-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									for (int z=0; z<newdims.z; ++z) anew[z] = ain[pos[z]];
									zimage.set(cnew,anew);
									progressor.step(newdims.z);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in z-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									ain[0] = ain[2]; ain[maxbz] = ain[maxbz-2];
									for (int z=0; z<newdims.z; ++z) {
										final int lpos = borders.z + pos[z];
										anew[z] = (
											kernel[z][0]*ain[lpos] +
											kernel[z][1]*ain[lpos+1]
										);
									}
									zimage.set(cnew,anew);
									progressor.step(newdims.z);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in z-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									ain[0] = ain[4]; ain[maxbz] = ain[maxbz-4];
									ain[1] = ain[3]; ain[maxbz-1] = ain[maxbz-3];
									for (int z=0; z<newdims.z; ++z) {
										final int lpos = borders.z + pos[z];
										anew[z] = (
											kernel[z][0]*ain[lpos-1] +
											kernel[z][1]*ain[lpos] +
											kernel[z][2]*ain[lpos+1] +
											kernel[z][3]*ain[lpos+2]
										);
									}
									zimage.set(cnew,anew);
									progressor.step(newdims.z);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in z-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									prefilter.bspline3(ain,borders.z);
									ain[0] = ain[4]; ain[maxbz] = ain[maxbz-4];
									ain[1] = ain[3]; ain[maxbz-1] = ain[maxbz-3];
									for (int z=0; z<newdims.z; ++z) {
										final int lpos = borders.z + pos[z];
										anew[z] = (
											kernel[z][0]*ain[lpos-1] +
											kernel[z][1]*ain[lpos] +
											kernel[z][2]*ain[lpos+1] +
											kernel[z][3]*ain[lpos+2]
										);
									}
									zimage.set(cnew,anew);
									progressor.step(newdims.z);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in z-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									prefilter.omoms3(ain,borders.z);
									ain[0] = ain[4]; ain[maxbz] = ain[maxbz-4];
									ain[1] = ain[3]; ain[maxbz-1] = ain[maxbz-3];
									for (int z=0; z<newdims.z; ++z) {
										final int lpos = borders.z + pos[z];
										anew[z] = (
											kernel[z][0]*ain[lpos-1] +
											kernel[z][1]*ain[lpos] +
											kernel[z][2]*ain[lpos+1] +
											kernel[z][3]*ain[lpos+2]
										);
									}
									zimage.set(cnew,anew);
									progressor.step(newdims.z);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in z-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=0, cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									prefilter.bspline5(ain,borders.z);
									ain[0] = ain[6]; ain[maxbz] = ain[maxbz-6];
									ain[1] = ain[5]; ain[maxbz-1] = ain[maxbz-5];
									ain[2] = ain[4]; ain[maxbz-2] = ain[maxbz-4];
									for (int z=0; z<newdims.z; ++z) {
										final int lpos = borders.z + pos[z];
										anew[z] = (
											kernel[z][0]*ain[lpos-2] +
											kernel[z][1]*ain[lpos-1] +
											kernel[z][2]*ain[lpos] +
											kernel[z][3]*ain[lpos+1] +
											kernel[z][4]*ain[lpos+2] +
											kernel[z][5]*ain[lpos+3]
										);
									}
									zimage.set(cnew,anew);
									progressor.step(newdims.z);
								}
					break;
				}
			}
		}
		// Disconnect handle so that the GC might release the memory:
		yimage = null;
		
		// Scaling in t-dimension: *********************************************
		if (tfactor == 1) {
			messenger.log("Skipping scaling in t-dimension");
			timage = zimage;
		} else {
			logus("Scaling"+component+" in t-dimension");
			initialize(tscheme,newdims.t,tfactor,tshift);
			final double[] ain = new double[indims.t + 2*borders.t];
			final double[] anew = new double[newdims.t];
			final Coordinates cin = new Coordinates(); cin.t = -borders.t;
			final Coordinates cnew = new Coordinates();
			final int maxbt = indims.t + 2*borders.t - 1;
			timage = Image.create(new Dimensions(newdims.x,newdims.y,newdims.z,newdims.t,indims.c),image.type());
			timage.axes(Axes.T); zimage.axes(Axes.T);
			
			switch (tscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in t-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									zimage.get(cin,ain);
									for (int t=0; t<newdims.t; ++t) anew[t] = ain[pos[t]];
									timage.set(cnew,anew);
									progressor.step(newdims.t);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in t-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									zimage.get(cin,ain);
									ain[0] = ain[2]; ain[maxbt] = ain[maxbt-2];
									for (int t=0; t<newdims.t; ++t) {
										final int lpos = borders.t + pos[t];
										anew[t] = (
											kernel[t][0]*ain[lpos] +
											kernel[t][1]*ain[lpos+1]
										);
									}
									timage.set(cnew,anew);
									progressor.step(newdims.t);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in t-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									zimage.get(cin,ain);
									ain[0] = ain[4]; ain[maxbt] = ain[maxbt-4];
									ain[1] = ain[3]; ain[maxbt-1] = ain[maxbt-3];
									for (int t=0; t<newdims.t; ++t) {
										final int lpos = borders.t + pos[t];
										anew[t] = (
											kernel[t][0]*ain[lpos-1] +
											kernel[t][1]*ain[lpos] +
											kernel[t][2]*ain[lpos+1] +
											kernel[t][3]*ain[lpos+2]
										);
									}
									timage.set(cnew,anew);
									progressor.step(newdims.t);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in t-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									zimage.get(cin,ain);
									prefilter.bspline3(ain,borders.t);
									ain[0] = ain[4]; ain[maxbt] = ain[maxbt-4];
									ain[1] = ain[3]; ain[maxbt-1] = ain[maxbt-3];
									for (int t=0; t<newdims.t; ++t) {
										final int lpos = borders.t + pos[t];
										anew[t] = (
											kernel[t][0]*ain[lpos-1] +
											kernel[t][1]*ain[lpos] +
											kernel[t][2]*ain[lpos+1] +
											kernel[t][3]*ain[lpos+2]
										);
									}
									timage.set(cnew,anew);
									progressor.step(newdims.t);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in t-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									zimage.get(cin,ain);
									prefilter.omoms3(ain,borders.t);
									ain[0] = ain[4]; ain[maxbt] = ain[maxbt-4];
									ain[1] = ain[3]; ain[maxbt-1] = ain[maxbt-3];
									for (int t=0; t<newdims.t; ++t) {
										final int lpos = borders.t + pos[t];
										anew[t] = (
											kernel[t][0]*ain[lpos-1] +
											kernel[t][1]*ain[lpos] +
											kernel[t][2]*ain[lpos+1] +
											kernel[t][3]*ain[lpos+2]
										);
									}
									timage.set(cnew,anew);
									progressor.step(newdims.t);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in t-dimension");
					for (cin.c=0, cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									zimage.get(cin,ain);
									prefilter.bspline5(ain,borders.t);
									ain[0] = ain[6]; ain[maxbt] = ain[maxbt-6];
									ain[1] = ain[5]; ain[maxbt-1] = ain[maxbt-5];
									ain[2] = ain[4]; ain[maxbt-2] = ain[maxbt-4];
									for (int t=0; t<newdims.t; ++t) {
										final int lpos = borders.t + pos[t];
										anew[t] = (
											kernel[t][0]*ain[lpos-2] +
											kernel[t][1]*ain[lpos-1] +
											kernel[t][2]*ain[lpos] +
											kernel[t][3]*ain[lpos+1] +
											kernel[t][4]*ain[lpos+2] +
											kernel[t][5]*ain[lpos+3]
										);
									}
									timage.set(cnew,anew);
									progressor.step(newdims.t);
								}
					break;
				}
			}
		}
		// Disconnect handle so that the GC might release the memory:
		zimage = null;
		
		// Scaling in c-dimension: *********************************************
		if (cfactor == 1) {
			messenger.log("Skipping scaling in c-dimension");
			cimage = timage;
		} else {
			logus("Scaling"+component+" in c-dimension");
			initialize(cscheme,newdims.c,cfactor,cshift);
			final double[] ain = new double[indims.c + 2*borders.c];
			final double[] anew = new double[newdims.c];
			final Coordinates cin = new Coordinates(); cin.c = -borders.c;
			final Coordinates cnew = new Coordinates();
			final int maxbc = indims.c + 2*borders.c - 1;
			cimage = Image.create(newdims,image.type());
			cimage.axes(Axes.C); timage.axes(Axes.C);
			
			switch (cscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in c-dimension");
					for (cin.t=0, cnew.t=0; cin.t<newdims.t; ++cin.t, ++cnew.t)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									timage.get(cin,ain);
									for (int c=0; c<newdims.c; ++c) anew[c] = ain[pos[c]];
									cimage.set(cnew,anew);
									progressor.step(newdims.c);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in c-dimension");
					for (cin.t=0, cnew.t=0; cin.t<newdims.t; ++cin.t, ++cnew.t)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									timage.get(cin,ain);
									ain[0] = ain[2]; ain[maxbc] = ain[maxbc-2];
									for (int c=0; c<newdims.c; ++c) {
										final int lpos = borders.c + pos[c];
										anew[c] = (
											kernel[c][0]*ain[lpos] +
											kernel[c][1]*ain[lpos+1]
										);
									}
									cimage.set(cnew,anew);
									progressor.step(newdims.c);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in c-dimension");
					for (cin.t=0, cnew.t=0; cin.t<newdims.t; ++cin.t, ++cnew.t)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									timage.get(cin,ain);
									ain[0] = ain[4]; ain[maxbc] = ain[maxbc-4];
									ain[1] = ain[3]; ain[maxbc-1] = ain[maxbc-3];
									for (int c=0; c<newdims.c; ++c) {
										final int lpos = borders.c + pos[c];
										anew[c] = (
											kernel[c][0]*ain[lpos-1] +
											kernel[c][1]*ain[lpos] +
											kernel[c][2]*ain[lpos+1] +
											kernel[c][3]*ain[lpos+2]
										);
									}
									cimage.set(cnew,anew);
									progressor.step(newdims.c);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in c-dimension");
					for (cin.t=0, cnew.t=0; cin.t<newdims.t; ++cin.t, ++cnew.t)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									timage.get(cin,ain);
									prefilter.bspline3(ain,borders.c);
									ain[0] = ain[4]; ain[maxbc] = ain[maxbc-4];
									ain[1] = ain[3]; ain[maxbc-1] = ain[maxbc-3];
									for (int c=0; c<newdims.c; ++c) {
										final int lpos = borders.c + pos[c];
										anew[c] = (
											kernel[c][0]*ain[lpos-1] +
											kernel[c][1]*ain[lpos] +
											kernel[c][2]*ain[lpos+1] +
											kernel[c][3]*ain[lpos+2]
										);
									}
									cimage.set(cnew,anew);
									progressor.step(newdims.c);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in c-dimension");
					for (cin.t=0, cnew.t=0; cin.t<newdims.t; ++cin.t, ++cnew.t)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									timage.get(cin,ain);
									prefilter.omoms3(ain,borders.c);
									ain[0] = ain[4]; ain[maxbc] = ain[maxbc-4];
									ain[1] = ain[3]; ain[maxbc-1] = ain[maxbc-3];
									for (int c=0; c<newdims.c; ++c) {
										final int lpos = borders.c + pos[c];
										anew[c] = (
											kernel[c][0]*ain[lpos-1] +
											kernel[c][1]*ain[lpos] +
											kernel[c][2]*ain[lpos+1] +
											kernel[c][3]*ain[lpos+2]
										);
									}
									cimage.set(cnew,anew);
									progressor.step(newdims.c);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in c-dimension");
					for (cin.t=0, cnew.t=0; cin.t<newdims.t; ++cin.t, ++cnew.t)
						for (cin.z=0, cnew.z=0; cin.z<newdims.z; ++cin.z, ++cnew.z)
							for (cin.y=0, cnew.y=0; cin.y<newdims.y; ++cin.y, ++cnew.y)
								for (cin.x=0, cnew.x=0; cin.x<newdims.x; ++cin.x, ++cnew.x) {
									timage.get(cin,ain);
									prefilter.bspline5(ain,borders.c);
									ain[0] = ain[6]; ain[maxbc] = ain[maxbc-6];
									ain[1] = ain[5]; ain[maxbc-1] = ain[maxbc-5];
									ain[2] = ain[4]; ain[maxbc-2] = ain[maxbc-4];
									for (int c=0; c<newdims.c; ++c) {
										final int lpos = borders.c + pos[c];
										anew[c] = (
											kernel[c][0]*ain[lpos-2] +
											kernel[c][1]*ain[lpos-1] +
											kernel[c][2]*ain[lpos] +
											kernel[c][3]*ain[lpos+1] +
											kernel[c][4]*ain[lpos+2] +
											kernel[c][5]*ain[lpos+3]
										);
									}
									cimage.set(cnew,anew);
									progressor.step(newdims.c);
								}
					break;
				}
			}
		}
		
		progressor.stop();
		
		return cimage;
	}
	
	private void initialize(
		final int interpolation,
		final int newsize,
		final double factor,
		final double shift
	) {
		
		pos = new int[newsize];
		
		switch (interpolation) {
			case NEAREST: {
				for (int i=0; i<newsize; ++i)
				pos[i] = FMath.round(i/factor + shift);
				break;
			}
			case LINEAR: {
				kernel = new double[newsize][2];
				for (int i=0; i<newsize; ++i) {
				final double in = i/factor + shift;
				pos[i] = FMath.floor(in);
				kernel[i][1] = in - pos[i];
				kernel[i][0] = 1 - kernel[i][1];
				}
				break;
			}
			case CUBIC: {
				kernel = new double[newsize][4];
				final double DM1O2 = -1.0/2.0;
				final double D3O2 = 3.0/2.0;
				final double D5O2 = 5.0/2.0;
				for (int i=0; i<newsize; ++i) {
				final double in = i/factor + shift;
				pos[i] = FMath.floor(in);
				final double diff = in - pos[i];
				final double mdiff = 1 - diff;
				kernel[i][0] = DM1O2*diff*mdiff*mdiff;
				kernel[i][1] = 1.0f + (D3O2*diff - D5O2)*diff*diff;
				kernel[i][2] = 1.0f + (D3O2*mdiff - D5O2)*mdiff*mdiff;
				kernel[i][3] = DM1O2*mdiff*diff*diff;
				}
				break;
			}
			case BSPLINE3: {
				kernel = new double[newsize][4];
				final double D1O2 = 1.0/2.0;
				final double D1O6 = 1.0/6.0;
				final double D2O3 = 2.0/3.0;
				for (int i=0; i<newsize; ++i) {
				final double in = i/factor + shift;
				pos[i] = FMath.floor(in);
				final double diff = in - pos[i];
				final double mdiff = 1 - diff;
				kernel[i][0] = D1O6*mdiff*mdiff*mdiff;
				kernel[i][1] = D2O3 + (D1O2*diff - 1)*diff*diff;
				kernel[i][2] = D2O3 + (D1O2*mdiff - 1)*mdiff*mdiff;
				kernel[i][3] = D1O6*diff*diff*diff;
				}
				break;
			}
			case OMOMS3: {
				kernel = new double[newsize][4];
				final double D1O2 = 1.0/2.0;
				final double D1O6 = 1.0/6.0;
				final double D1O14 = 1.0/14.0;
				final double D1O42 = 1.0/42.0;
				final double D13O21 = 13.0/21.0;
				for (int i=0; i<newsize; ++i) {
				final double in = i/factor + shift;
				pos[i] = FMath.floor(in);
				final double diff = in - pos[i];
				final double mdiff = 1 - diff;
				kernel[i][0] = mdiff*(D1O42 + D1O6*mdiff*mdiff);
				kernel[i][1] = D13O21 + diff*(D1O14 + diff*(D1O2*diff - 1));
				kernel[i][2] = D13O21 + mdiff*(D1O14 + mdiff*(D1O2*mdiff - 1));
				kernel[i][3] = diff*(D1O42 + D1O6*diff*diff);
				}
				break;
			}
			case BSPLINE5: {
				kernel = new double[newsize][6];
				final double D1O2 = 1.0/2.0;
				final double D1O4 = 1.0/4.0;
				final double D1O12 = 1.0/12.0;
				final double D1O24 = 1.0/24.0;
				final double D1O120 = 1.0/120.0;
				final double D11O20 = 11.0/20.0;
				for (int i=0; i<newsize; ++i) {
				final double in = i/factor + shift;
				pos[i] = FMath.floor(in);
				final double diff = in - pos[i];
				final double diff2 = diff*diff;
				final double mdiff = 1.0f - diff;
				final double mdiff2 = mdiff*mdiff;
				kernel[i][0] = D1O120*mdiff2*mdiff2*mdiff;
				kernel[i][1] = D1O120 + D1O24*mdiff*(1 + mdiff*(2 + mdiff*(2 + mdiff - mdiff2)));
				kernel[i][2] = D11O20 + diff2*((D1O4 - D1O12*diff)*diff2 - D1O2);
				kernel[i][3] = D11O20 + mdiff2*((D1O4 - D1O12*mdiff)*mdiff2 - D1O2);
				kernel[i][4] = D1O120 + D1O24*diff*(1 + diff*(2 + diff*(2 + diff - diff2)));
				kernel[i][5] = D1O120*diff2*diff2*diff;
				}
				break;
			}
		}
	}
	
	private void initialize(
		final Image image,
		final double xfactor,
		final double yfactor,
		final double zfactor,
		final double tfactor,
		final double cfactor,
		final int interpolation
	) {
		
		// Check parameters and conditions
		if (image.aspects().x <= 0) throw new IllegalStateException("Aspect ratio in x-dimension less than or equal to 0");
		if (image.aspects().y <= 0) throw new IllegalStateException("Aspect ratio in y-dimension less than or equal to 0");
		if (image.aspects().z <= 0) throw new IllegalStateException("Aspect ratio in z-dimension less than or equal to 0");
		
		this.xfactor = xfactor; if (xfactor <= 0) throw new IllegalArgumentException("Scaling factor in x-dimension less than or equal to 0");
		this.yfactor = yfactor; if (yfactor <= 0) throw new IllegalArgumentException("Scaling factor in y-dimension less than or equal to 0");
		this.zfactor = zfactor; if (zfactor <= 0) throw new IllegalArgumentException("Scaling factor in z-dimension less than or equal to 0");
		this.tfactor = tfactor; if (tfactor <= 0) throw new IllegalArgumentException("Scaling factor in t-dimension less than or equal to 0");
		this.cfactor = cfactor; if (cfactor <= 0) throw new IllegalArgumentException("Scaling factor in c-dimension less than or equal to 0");
		
		messenger.log("Scaling factors: (x,y,z,t,c) = ("+xfactor+","+yfactor+","+zfactor+","+tfactor+","+cfactor+")");
		
		final Dimensions indims = image.dimensions();
		
		messenger.log("Input image dimensions: (x,y,z,t,c) = ("+indims.x+","+indims.y+","+indims.z+","+indims.t+","+indims.c+")");
		
		// Compute and store dimensions of scaled image:
		int newdimsx = FMath.round(indims.x*xfactor);
		int newdimsy = FMath.round(indims.y*yfactor);
		int newdimsz = FMath.round(indims.z*zfactor);
		int newdimst = FMath.round(indims.t*tfactor);
		int newdimsc = FMath.round(indims.c*cfactor);
		if (newdimsx == 0) newdimsx = 1;
		if (newdimsy == 0) newdimsy = 1;
		if (newdimsz == 0) newdimsz = 1;
		if (newdimst == 0) newdimst = 1;
		if (newdimsc == 0) newdimsc = 1;
		newdims = new Dimensions(newdimsx,newdimsy,newdimsz,newdimst,newdimsc);
		
		messenger.log("Output image dimensions: (x,y,z,t,c) = ("+newdims.x+","+newdims.y+","+newdims.z+","+newdims.t+","+newdims.c+")");
		
		// Compute and store the shift of the origin with respect to the input image:
		xshift = 0.5*((indims.x - 1) - (newdims.x - 1)/xfactor);
		yshift = 0.5*((indims.y - 1) - (newdims.y - 1)/yfactor);
		zshift = 0.5*((indims.z - 1) - (newdims.z - 1)/zfactor);
		tshift = 0.5*((indims.t - 1) - (newdims.t - 1)/tfactor);
		cshift = 0.5*((indims.c - 1) - (newdims.c - 1)/cfactor);
		
		// Check if requested interpolation scheme is applicable:
		messenger.log("Selecting "+schemes(interpolation));
		if (interpolation < NEAREST || interpolation > BSPLINE5)
			throw new IllegalArgumentException("Non-supported interpolation scheme");
		xscheme = yscheme = zscheme = tscheme = cscheme = interpolation;
		if (xfactor != 1 && indims.x == 1 && xscheme >= 1) {
			messenger.log("Size of input image in x-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in x-dimension");
			xscheme = 0;
		} else if (xfactor != 1 && indims.x < 4 && xscheme >= 2) {
			messenger.log("Size of input image in x-dimension too small");
			messenger.log("Using linear interpolation in x-dimension");
			xscheme = 1;
		}
		if (yfactor != 1 && indims.y == 1 && yscheme >= 1) {
			messenger.log("Size of input image in y-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in y-dimension");
			yscheme = 0;
		} else if (yfactor != 1 && indims.y < 4 && yscheme >= 2) {
			messenger.log("Size of input image in y-dimension too small");
			messenger.log("Using linear interpolation in y-dimension");
			yscheme = 1;
		}
		if (zfactor != 1 && indims.z == 1 && zscheme >= 1) {
			messenger.log("Size of input image in z-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in z-dimension");
			zscheme = 0;
		} else if (zfactor != 1 && indims.z < 4 && zscheme >= 2) {
			messenger.log("Size of input image in z-dimension too small");
			messenger.log("Using linear interpolation in z-dimension");
			zscheme = 1;
		}
		if (tfactor != 1 && indims.t == 1 && tscheme >= 1) {
			messenger.log("Size of input image in t-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in t-dimension");
			tscheme = 0;
		} else if (tfactor != 1 && indims.t < 4 && tscheme >= 2) {
			messenger.log("Size of input image in t-dimension too small");
			messenger.log("Using linear interpolation in t-dimension");
			tscheme = 1;
		}
		if (cfactor != 1 && indims.c == 1 && cscheme >= 1) {
			messenger.log("Size of input image in c-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in c-dimension");
			cscheme = 0;
		} else if (cfactor != 1 && indims.c < 4 && cscheme >= 2) {
			messenger.log("Size of input image in c-dimension too small");
			messenger.log("Using linear interpolation in c-dimension");
			cscheme = 1;
		}
		
		// Set border sizes based on interpolation scheme:
		int bxsize = 0;
		switch (xscheme) {
			case NEAREST: bxsize = 0; break;
			case LINEAR: bxsize = 1; break;
			case CUBIC: bxsize = 2; break;
			case BSPLINE3: bxsize = 2; break;
			case OMOMS3: bxsize = 2; break;
			case BSPLINE5: bxsize = 3; break;
		}
		int bysize = 0;
		switch (yscheme) {
			case NEAREST: bysize = 0; break;
			case LINEAR: bysize = 1; break;
			case CUBIC: bysize = 2; break;
			case BSPLINE3: bysize = 2; break;
			case OMOMS3: bysize = 2; break;
			case BSPLINE5: bysize = 3; break;
		}
		int bzsize = 0;
		switch (zscheme) {
			case NEAREST: bzsize = 0; break;
			case LINEAR: bzsize = 1; break;
			case CUBIC: bzsize = 2; break;
			case BSPLINE3: bzsize = 2; break;
			case OMOMS3: bzsize = 2; break;
			case BSPLINE5: bzsize = 3; break;
		}
		int btsize = 0;
		switch (tscheme) {
			case NEAREST: btsize = 0; break;
			case LINEAR: btsize = 1; break;
			case CUBIC: btsize = 2; break;
			case BSPLINE3: btsize = 2; break;
			case OMOMS3: btsize = 2; break;
			case BSPLINE5: btsize = 3; break;
		}
		int bcsize = 0;
		switch (cscheme) {
			case NEAREST: bcsize = 0; break;
			case LINEAR: bcsize = 1; break;
			case CUBIC: bcsize = 2; break;
			case BSPLINE3: bcsize = 2; break;
			case OMOMS3: bcsize = 2; break;
			case BSPLINE5: bcsize = 3; break;
		}
		borders = new Borders(bxsize,bysize,bzsize,btsize,bcsize);
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
	
	private void logus(final String s) {
		
		messenger.log(s);
		progressor.status(s+"...");
	}
	
	/** The object used for message displaying. */
	public final Messenger messenger = new Messenger();
	
	/** The object used for progress displaying. */
	public final Progressor progressor = new Progressor();
	
	private final Prefilter prefilter = new Prefilter();
	
	private Dimensions newdims;
	private Borders borders;
	
	private double[][] kernel;
	private int[] pos;
	
	private double xfactor, yfactor, zfactor, tfactor, cfactor;
	private double xshift, yshift, zshift, tshift, cshift;
	private int xscheme, yscheme, zscheme, tscheme, cscheme;
	
	private String component = "";
	
}
