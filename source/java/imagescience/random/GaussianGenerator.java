package imagescience.random;

import java.lang.Math;

/** Gaussian random number generator. This implementation is based on the Box-Muller transformation applied to uniform random numbers, the latter of which are obtained from class {@link UniformGenerator}. For more details, see for example W. H. Press, S. A. Teukolsky, W. T. Vetterling, B. P. Flannery, <a href="http://numerical.recipes/" target="_blank">Numerical Recipes in C: The Art of Scientific Computing</a> (2nd edition), Cambridge University Press, Cambridge, 1992, Section 7.2. */
public class GaussianGenerator implements RandomGenerator {
	
	private final double mean, stdev;
	
	private final UniformGenerator unigen;
	
	/** Constructs a generator of random numbers from the Gaussian distribution with zero mean and unit standard deviation and initialized with a random seed. */
	public GaussianGenerator() { this(0.0,1.0); }
	
	/** Constructs a generator of random numbers from the Gaussian distribution with zero mean and unit standard deviation and initialized with the given {@code seed}.
		
		@param seed The seed used for initialization of the generator.
	*/
	public GaussianGenerator(final int seed) { this(0.0,1.0,seed); }
	
	/** Constructs a generator of random numbers from the Gaussian distribution with given {@code mean} and standard deviation {@code stdev} and initialized with a random seed.
		
		@param mean The mean of the distribution.
		
		@param stdev The standard deviation from the mean of the distribution. Must be larger than or equal to {@code 0}.
		
		@throws IllegalArgumentException If {@code stdev} is less than {@code 0}.
	*/
	public GaussianGenerator(final double mean, final double stdev) {
		
		if (stdev < 0.0) throw new IllegalArgumentException("Standard deviation less than 0");
		
		this.mean = mean; this.stdev = stdev;
		
		unigen = new UniformGenerator(-1,1);
	}
	
	/** Constructs a generator of random numbers from the Gaussian distribution with given {@code mean} and standard deviation {@code stdev} and initialized with the given {@code seed}.
		
		@param mean The mean of the distribution.
		
		@param stdev The standard deviation from the mean of the distribution. Must be larger than or equal to {@code 0}.
		
		@param seed The seed used for initialization of the generator.
		
		@throws IllegalArgumentException If {@code stdev} is less than {@code 0}.
	*/
	public GaussianGenerator(final double mean, final double stdev, final int seed) {
		
		if (stdev < 0.0) throw new IllegalArgumentException("Standard deviation less than 0");
		
		this.mean = mean; this.stdev = stdev;
		
		unigen = new UniformGenerator(-1,1,seed);
	}
	
	/** Returns a random number from the Gaussian distribution with mean and standard deviation specified at construction.
		
		@return A random number from the Gaussian distribution with mean and standard deviation specified at construction.
	*/
	public double next() { return next(mean,stdev); }
	
	/** Returns a random number from the Gaussian distribution with given {@code mean} and standard deviation {@code stdev}.
		
		@param mean The mean of the distribution.
		
		@param stdev The standard deviation from the mean of the distribution. Must be larger than or equal to {@code 0}.
		
		@return A random number from the Gaussian distribution with given {@code mean} and standard deviation {@code stdev}.
		
		@throws IllegalArgumentException If {@code stdev} is less than {@code 0}.
	*/
	public double next(final double mean, final double stdev) {
		
		if (stdev < 0.0) {
			throw new IllegalArgumentException("Standard deviation less than 0");
		}
		
		if (cached) {
			cached = false;
			return mean + stdev*cache;
		}
		
		double v1, v2, R2;
		
		do {
			v1 = unigen.next();
			v2 = unigen.next();
			R2 = v1*v1 + v2*v2;
		} while (R2 >= 1.0);
		
		final double fac = Math.sqrt(-2.0*Math.log(R2)/R2);
		
		cache = v1*fac; cached = true;
		
		return mean + stdev*v2*fac;
	}
	
	private double cache;
	
	private boolean cached = false;
	
}
