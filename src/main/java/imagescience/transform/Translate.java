package imagescience.transform;

import imagescience.ImageScience;

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

/** Translates an image using different interpolation schemes.
	
	<dt><b>References:</b></dt>
	
	<dd><table border="0" cellspacing="0" cellpadding="0">
	
	<tr><td valign="top">[1]</td><td width="10"></td><td>R. G. Keys, "Cubic Convolution Interpolation for Digital Image Processing", <em>IEEE Transactions on Acoustics, Speech, and Signal Processing</em>, vol. 29, no. 6, December 1981, pp. 1153-1160.</td></tr>
	
	<tr><td valign="top">[2]</td><td width="10"></td><td>M. Unser, "Splines: A Perfect Fit for Signal and Image Processing", <em>IEEE Signal Processing Magazine</em>, vol. 16, no. 6, November 1999, pp. 22-38.</td></tr>
	
	<tr><td valign="top">[3]</td><td width="10"></td><td>P. Thevenaz, T. Blu, M. Unser, "Interpolation Revisited", <em>IEEE Transactions on Medical Imaging</em>, vol. 19, no. 7, July 2000, pp.739-758.</td></tr>
	
	<tr><td valign="top">[4]</td><td width="10"></td><td>E. Meijering, W. Niessen, M. Viergever, "Quantitative Evaluation of Convolution-Based Methods for Medical Image Interpolation", <em>Medical Image Analysis</em>, vol. 5, no. 2, June 2001, pp. 111-126.</td></tr>
	
	<tr><td valign="top">[5]</td><td width="10"></td><td>T. Blu, P. Thevenaz, M. Unser, "MOMS: Maximal-Order Interpolation of Minimal Support", <em>IEEE Transactions on Image Processing</em>, vol. 10, no. 7, July 2001, pp. 1069-1080.</td></tr>
	
	</table></dd>
*/
public class Translate {
	
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
	public Translate() { }
	
	/** Translates an image.
		
		@param image The input image to be translated. For images of type {@link ColorImage}, the color components are processed separately by the method.
		
		@param xtrans {@code ytrans} - {@code ztrans} - The translation distances (in physical units) in the respective dimensions. The translation is applied to every x-y-z subimage in a 5D image.
		
		@param interpolation The interpolation scheme to be used. Must be equal to one of the static fields of this class.
		
		@return A new image containing a translated version of the input image. The returned image is of the same type as the input image.
		
		@throws IllegalArgumentException If the translation distances are out of range or if the requested {@code interpolation} scheme is not supported.
		
		@throws IllegalStateException If the x-, y-, or z-aspect size of {@code image} is less than or equal to {@code 0}.
		
		@throws NullPointerException If {@code image} is {@code null}.
		
		@throws UnknownError If for any reason the output image can not be created. In most cases this will be due to insufficient free memory.
	*/
	public synchronized Image run(
		final Image image,
		final double xtrans,
		final double ytrans,
		final double ztrans,
		final int interpolation
	) {
		
		messenger.log(ImageScience.prelude()+"Translate");
		
		// Initialize:
		final Timer timer = new Timer();
		timer.messenger.log(messenger.log());
		timer.start();
		
		initialize(image,xtrans,ytrans,ztrans,interpolation);
		
		// Translate:
		messenger.log("Translating "+image.type());
		Image translated = null;
		if (image instanceof ColorImage) {
			messenger.log("Processing RGB-color components separately");
			final ColorImage cimage = (ColorImage)image;
			
			progressor.range(0,0.33);
			component = " red component";
			messenger.log("Translating"+component);
			cimage.component(ColorImage.RED);
			Image comp = cimage.get(); comp = translate(comp);
			final ColorImage ctranslated = new ColorImage(comp.dimensions());
			ctranslated.component(ColorImage.RED);
			ctranslated.set(comp);
			
			progressor.range(0.33,0.67);
			component = " green component";
			messenger.log("Translating"+component);
			cimage.component(ColorImage.GREEN);
			comp = cimage.get(); comp = translate(comp);
			ctranslated.component(ColorImage.GREEN);
			ctranslated.set(comp);
			
			progressor.range(0.67,1);
			component = " blue component";
			messenger.log("Translating"+component);
			cimage.component(ColorImage.BLUE);
			comp = cimage.get(); comp = translate(comp);
			ctranslated.component(ColorImage.BLUE);
			ctranslated.set(comp);
			
			translated = ctranslated;
			
		} else {
			component = "";
			progressor.range(0,1);
			translated = translate(image);
		}
		
		// Finish up:
		translated.name(image.name()+" translated");
		translated.aspects(image.aspects().duplicate());
		
		timer.stop();
		
		return translated;
	}
	
