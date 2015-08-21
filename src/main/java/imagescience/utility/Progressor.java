package imagescience.utility;

import ij.IJ;
import ij.ImageJ;
import ij.gui.ProgressBar;

import java.awt.Graphics;
import java.awt.Panel;

import javax.swing.JLabel;

/** Wrapper around ImageJ's progress bar. This class offers several advantages over ImageJ's native progress displaying methods. First, it allows to make sure the progress bar is <em>always</em> updated (repainting can be enforced) if displaying is enabled, even when it is used from within the event dispatching thread (in which case the bar - drawn within ImageJ using that same thread - would otherwise not be updated until the process using the bar is finished). Furthermore, it relieves the user of explicitly computing the progress percentage (all this class requires is the total number of steps before the start of a process and the same number of step calls during the process). Also, it allows to specify the number of progress updates (by default {@code 20} from start to end), thereby limiting the number of repaint calls (which are relatively expensive), and thus reducing execution time for progress displaying. Finally, it allows specifying the progress range (the minimum / maximum displayed progress value for the start / end of the corresponding process), which facilitates progress displaying for subprocesses. */
public class Progressor {
	
	private long step = 0;
	private long steps = 0;
	
	private long update = 0;
	private long updates = 20;
	
	private double min = 0, max = 1;
	private double percent = 0;
	
	private boolean display = false;
	private boolean enforce = false;
	
	private String status = null;
	
	private Progressor parent = null;
	
	/** Default constructor. */
	public Progressor() { }
	
	/** Specifies the number of progress updates displayed by the progress bar. The default number of updates is {@code 20}.
		
		@param n The number of progress updates displayed by the progress bar.
		
		@throws IllegalArgumentException If {@code n} is less than {@code 0}.
	*/
	public void updates(final long n) {
		
		if (n < 0) throw new IllegalArgumentException("Number of updates less than 0");
		
		updates = n;
	}
	
	/** Returns the number of progress updates displayed by the progress bar.
		
		@return The number of progress updates displayed by the progress bar.
	*/
	public long updates() { return updates; }
	
	/** Specifies the number of steps in the process.
		
		@param n The number of steps in the process.
		
		@throws IllegalArgumentException If {@code n} is less than {@code 0}.
	*/
	public void steps(final long n) {
		
		if (n < 0) throw new IllegalArgumentException("Number of steps less than 0");
		
		steps = n;
	}
	
	/** Returns the number of steps in the process.
		
		@return The number of steps in the process.
	*/
	public long steps() { return steps; }
	
	/** Increases the internal step counter. */
	public void step() {
		
		set(step + 1);
	}
	
	/** Increases the internal step counter by the given amount.
		
		@param n The number of steps to be added to the internal step counter.
		
		@throws IllegalArgumentException If {@code n} is less than {@code 0}.
	*/
	public void step(final long n) {
		
		if (n < 0) throw new IllegalArgumentException("Number of steps less than 0");
		
		set(step + n);
	}
	
	/** Sets the internal step counter to the given value.
		
		@param n The value to set the internal step counter to.
		
		@throws IllegalArgumentException If {@code n} is less than {@code 0}.
	*/
	public void set(final long n) {
		
		if (n < 0) throw new IllegalArgumentException("Set value less than 0");
		
		step = n;
		
		if (steps > 0) {
			percent = step/(double)steps;
			final long pup = (long)(percent*updates);
			if (pup > update) {
				update = pup;
				progress();
			}
		}
	}
	
	/** Specifies the minimum displayed progress value. The default minimum is {@code 0}.
		
		@param min The minimum displayed progress value.
		
		@throws IllegalArgumentException If {@code min} is less than {@code 0} or larger than the maximum displayed progress value.
	*/
	public void min(final double min) {
		
		if (min < 0) throw new IllegalArgumentException("Minimum progress value less than 0");
		else if (min > max) throw new IllegalArgumentException("Minimum larger than maximum progress value");
		this.min = min;
	}
	
	/** Returns the minimum displayed progress value. If a parent object has been set using {@link #parent(Progressor)}, the returned value is the minimum of the present object, mapped to the progress range of the parent. This recurses through all layers of parenthood.
		
		@return The minimum displayed progress value.
	*/
	public double min() {
		
		if (parent != null) {
			final double pmin = parent.min();
			final double pmax = parent.max();
			return pmin + min*(pmax - pmin);
		} else return min;
	}
	
	/** Specifies the maximum displayed progress value. The default maximum is {@code 1}.
		
		@param max The maximum displayed progress value.
		
		@throws IllegalArgumentException If {@code max} is larger than {@code 1} or less than the minimum displayed progress value.
	*/
	public void max(final double max) {
		
		if (max > 1) throw new IllegalArgumentException("Maximum progress value larger than 1");
		else if (max < min) throw new IllegalArgumentException("Maximum less than minimum progress value");
		this.max = max;
	}
	
	/** Returns the maximum displayed progress value. If a parent object has been set using {@link #parent(Progressor)}, the returned value is the maximum of the present object, mapped to the progress range of the parent. This recurses through all layers of parenthood.
		
		@return The maximum displayed progress value.
	*/
	public double max() {
		
		if (parent != null) {
			final double pmin = parent.min();
			final double pmax = parent.max();
			return pmin + max*(pmax - pmin);
		} else return max;
	}
	
