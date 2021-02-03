package imagescience.utility;

import java.util.StringTokenizer;

/** Compares version strings. The strings are assumed to be of the form P.S.T.Q, where P, S, T, and Q are integers representing the primary, secondary, tertiary, and quaternary version numbers, respectively, separated by a period. Rather than comparing the strings lexicographically, this class compares the values of P, S, T, and Q as integers, in that order. This allows any of them to be outside the range {@code 0...9} and still compare properly. If any of them are missing, or do not represent an integer value, they default to {@code 0}. Version strings are allowed to have an extra period followed by additional version information, for example P.S.T.Q.beta, but that information is ignored.*/
public class VersionChecker {
	
	/** Default constructor. */
	public VersionChecker() { }
	
	/** Indicates how the given version strings compare to each other.
		
		@param v1 The first version string.
		
		@param v2 The second version string.
		
		@return An integer value indicating how {@code v1} compares to {@code v2}:<br>
		Value {@code -1} means {@code v1} is smaller (older) than {@code v2}.<br>
		Value {@code 0} means {@code v1} is equal to {@code v2}.<br>
		Value {@code 1} means {@code v1} is larger (newer) than {@code v2}.
		
		@throws NullPointerException If {@code v1} or {@code v2} is {@code null}.
	*/
	public static int compare(final String v1, final String v2) {
		
		final StringTokenizer st1 = new StringTokenizer(v1,".");
		int v1p; try { v1p = Integer.parseInt(st1.nextToken()); } catch(Throwable e) { v1p = 0; }
		int v1s; try { v1s = Integer.parseInt(st1.nextToken()); } catch(Throwable e) { v1s = 0; }
		int v1t; try { v1t = Integer.parseInt(st1.nextToken()); } catch(Throwable e) { v1t = 0; }
		int v1q; try { v1q = Integer.parseInt(st1.nextToken()); } catch(Throwable e) { v1q = 0; }
		
		final StringTokenizer st2 = new StringTokenizer(v2,".");
		int v2p; try { v2p = Integer.parseInt(st2.nextToken()); } catch(Throwable e) { v2p = 0; }
		int v2s; try { v2s = Integer.parseInt(st2.nextToken()); } catch(Throwable e) { v2s = 0; }
		int v2t; try { v2t = Integer.parseInt(st2.nextToken()); } catch(Throwable e) { v2t = 0; }
		int v2q; try { v2q = Integer.parseInt(st2.nextToken()); } catch(Throwable e) { v2q = 0; }
		
		if (v1p > v2p) return 1;
		else if (v1p < v2p) return -1;
		else if (v1s > v2s) return 1;
		else if (v1s < v2s) return -1;
		else if (v1t > v2t) return 1;
		else if (v1t < v2t) return -1;
		else if (v1q > v2q) return 1;
		else if (v1q < v2q) return -1;
		return 0;
	}
	
}
