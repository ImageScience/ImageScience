package imagescience.utility;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;

/** Provides custom-made mouse cursors. */
public class MouseCursor {
	
	/** The custom arrow cursor type. */
	public static final int ARROW=0;
	
	/** The custom plus cursor type. */
	public static final int PLUS=1;
	
	/** The custom magnifier cursor type. */
	public static final int MAGNIFIER=2;
	
	/** The custom hand cursor type. */
	public static final int HAND=3;
	
	/** The custom cross cursor type. */
	public static final int CROSS=4;
	
	/** The custom sight cursor type. */
	public static final int SIGHT=5;
	
	/** The custom finger cursor type. */
	public static final int FINGER=6;
	
	private static final int SIZE = 32; // Cursor size
	private static final byte o = (byte)0; // Zero value
	private static final byte l = (byte)1; // Low value
	private static final byte m = (byte)180; // Medium value
	private static final byte M = (byte)255; // Maximum value
	
	/** Default constructor. */
	public MouseCursor() { }
	
	/** Creates a new cursor of the requested custom type.
		
		@param type The cursor type. Must be one of the static fields of this class.
		
		@return A new cursor of the requested custom type. If, for any reason, the requested custom cursor can not be created, this method returns a standard replacement cursor provided by the system.
		
		@throws IllegalArgumentException If {@code type} is not one of the indicated values.
	*/
	public Cursor create(final int type) {
		
		Cursor cursor = null;
		
		try {
			byte[] pixels = null;
			Point hotspot = new Point();
			String name = "Cursor";
			switch(type) {
				case ARROW:
					messenger.log("Creating custom arrow cursor");
					pixels = new byte[] {
						l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,l,l,l,l,l,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 0;
					hotspot.y = 0;
					name = "Arrow";
					break;
				case CROSS:
					messenger.log("Creating custom cross cursor");
					pixels = new byte[] {
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 15;
					hotspot.y = 15;
					name = "Cross";
					break;
				case HAND:
					messenger.log("Creating custom hand cursor");
					pixels = new byte[] {
						o,o,o,o,o,o,o,o,o,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,l,l,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,l,o,o,l,M,M,M,M,M,M,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,l,l,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,M,M,M,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,l,M,M,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,l,l,l,l,l,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 11;
					hotspot.y = 15;
					name = "Hand";
					break;
				case FINGER:
					messenger.log("Creating custom finger cursor");
					pixels = new byte[] {
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,l,M,M,l,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,l,o,o,l,M,M,M,M,M,M,M,M,l,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,l,l,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,M,M,M,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,l,M,M,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,l,l,l,l,l,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 6;
					hotspot.y = 3;
					name = "Finger";
					break;
				case MAGNIFIER:
					messenger.log("Creating custom magnifier cursor");
					pixels = new byte[] {
						o,o,o,o,l,l,l,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,l,l,l,M,M,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,l,M,M,M,M,M,M,M,M,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,l,M,M,M,M,M,M,M,M,M,M,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						l,l,M,M,M,M,M,M,M,M,M,M,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,l,l,M,M,M,M,M,M,M,M,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,l,l,l,M,M,M,M,l,l,l,m,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,l,l,l,l,l,l,o,l,l,m,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,l,l,m,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,m,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,m,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 7;
					hotspot.y = 6;
					name = "Magnifier";
					break;
				case PLUS:
					messenger.log("Creating custom plus cursor");
					pixels = new byte[] {
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,l,l,l,l,l,l,l,l,l,l,l,M,l,l,l,l,l,l,l,l,l,l,l,o,o,o,o,o,
						o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,
						o,o,o,o,l,l,l,l,l,l,l,l,l,l,l,M,l,l,l,l,l,l,l,l,l,l,l,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 15;
					hotspot.y = 15;
					name = "Plus";
					break;
				case SIGHT:
					messenger.log("Creating custom sight cursor");
					pixels = new byte[] {
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,l,l,l,l,M,l,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,l,l,M,M,M,M,M,M,M,M,M,l,l,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,l,M,M,l,l,l,l,M,l,l,l,l,M,M,l,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,l,M,l,l,o,o,o,l,M,l,o,o,o,l,l,M,l,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,
						o,o,o,o,o,l,M,l,o,o,o,o,o,l,l,M,l,l,o,o,o,o,o,l,M,l,o,o,o,o,o,o,
						o,o,o,o,o,l,M,l,o,o,o,l,l,M,M,M,M,M,l,l,o,o,o,l,M,l,o,o,o,o,o,o,
						o,o,o,o,l,M,l,o,o,o,l,M,M,l,l,l,l,l,M,M,l,o,o,o,l,M,l,o,o,o,o,o,
						o,o,o,o,l,M,l,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,l,M,l,o,o,o,o,o,
						o,o,o,o,l,M,l,o,o,l,M,l,o,o,o,o,o,o,o,l,M,l,o,o,l,M,l,o,o,o,o,o,
						l,l,l,l,l,M,l,l,l,l,M,l,o,o,o,o,o,o,o,l,M,l,l,l,l,M,l,l,l,l,l,o,
						l,M,M,M,M,M,M,M,M,M,M,l,o,o,o,o,o,o,o,l,M,M,M,M,M,M,M,M,M,M,l,o,
						l,l,l,l,l,M,l,l,l,l,M,l,o,o,o,o,o,o,o,l,M,l,l,l,l,M,l,l,l,l,l,o,
						o,o,o,o,l,M,l,o,o,l,M,l,o,o,o,o,o,o,o,l,M,l,o,o,l,M,l,o,o,o,o,o,
						o,o,o,o,l,M,l,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,l,M,l,o,o,o,o,o,
						o,o,o,o,l,M,l,o,o,o,l,M,M,l,l,l,l,l,M,M,l,o,o,o,l,M,l,o,o,o,o,o,
						o,o,o,o,o,l,M,l,o,o,o,l,l,M,M,M,M,M,l,l,o,o,o,l,M,l,o,o,o,o,o,o,
						o,o,o,o,o,l,M,l,o,o,o,o,o,l,l,M,l,l,o,o,o,o,o,l,M,l,o,o,o,o,o,o,
						o,o,o,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,l,M,l,l,o,o,o,l,M,l,o,o,o,l,l,M,l,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,l,M,M,l,l,l,l,M,l,l,l,l,M,M,l,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,l,l,M,M,M,M,M,M,M,M,M,l,l,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,l,l,l,l,M,l,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,M,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,l,l,l,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,
						o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o,o
					};
					hotspot.x = 15;
					hotspot.y = 15;
					name = "Sight";
					break;
				default:
					throw new IllegalArgumentException();
			}
			final Toolkit dtk = Toolkit.getDefaultToolkit();
			final Dimension dim = dtk.getBestCursorSize(SIZE,SIZE);
			if (dim.width != SIZE || dim.height != SIZE) throw new IllegalStateException();
			final byte[] lut = new byte[256];
			for(int i=0; i<256; ++i) lut[i] = (byte)i;
			final MemoryImageSource source = new MemoryImageSource(SIZE,SIZE,new IndexColorModel(8,256,lut,lut,lut,0),pixels,0,SIZE);
			source.setAnimated(false);
			cursor = dtk.createCustomCursor(dtk.createImage(source),hotspot,name);
			
		} catch (Throwable e) {
			messenger.log("Could not create requested cursor");
			messenger.log("Creating suitable system cursor");
			switch(type) {
				case ARROW:
				case FINGER:
					cursor = new Cursor(Cursor.DEFAULT_CURSOR);
					break;
				case HAND:
					cursor = new Cursor(Cursor.HAND_CURSOR);
					break;
				case MAGNIFIER:
					cursor = new Cursor(Cursor.MOVE_CURSOR);
					break;
				case CROSS:
				case PLUS:
				case SIGHT:
					cursor = new Cursor(Cursor.CROSSHAIR_CURSOR);
					break;
				default:
					throw new IllegalArgumentException("Non-supported cursor type");
			}
		}
		
		return cursor;
	}
	
	/** The object used for message displaying. */
	public final Messenger messenger = new Messenger();
	
}
