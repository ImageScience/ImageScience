package imagescience;

/** Contains the name and version number of the ImageScience library. */
public class ImageScience {
	
	/** Default constructor. */
	public ImageScience() { }
	
	/** Returns the name of the library.
		
		@return A new {@code String} object with the name of the library.
	*/
	public static String name() {
		
		return "ImageScience";
	}
	
	/** Returns the version number of the library.
		
		@return A new {@code String} object with the version number of the library.
	*/
	public static String version() {
		
		final String version = ImageScience.class.getPackage().getImplementationVersion();
		
		return (version == null) ? "DEV" : version;
	}
	
	/** Returns the name and version number of the library appended with a colon and space.
		
		@return A new {@code String} object with the name and version number of the library appended with a colon and space.
	*/
	public static String prelude() {
		
		return name()+" "+version()+": ";
	}
	
}
