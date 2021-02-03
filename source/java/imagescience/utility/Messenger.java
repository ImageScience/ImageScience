package imagescience.utility;

import ij.IJ;
import ij.ImageJ;
import ij.gui.MessageDialog;

/** Displays various types of messages. */
public class Messenger {
	
	/** Default constructor. */
	public Messenger() { }
	
	private boolean log = false;
	
	/** Indicates whether log messages are displayed.
		
		@return Value {@code true} if log messages are displayed, or {@code false} if this is not the case.
	*/
	public boolean log() { return log; }
	
	/** Enables or disables displaying of log messages. By default they are not displayed.
		
		@param enable If {@code true} (or {@code false}) log messages are (or are not) displayed.
	*/
	public void log(final boolean enable) { log = enable; }
	
	/** Displays the given log message if displaying is enabled for such messages. The given message is then written to ImageJ's log window.
		
		@param message The message.
	*/
	public void log(final String message) { if (log) IJ.log(message); }
	
	private boolean status = false;
	
	/** Indicates whether status messages are displayed.
		
		@return Value {@code true} if status messages are displayed, or {@code false} if this is not the case.
	*/
	public boolean status() { return status; }
	
	/** Enables or disables displaying of status messages. By default they are not displayed.
		
		@param enable If {@code true} (or {@code false}) status messages are (or are not) displayed.
	*/
	public void status(final boolean enable) { status = enable; }
	
	/** Displays the given status message if displaying is enabled for such messages. The given message is then written to ImageJ's status bar.
		
		@param message The message.
	*/
	public void status(final String message) { if (status) IJ.showStatus(message); }
}
