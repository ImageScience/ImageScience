package imagescience.image;

/** Contains the dimensions of up to 5D image data objects. */
public class Dimensions {
	
	/** The x-dimension. The default value is {@code 1}. */
	public final int x;
	
	/** The y-dimension. The default value is {@code 1}. */
	public final int y;
	
	/** The z-dimension. The default value is {@code 1}. */
	public final int z;
	
	/** The t-dimension. The default value is {@code 1}. */
	public final int t;
	
	/** The c-dimension. The default value is {@code 1}. */
	public final int c;
	
	/** Default constructor. */
	public Dimensions() {
		
		this.x = 1;
		this.y = 1;
		this.z = 1;
		this.t = 1;
		this.c = 1;
	}
	
	/** One-dimensional constructor.
		
		@param x The x-dimension.
		
		@throws IllegalArgumentException If the parameter value is less than or equal to {@code 0}.
	*/
	public Dimensions(final int x) {
		
		if (x <= 0) throw new IllegalArgumentException("Dimension less than or equal to 0");
		this.x = x;
		this.y = 1;
		this.z = 1;
		this.t = 1;
		this.c = 1;
	}
	
	/** Two-dimensional constructor.
		
		@param x The x-dimension.
		
		@param y The y-dimension.
		
		@throws IllegalArgumentException If any of the parameter values is less than or equal to {@code 0}.
	*/
	public Dimensions(final int x, final int y) {
		
		if (x <= 0 || y <= 0) throw new IllegalArgumentException("Dimension less than or equal to 0");
		this.x = x;
		this.y = y;
		this.z = 1;
		this.t = 1;
		this.c = 1;
	}
	
	/** Three-dimensional constructor.
		
		@param x The x-dimension.
		
		@param y The y-dimension.
		
		@param z The z-dimension.
		
		@throws IllegalArgumentException If any of the parameter values is less than or equal to {@code 0}.
	*/
	public Dimensions(final int x, final int y, final int z) {
		
		if (x <= 0 || y <= 0 || z <= 0) throw new IllegalArgumentException("Dimension less than or equal to 0");
		this.x = x;
		this.y = y;
		this.z = z;
		this.t = 1;
		this.c = 1;
	}
	
	/** Four-dimensional constructor.
		
		@param x The x-dimension.
		
		@param y The y-dimension.
		
		@param z The z-dimension.
		
		@param t The t-dimension.
		
		@throws IllegalArgumentException If any of the parameter values is less than or equal to {@code 0}.
	*/
	public Dimensions(final int x, final int y, final int z, final int t) {
		
		if (x <= 0 || y <= 0 || z <= 0 || t <= 0) throw new IllegalArgumentException("Dimension less than or equal to 0");
		this.x = x;
		this.y = y;
		this.z = z;
		this.t = t;
		this.c = 1;
	}
	
	/** Five-dimensional constructor.
		
		@param x The x-dimension.
		
		@param y The y-dimension.
		
		@param z The z-dimension.
		
		@param t The t-dimension.
		
		@param c The c-dimension.
		
		@throws IllegalArgumentException If any of the parameter values is less than or equal to {@code 0}.
	*/
	public Dimensions(final int x, final int y, final int z, final int t, final int c) {
		
		if (x <= 0 || y <= 0 || z <= 0 || t <= 0 || c <= 0) throw new IllegalArgumentException("Dimension less than or equal to 0");
		this.x = x;
		this.y = y;
		this.z = z;
		this.t = t;
		this.c = c;
	}
	
	/** Duplicates this object.
		
		@return A new {@code Dimensions} object that is an exact copy of this object. All information is copied and no memory is shared between this and the returned object.
	*/
	public Dimensions duplicate() { return new Dimensions(x,y,z,t,c); }
	
	/** Indicates whether this object is in the same state as the given object.
		
		@param dims The object to compare this object with.
	
		@return Value {@code true} if the given object is not {@code null} and its dimensions are equal to the corresponding dimensions of this object, or {@code false} if this is not the case.
	*/
	public boolean equals(final Dimensions dims) {
		
		if (dims == null) return false;
		if (x==dims.x && y==dims.y && z==dims.z && t==dims.t && c==dims.c) return true;
		return false;
	}
	
}