	/** Specifies the minimum and maximum displayed progress value. The default minimum is {@code 0} and the default maximum is {@code 1}.
		
		@param min The minimum displayed progress value.
		
		@param max The maximum displayed progress value.
		
		@throws IllegalArgumentException If {@code min} is less than {@code 0} or larger than {@code max}, or if {@code max} is larger than {@code 1}.
		
		@see #min()
		@see #min(double)
		@see #max()
		@see #max(double)
	*/
	public void range(final double min, final double max) {
		
		if (min < 0) throw new IllegalArgumentException("Minimum progress value less than 0");
		else if (max > 1) throw new IllegalArgumentException("Maximum progress value larger than 1");
		else if (min > max) throw new IllegalArgumentException("Minimum larger than maximum progress value");
		this.min = min;
		this.max = max;
	}
	
	/** Starts the progress displaying. Should be called at the start of the process whose progress is to be displayed. */
	public void start() {
		
		step = 0;
		update = 0;
		percent = 0;
		progress();
	}
	
	/** Stops the progress displaying. Should be called at the end of the process whose progress was displayed. */
	public void stop() {
		
		percent = 1;
		progress();
	}
	
	private void progress() {
		
		if (display()) {
			final ImageJ ij = IJ.getInstance();
			if (ij != null) { // To avoid exceptions in batch mode
				String s = status();
				final double min = min();
				final double max = max();
				final double p = min + percent*(max - min);
				if (s == null || p >= 1) s = "";
				IJ.showStatus(s);
				IJ.showProgress(p);
				if (enforce()) {
					final Panel sb = ij.getStatusBar();
					final JLabel sl = (JLabel)sb.getComponent(0);
					sl.setOpaque(true); // Transparent by default
					sl.paintImmediately(sl.getBounds());
					final ProgressBar pb = (ProgressBar)sb.getComponent(1);
					final Graphics pg = pb.getGraphics();
					if (pg != null) pb.paint(pg);
				}
			}
		}
	}
	
	/** Indicates whether progress is displayed. If a parent object has been set using {@link #parent(Progressor)}, that object instead determines whether progress is displayed. This recurses through all layers of parenthood, so that effectively the returned value is that of the highest parent in the hierarchy.
		
		@return Value {@code true} if progress is displayed, or {@code false} if this is not the case.
	*/
	public boolean display() {
		
		if (parent != null) return parent.display();
		
		return display;
	}
	
	/** Determines whether progress is displayed. By default progress is not displayed.
		
		@param enable If {@code true} (or {@code false}) progress is (or is not) displayed.
	*/
	public void display(final boolean enable) {
		
		display = enable;
	}
	
	/** Indicates whether repainting of the progress bar is enforced. If a parent object has been set using {@link #parent(Progressor)}, that object instead determines whether repainting is enforced. This recurses through all layers of parenthood, so that effectively the returned value is that of the highest parent in the hierarchy.
		
		@return Value {@code true} if repainting of the progress bar is displayed, or {@code false} if this is not the case.
	*/
	public boolean enforce() {
		
		if (parent != null) return parent.enforce();
		
		return enforce;
	}
	
	/** Determines whether repainting of the progress bar is enforced. By default it is not enforced. Under normal circumstances the progress bar gets repainted in the event dispatching thread. This precludes progress displaying for processes running in that same thread. By enabling enforced repainting, the progress bar gets repainted explicitly, outside of that thread.
		
		@param enable If {@code true} (or {@code false}) repainting of the progress bar is (or is not) enforced.
	*/
	public void enforce(final boolean enable) {
		
		enforce = enable;
	}
	
	/** Sets the parent object for progress displaying. By default no parent object is set. If a parent object is specified, that object determines whether progress is displayed and whether this is enforced or not, overruling the values set using {@link #display(boolean)} and {@link #enforce(boolean)}, and the minimum and maximum displayed progress values set using {@link #min(double)} and {@link #max(double)} or {@link #range(double,double)} are mapped to the progress range of the parent.
		
		@param parent The parent object for progress displaying. Parameter value {@code null} means no parent object is set.
	*/
	public void parent(final Progressor parent) {
		
		this.parent = parent;
	}
	
	/** Returns the parent object for progress displaying.
		
		@return The parent object for progress displaying. The returned value is either {@code null} (indicating no parent has been set) or a valid {@code Progressor} object.
	*/
	public Progressor parent() {
		
		return parent;
	}
	
	/** Sets the status message. The message is displayed in the status area on the left of the progress bar and is rewritten each time the progress bar is updated. By default no message is displayed.
		
		@param status The status message. Parameter value {@code null} means no status message is displayed.
	*/
	public void status(final String status) {
		
		this.status = status;
	}
	
	/** Returns the status message. If a parent object has been set using {@link #parent(Progressor)}, and its status message is not {@code null}, the returned value is the status message of the parent. This recurses through all layers of parenthood, so that effectively the returned value is that of the highest parent in the hierarchy whose status message is not {@code null}, and if no such parent exists the returned value is {@code null}.
		
		@return The status message. The returned value is either {@code null} (indicating no status message has been set) or a valid {@code String} object.
	*/
	public String status() {
		
		if (parent != null) {
			final String ps = parent.status();
			if (ps != null) return ps;
		}
		
		return status;
	}
	
}
