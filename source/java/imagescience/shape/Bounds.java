package imagescience.shape;

/** Contains bounding box information of up to 5D shapes. By design, this class does not prevent making the upper bound less than the lower bound in any dimension. */
public class Bounds {

	/** Contains the lower bound for each dimension. The default lower bound for each dimension is {@code 0}. */
	public final Point lower = new Point();

	/** Contains the upper bound for each dimension. The default upper bound for each dimension is {@code 0}. */
	public final Point upper = new Point();

	/** Default constructor. */
	public Bounds() { }

	/** Constructor.
		
		@param lower A point whose coordinates define the lower bound for each dimension.
		
		@param upper A point whose coordinates define the upper bound for each dimension.
		
		@throws NullPointerException If {@code lower} or {@code upper} is {@code null}.
	*/
	public Bounds(final Point lower, final Point upper) {

		this.lower.set(lower);
		this.upper.set(upper);
	}

	/** Duplicates the bounds.
		
		@return A new {@code Bounds} object that is an exact copy of this object. All information is copied and no memory is shared between this and the returned object.
	*/
	public Bounds duplicate() { return new Bounds(lower,upper); }

	/** Indicates whether this bounding box is the same as the given bounding box.
		
		@param bounds The bounding box to compare with.
		
		@return Value {@code true} if {@code bounds} is not {@code null} and its lower and upper bounds in each dimension are equal to the corresponding lower and upper bounds of this object, or {@code false} if this is not the case.
	*/
	public boolean equals(final Bounds bounds) {

		if (bounds == null) return false;

		if (this.lower.equals(bounds.lower) && this.upper.equals(bounds.upper)) return true;

		return false;
	}

}