	private Image translate(final Image image) {
		
		// No need to translate if all distances are zero:
		if (dx == 0 && dy == 0 && dz == 0) {
			messenger.log("All translation distances are zero");
			messenger.log("Returning a copy of the input image");
			return image.duplicate();
		}
		
		final Dimensions indims = image.dimensions();
		final int vol = indims.c*indims.t*indims.z*indims.y*indims.x;
		Image ximage = null, yimage = null, zimage = null;
		
		progressor.steps(
			(dx != 0 ? vol : 0) +
			(dy != 0 ? vol : 0) +
			(dz != 0 ? vol : 0)
		);
		progressor.status("Translating...");
		progressor.start();
		
		// Translating in x-dimension: *********************************************
		if (dx == 0) {
			messenger.log("Skipping translation in x-dimension");
			ximage = image;
		} else {
			logus("Translating"+component+" in x-dimension");
			final double[] ain = new double[indims.x + 2*borders.x];
			final double[] anew = new double[indims.x];
			for (int i=0; i<anew.length; ++i) anew[i] = background;
			final Coordinates cin = new Coordinates(); cin.x = -borders.x;
			final Coordinates cnew = new Coordinates();
			final int maxbx = indims.x + 2*borders.x - 1;
			final int xoffset = FMath.floor(-dx);
			final double xdiff = -dx - xoffset;
			final double xmdiff = 1 - xdiff;
			final int minx = (dx > 0) ? FMath.floor(dx) : 0;
			final int maxx = (dx > 0) ? (indims.x-1) : (indims.x-1+FMath.ceil(dx));
			final int ix = maxx - minx + 1;
			ximage = Image.create(indims,image.type());
			ximage.axes(Axes.X); image.axes(Axes.X);
			
			switch (xscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in x-dimension");
					final int xoffsetnn = -FMath.round(dx);
					final int minxnn = (dx > 0) ? -xoffsetnn : 0;
					final int maxxnn = (dx > 0) ? (indims.x-1) : (indims.x-1-xoffsetnn);
					final int ixnn = maxxnn - minxnn + 1;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									for (int x=0, xin=minxnn+xoffsetnn, xnew=minxnn; x<ixnn; ++x, ++xin, ++xnew)
										anew[xnew] = ain[xin];
									ximage.set(cnew,anew);
									progressor.step(indims.x);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in x-dimension");
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									// ain[0] = ain[2]; ain[maxbx] = ain[maxbx-2];
									for (int x=0, xin=borders.x+minx+xoffset, xnew=minx; x<ix; ++x, ++xin, ++xnew)
										anew[xnew] = xmdiff*ain[xin] + xdiff*ain[xin+1];
									ximage.set(cnew,anew);
									progressor.step(indims.x);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in x-dimension");
					final double wxm1 = -D1O2*xdiff*xmdiff*xmdiff;
					final double wx   = 1 + (D3O2*xdiff - D5O2)*xdiff*xdiff;
					final double wxp1 = 1 + (D3O2*xmdiff - D5O2)*xmdiff*xmdiff;
					final double wxp2 = -D1O2*xmdiff*xdiff*xdiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									// ain[0] = ain[4]; ain[maxbx] = ain[maxbx-4];
									// ain[1] = ain[3]; ain[maxbx-1] = ain[maxbx-3];
									for (int x=0, xin=borders.x+minx+xoffset, xnew=minx; x<ix; ++x, ++xin, ++xnew)
										anew[xnew] = wx*ain[xin] + wxm1*ain[xin-1] + wxp1*ain[xin+1] + wxp2*ain[xin+2];
									ximage.set(cnew,anew);
									progressor.step(indims.x);
							}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in x-dimension");
					final double wxm1 = D1O6*xmdiff*xmdiff*xmdiff;
					final double wx   = D2O3 + (D1O2*xdiff - 1)*xdiff*xdiff;
					final double wxp1 = D2O3 + (D1O2*xmdiff - 1)*xmdiff*xmdiff;
					final double wxp2 = D1O6*xdiff*xdiff*xdiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									prefilter.bspline3(ain,borders.x);
									// ain[0] = ain[4]; ain[maxbx] = ain[maxbx-4];
									// ain[1] = ain[3]; ain[maxbx-1] = ain[maxbx-3];
									for (int x=0, xin=borders.x+minx+xoffset, xnew=minx; x<ix; ++x, ++xin, ++xnew)
										anew[xnew] = wx*ain[xin] + wxm1*ain[xin-1] + wxp1*ain[xin+1] + wxp2*ain[xin+2];
									ximage.set(cnew,anew);
									progressor.step(indims.x);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in x-dimension");
					final double wxm1 = xmdiff*(D1O42 + D1O6*xmdiff*xmdiff);
					final double wx   = D13O21 + xdiff*(D1O14 + xdiff*(D1O2*xdiff - 1));
					final double wxp1 = D13O21 + xmdiff*(D1O14 + xmdiff*(D1O2*xmdiff - 1));
					final double wxp2 = xdiff*(D1O42 + D1O6*xdiff*xdiff);
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									prefilter.omoms3(ain,borders.x);
									// ain[0] = ain[4]; ain[maxbx] = ain[maxbx-4];
									// ain[1] = ain[3]; ain[maxbx-1] = ain[maxbx-3];
									for (int x=0, xin=borders.x+minx+xoffset, xnew=minx; x<ix; ++x, ++xin, ++xnew)
										anew[xnew] = wx*ain[xin] + wxm1*ain[xin-1] + wxp1*ain[xin+1] + wxp2*ain[xin+2];
									ximage.set(cnew,anew);
									progressor.step(indims.x);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in x-dimension");
					final double wxm2 = D1O120*xmdiff*xmdiff*xmdiff*xmdiff*xmdiff;
					final double wxm1 = D1O120 + D1O24*xmdiff*(1 + xmdiff*(2 + xmdiff*(2 + xmdiff - xmdiff*xmdiff)));
					final double wx   = D11O20 + xdiff*xdiff*((D1O4 - D1O12*xdiff)*xdiff*xdiff - D1O2);
					final double wxp1 = D11O20 + xmdiff*xmdiff*((D1O4 - D1O12*xmdiff)*xmdiff*xmdiff - D1O2);
					final double wxp2 = D1O120 + D1O24*xdiff*(1 + xdiff*(2 + xdiff*(2 + xdiff - xdiff*xdiff)));
					final double wxp3 = D1O120*xdiff*xdiff*xdiff*xdiff*xdiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.y=0, cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y) {
									image.get(cin,ain);
									prefilter.bspline5(ain,borders.x);
									// ain[0] = ain[6]; ain[maxbx] = ain[maxbx-6];
									// ain[1] = ain[5]; ain[maxbx-1] = ain[maxbx-5];
									// ain[2] = ain[4]; ain[maxbx-2] = ain[maxbx-4];
									for (int x=0, xin=borders.x+minx+xoffset, xnew=minx; x<ix; ++x, ++xin, ++xnew)
										anew[xnew] = wx*ain[xin] + wxm1*ain[xin-1] + wxm2*ain[xin-2] + wxp1*ain[xin+1] + wxp2*ain[xin+2] + wxp3*ain[xin+3];
									ximage.set(cnew,anew);
									progressor.step(indims.x);
								}
					break;
				}
			}
		}
		
		// Translating in y-dimension: *********************************************
		if (dy == 0) {
			messenger.log("Skipping translation in y-dimension");
			yimage = ximage;
		} else {
			logus("Translating"+component+" in y-dimension");
			final double[] ain = new double[indims.y + 2*borders.y];
			final double[] anew = new double[indims.y];
			for (int i=0; i<anew.length; ++i) anew[i] = background;
			final Coordinates cin = new Coordinates(); cin.y = -borders.y;
			final Coordinates cnew = new Coordinates();
			final int maxby = indims.y + 2*borders.y - 1;
			final int yoffset = FMath.floor(-dy);
			final double ydiff = -dy - yoffset;
			final double ymdiff = 1 - ydiff;
			final int miny = (dy > 0) ? FMath.floor(dy) : 0;
			final int maxy = (dy > 0) ? (indims.y-1) : (indims.y-1+FMath.ceil(dy));
			final int iy = maxy - miny + 1;
			yimage = Image.create(indims,image.type());
			yimage.axes(Axes.Y); ximage.axes(Axes.Y);
			
			switch (yscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in y-dimension");
					final int yoffsetnn = -FMath.round(dy);
					final int minynn = (dy > 0) ? -yoffsetnn : 0;
					final int maxynn = (dy > 0) ? (indims.y-1) : (indims.y-1-yoffsetnn);
					final int iynn = maxynn - minynn + 1;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									for (int y=0, yin=minynn+yoffsetnn, ynew=minynn; y<iynn; ++y, ++yin, ++ynew)
										anew[ynew] = ain[yin];
									yimage.set(cnew,anew);
									progressor.step(indims.y);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in y-dimension");
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									// ain[0] = ain[2]; ain[maxby] = ain[maxby-2];
									for (int y=0, yin=borders.y+miny+yoffset, ynew=miny; y<iy; ++y, ++yin, ++ynew)
										anew[ynew] = ymdiff*ain[yin] + ydiff*ain[yin+1];
									yimage.set(cnew,anew);
									progressor.step(indims.y);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in y-dimension");
					final double wym1 = -D1O2*ydiff*ymdiff*ymdiff;
					final double wy   = 1 + (D3O2*ydiff - D5O2)*ydiff*ydiff;
					final double wyp1 = 1 + (D3O2*ymdiff - D5O2)*ymdiff*ymdiff;
					final double wyp2 = -D1O2*ymdiff*ydiff*ydiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									// ain[0] = ain[4]; ain[maxby] = ain[maxby-4];
									// ain[1] = ain[3]; ain[maxby-1] = ain[maxby-3];
									for (int y=0, yin=borders.y+miny+yoffset, ynew=miny; y<iy; ++y, ++yin, ++ynew)
										anew[ynew] = wy*ain[yin] + wym1*ain[yin-1] + wyp1*ain[yin+1] + wyp2*ain[yin+2];
									yimage.set(cnew,anew);
									progressor.step(indims.y);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in y-dimension");
					final double wym1 = D1O6*ymdiff*ymdiff*ymdiff;
					final double wy   = D2O3 + (D1O2*ydiff - 1)*ydiff*ydiff;
					final double wyp1 = D2O3 + (D1O2*ymdiff - 1)*ymdiff*ymdiff;
					final double wyp2 = D1O6*ydiff*ydiff*ydiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									prefilter.bspline3(ain,borders.y);
									// ain[0] = ain[4]; ain[maxby] = ain[maxby-4];
									// ain[1] = ain[3]; ain[maxby-1] = ain[maxby-3];
									for (int y=0, yin=borders.y+miny+yoffset, ynew=miny; y<iy; ++y, ++yin, ++ynew)
										anew[ynew] = wy*ain[yin] + wym1*ain[yin-1] + wyp1*ain[yin+1] + wyp2*ain[yin+2];
									yimage.set(cnew,anew);
									progressor.step(indims.y);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in y-dimension");
					final double wym1 = ymdiff*(D1O42 + D1O6*ymdiff*ymdiff);
					final double wy   = D13O21 + ydiff*(D1O14 + ydiff*(D1O2*ydiff - 1));
					final double wyp1 = D13O21 + ymdiff*(D1O14 + ymdiff*(D1O2*ymdiff - 1));
					final double wyp2 = ydiff*(D1O42 + D1O6*ydiff*ydiff);
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									prefilter.omoms3(ain,borders.y);
									// ain[0] = ain[4]; ain[maxby] = ain[maxby-4];
									// ain[1] = ain[3]; ain[maxby-1] = ain[maxby-3];
									for (int y=0, yin=borders.y+miny+yoffset, ynew=miny; y<iy; ++y, ++yin, ++ynew)
										anew[ynew] = wy*ain[yin] + wym1*ain[yin-1] + wyp1*ain[yin+1] + wyp2*ain[yin+2];
									yimage.set(cnew,anew);
									progressor.step(indims.y);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in y-dimension");
					final double wym2 = D1O120*ymdiff*ymdiff*ymdiff*ymdiff*ymdiff;
					final double wym1 = D1O120 + D1O24*ymdiff*(1 + ymdiff*(2 + ymdiff*(2 + ymdiff - ymdiff*ymdiff)));
					final double wy   = D11O20 + ydiff*ydiff*((D1O4 - D1O12*ydiff)*ydiff*ydiff - D1O2);
					final double wyp1 = D11O20 + ymdiff*ymdiff*((D1O4 - D1O12*ymdiff)*ymdiff*ymdiff - D1O2);
					final double wyp2 = D1O120 + D1O24*ydiff*(1 + ydiff*(2 + ydiff*(2 + ydiff - ydiff*ydiff)));
					final double wyp3 = D1O120*ydiff*ydiff*ydiff*ydiff*ydiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.z=cnew.z=0; cin.z<indims.z; ++cin.z, ++cnew.z)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									ximage.get(cin,ain);
									prefilter.bspline5(ain,borders.y);
									// ain[0] = ain[6]; ain[maxby] = ain[maxby-6];
									// ain[1] = ain[5]; ain[maxby-1] = ain[maxby-5];
									// ain[2] = ain[4]; ain[maxby-2] = ain[maxby-4];
									for (int y=0, yin=borders.y+miny+yoffset, ynew=miny; y<iy; ++y, ++yin, ++ynew)
										anew[ynew] = wy*ain[yin] + wym1*ain[yin-1] + wym2*ain[yin-2] + wyp1*ain[yin+1] + wyp2*ain[yin+2] + wyp3*ain[yin+3];
									yimage.set(cnew,anew);
									progressor.step(indims.y);
								}
					break;
				}
			}
		}
		// Disconnect handle so that the GC might release the memory:
		ximage = null;
		
		// Translating in z-dimension: *********************************************
		if (dz == 0) {
			messenger.log("Skipping translation in z-dimension");
			zimage = yimage;
		} else {
			logus("Translating"+component+" in z-dimension");
			final double[] ain = new double[indims.z + 2*borders.z];
			final double[] anew = new double[indims.z];
			for (int i=0; i<anew.length; ++i) anew[i] = background;
			final Coordinates cin = new Coordinates(); cin.z = -borders.z;
			final Coordinates cnew = new Coordinates();
			final int maxbz = indims.z + 2*borders.z - 1;
			final int zoffset = FMath.floor(-dz);
			final double zdiff = -dz - zoffset;
			final double zmdiff = 1 - zdiff;
			final int minz = (dz > 0) ? FMath.floor(dz) : 0;
			final int maxz = (dz > 0) ? (indims.z-1) : (indims.z-1+FMath.ceil(dz));
			final int iz = maxz - minz + 1;
			zimage = Image.create(indims,image.type());
			zimage.axes(Axes.Z); yimage.axes(Axes.Z);
			
			switch (zscheme) {
				case NEAREST: {
					messenger.log("Nearest-neighbor sampling in z-dimension");
					final int zoffsetnn = -FMath.round(dz);
					final int minznn = (dz > 0) ? -zoffsetnn : 0;
					final int maxznn = (dz > 0) ? (indims.z-1) : (indims.z-1-zoffsetnn);
					final int iznn = maxznn - minznn + 1;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									for (int z=0, zin=minznn+zoffsetnn, znew=minznn; z<iznn; ++z, ++zin, ++znew)
										anew[znew] = ain[zin];
									zimage.set(cnew,anew);
									progressor.step(indims.z);
								}
					break;
				}
				case LINEAR: {
					messenger.log("Linear sampling in z-dimension");
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									// ain[0] = ain[2]; ain[maxbz] = ain[maxbz-2];
									for (int z=0, zin=borders.z+minz+zoffset, znew=minz; z<iz; ++z, ++zin, ++znew)
										anew[znew] = zmdiff*ain[zin] + zdiff*ain[zin+1];
									zimage.set(cnew,anew);
									progressor.step(indims.z);
								}
					break;
				}
				case CUBIC: {
					messenger.log("Cubic convolution sampling in z-dimension");
					final double wzm1 = -D1O2*zdiff*zmdiff*zmdiff;
					final double wz   = 1 + (D3O2*zdiff - D5O2)*zdiff*zdiff;
					final double wzp1 = 1 + (D3O2*zmdiff - D5O2)*zmdiff*zmdiff;
					final double wzp2 = -D1O2*zmdiff*zdiff*zdiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									// ain[0] = ain[4]; ain[maxbz] = ain[maxbz-4];
									// ain[1] = ain[3]; ain[maxbz-1] = ain[maxbz-3];
									for (int z=0, zin=borders.z+minz+zoffset, znew=minz; z<iz; ++z, ++zin, ++znew)
										anew[znew] = wz*ain[zin] + wzm1*ain[zin-1] + wzp1*ain[zin+1] + wzp2*ain[zin+2];
									zimage.set(cnew,anew);
									progressor.step(indims.z);
								}
					break;
				}
				case BSPLINE3: {
					messenger.log("Cubic B-spline prefiltering and sampling in z-dimension");
					final double wzm1 = D1O6*zmdiff*zmdiff*zmdiff;
					final double wz   = D2O3 + (D1O2*zdiff - 1)*zdiff*zdiff;
					final double wzp1 = D2O3 + (D1O2*zmdiff - 1)*zmdiff*zmdiff;
					final double wzp2 = D1O6*zdiff*zdiff*zdiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									prefilter.bspline3(ain,borders.z);
									// ain[0] = ain[4]; ain[maxbz] = ain[maxbz-4];
									// ain[1] = ain[3]; ain[maxbz-1] = ain[maxbz-3];
									for (int z=0, zin=borders.z+minz+zoffset, znew=minz; z<iz; ++z, ++zin, ++znew)
										anew[znew] = wz*ain[zin] + wzm1*ain[zin-1] + wzp1*ain[zin+1] + wzp2*ain[zin+2];
									zimage.set(cnew,anew);
									progressor.step(indims.z);
								}
					break;
				}
				case OMOMS3: {
					messenger.log("Cubic O-MOMS prefiltering and sampling in z-dimension");
					final double wzm1 = zmdiff*(D1O42 + D1O6*zmdiff*zmdiff);
					final double wz   = D13O21 + zdiff*(D1O14 + zdiff*(D1O2*zdiff - 1));
					final double wzp1 = D13O21 + zmdiff*(D1O14 + zmdiff*(D1O2*zmdiff - 1));
					final double wzp2 = zdiff*(D1O42 + D1O6*zdiff*zdiff);
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									prefilter.omoms3(ain,borders.z);
									// ain[0] = ain[4]; ain[maxbz] = ain[maxbz-4];
									// ain[1] = ain[3]; ain[maxbz-1] = ain[maxbz-3];
									for (int z=0, zin=borders.z+minz+zoffset, znew=minz; z<iz; ++z, ++zin, ++znew)
										anew[znew] = wz*ain[zin] + wzm1*ain[zin-1] + wzp1*ain[zin+1] + wzp2*ain[zin+2];
									zimage.set(cnew,anew);
									progressor.step(indims.z);
								}
					break;
				}
				case BSPLINE5: {
					messenger.log("Quintic B-spline prefiltering and sampling in z-dimension");
					final double wzm2 = D1O120*zmdiff*zmdiff*zmdiff*zmdiff*zmdiff;
					final double wzm1 = D1O120 + D1O24*zmdiff*(1 + zmdiff*(2 + zmdiff*(2 + zmdiff - zmdiff*zmdiff)));
					final double wz   = D11O20 + zdiff*zdiff*((D1O4 - D1O12*zdiff)*zdiff*zdiff - D1O2);
					final double wzp1 = D11O20 + zmdiff*zmdiff*((D1O4 - D1O12*zmdiff)*zmdiff*zmdiff - D1O2);
					final double wzp2 = D1O120 + D1O24*zdiff*(1 + zdiff*(2 + zdiff*(2 + zdiff - zdiff*zdiff)));
					final double wzp3 = D1O120*zdiff*zdiff*zdiff*zdiff*zdiff;
					for (cin.c=cnew.c=0; cin.c<indims.c; ++cin.c, ++cnew.c)
						for (cin.t=cnew.t=0; cin.t<indims.t; ++cin.t, ++cnew.t)
							for (cin.y=cnew.y=0; cin.y<indims.y; ++cin.y, ++cnew.y)
								for (cin.x=cnew.x=0; cin.x<indims.x; ++cin.x, ++cnew.x) {
									yimage.get(cin,ain);
									prefilter.bspline5(ain,borders.z);
									// ain[0] = ain[6]; ain[maxbz] = ain[maxbz-6];
									// ain[1] = ain[5]; ain[maxbz-1] = ain[maxbz-5];
									// ain[2] = ain[4]; ain[maxbz-2] = ain[maxbz-4];
									for (int z=0, zin=borders.z+minz+zoffset, znew=minz; z<iz; ++z, ++zin, ++znew)
										anew[znew] = wz*ain[zin] + wzm1*ain[zin-1] + wzm2*ain[zin-2] + wzp1*ain[zin+1] + wzp2*ain[zin+2] + wzp3*ain[zin+3];
									zimage.set(cnew,anew);
									progressor.step(indims.z);
								}
					break;
				}
			}
		}
		
		progressor.stop();
		
		return zimage;
	}
	
	private void initialize(
		final Image image,
		final double xtrans,
		final double ytrans,
		final double ztrans,
		final int interpolation
	) {
		
		// Check parameters and conditions:
		final Dimensions indims = image.dimensions();
		messenger.log("Input image dimensions: (x,y,z,t,c) = ("+indims.x+","+indims.y+","+indims.z+","+indims.t+","+indims.c+")");
		
		final double vx = image.aspects().x;
		final double vy = image.aspects().y;
		final double vz = image.aspects().z;
		if (vx <= 0) throw new IllegalStateException("Aspect ratio in x-dimension less than or equal to 0");
		if (vy <= 0) throw new IllegalStateException("Aspect ratio in y-dimension less than or equal to 0");
		if (vz <= 0) throw new IllegalStateException("Aspect ratio in z-dimension less than or equal to 0");
		messenger.log("Input voxel dimensions: (x,y,z) = ("+vx+","+vy+","+vz+")");
		
		dx = xtrans/vx;
		dy = ytrans/vy;
		dz = ztrans/vz;
		
		messenger.log("Translation distances: (x,y,z) = ("+xtrans+","+ytrans+","+ztrans+")");
		messenger.log("Translation in voxel units: (x,y,z) = ("+dx+","+dy+","+dz+")");
		
		final double maxx = indims.x - 1;
		final double maxy = indims.y - 1;
		final double maxz = indims.z - 1;
		
		if (dx > maxx || dx < -maxx) throw new IllegalArgumentException("Translation distance out of range in x-dimension");
		if (dy > maxy || dy < -maxy) throw new IllegalArgumentException("Translation distance out of range in y-dimension");
		if (dz > maxz || dz < -maxz) throw new IllegalArgumentException("Translation distance out of range in z-dimension");
		
		// Check if requested interpolation scheme is applicable:
		messenger.log("Selecting "+schemes(interpolation));
		if (interpolation < NEAREST || interpolation > BSPLINE5)
			throw new IllegalArgumentException("Non-supported interpolation scheme");
		xscheme = yscheme = zscheme = interpolation;
		if (dx != 0 && indims.x == 1 && xscheme >= 1) {
			messenger.log("Size of input image in x-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in x-dimension");
			xscheme = 0;
		} else if (dx != 0 && indims.x < 4 && xscheme >= 2) {
			messenger.log("Size of input image in x-dimension too small");
			messenger.log("Using linear interpolation in x-dimension");
			xscheme = 1;
		}
		if (dy != 0 && indims.y == 1 && yscheme >= 1) {
			messenger.log("Size of input image in y-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in y-dimension");
			yscheme = 0;
		} else if (dy != 0 && indims.y < 4 && yscheme >= 2) {
			messenger.log("Size of input image in y-dimension too small");
			messenger.log("Using linear interpolation in y-dimension");
			yscheme = 1;
		}
		if (dz != 0 && indims.z == 1 && zscheme >= 1) {
			messenger.log("Size of input image in z-dimension too small");
			messenger.log("Using nearest-neighbor interpolation in z-dimension");
			zscheme = 0;
		} else if (dz != 0 && indims.z < 4 && zscheme >= 2) {
			messenger.log("Size of input image in z-dimension too small");
			messenger.log("Using linear interpolation in z-dimension");
			zscheme = 1;
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
		borders = new Borders(bxsize,bysize,bzsize,0,0);
		
		// Show background filling value:
		messenger.log("Background filling with value "+background);
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
	
	/** The value used for background filling. The default value is {@code 0}. */
	public double background = 0;
	
	/** The object used for message displaying. */
	public final Messenger messenger = new Messenger();
	
	/** The object used for progress displaying. */
	public final Progressor progressor = new Progressor();
	
	private final Prefilter prefilter = new Prefilter();
	
	private Borders borders;
	
	private double dx, dy, dz;
	private int xscheme, yscheme, zscheme;
	
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
	
}
