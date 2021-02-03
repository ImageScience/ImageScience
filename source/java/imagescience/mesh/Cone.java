package imagescience.mesh;

import java.util.Vector;

import org.scijava.vecmath.Point3f;

/** Triangular mesh of a cone in 3D. */
public class Cone {
	
	private Point3f[] v = null; // Vertices
	
	private int[] t = null; // Triangles
	
	private int lod = 0; // Level of detail
	
	private boolean cap = true; // Capping
	
	/** Constructs the mesh of a unit cone at the given level of detail.
		
		@param lod The level of detail. At minimum level {@code 0} the cone has 16 faces.
		
		@param cap Determines whether the cone is capped (closed) or open.
		
		@throws IllegalArgumentException If {@code lod} is less than {@code 0}.
	*/
	public Cone(final int lod, final boolean cap) {
		
		if (lod < 0) throw new IllegalArgumentException("Level of detail less than 0");
		
		this.lod = lod; this.cap = cap;
		
		// Create arrow at given level:
		final int ni = 16*(int)Math.pow(2,lod);
		final int no = cap ? 2 : 1;
		final int nv = ni + no;
		final int nt = 3*ni*no;
		v = new Point3f[nv]; int vi = 0;
		t = new int[nt]; int ti = 0;
		v[vi++] = new Point3f(1,0,0);
		if (cap) v[vi++] = new Point3f(0,0,0);
		final double da = 2*Math.PI/ni;
		for (int i=0; i<ni; ++i) {
			final double a = i*da;
			final float ca = (float)Math.cos(a);
			final float sa = (float)Math.sin(a);
			v[vi++] = new Point3f(0,ca,sa);
			if (i > 0) {
				t[ti++] = vi-2; t[ti++] = vi-1; t[ti++] = 0;
				if (cap) { t[ti++] = vi-1; t[ti++] = vi-2; t[ti++] = 1; }
			}
		}
		t[ti++] = vi-1; t[ti++] = no; t[ti++] = 0;
		if (cap) { t[ti++] = vi-1; t[ti++] = 1; t[ti++] = 2; }
	}
	
	/** Returns a copy of the cone mesh with given base and apex position and with given base radius.
		
		@param base The center position of the cone base.
		
		@param radius The radius of the cone base.
		
		@param apex The position of the cone apex.
		
		@throws IllegalArgumentException If {@code radius} is less than {@code 0}.
		
		@return A new {@code Vector<Point3f>} object containing the triangles of the cone mesh. Each vector element is one vertex of the mesh and each successive three elements constitute one triangle. For memory efficiency, vertices are shared between adjacent triangles. That is, the corresponding {@code Point3f} elements are handles of the same object.
	*/
	public Vector<Point3f> render(final Point3f base, final float radius, final Point3f apex) {
		
		if (radius < 0) throw new IllegalArgumentException("Radius less than 0");
		
		// Copy and transform vertices:
		final int nv = v.length;
		final Point3f[] vc = new Point3f[nv];
		final double dx = apex.x - base.x;
		final double dy = apex.y - base.y;
		final double dz = apex.z - base.z;
		final double d2 = Math.sqrt(dx*dx+dy*dy);
		final double d3 = Math.sqrt(dx*dx+dy*dy+dz*dz);
		final double a = Math.atan2(dy,dx);
		final double e = Math.atan2(dz,d2);
		final float ca = (float)Math.cos(a);
		final float sa = (float)Math.sin(a);
		final float ce = (float)Math.cos(e);
		final float se = (float)Math.sin(e);
		vc[0] = new Point3f(apex);
		if (cap) vc[1] = new Point3f(base);
		float vix, viy, viz;
		for (int i=(cap?2:1); i<nv; ++i) {
			final Point3f vi = new Point3f(v[i]);
			// Stretch to length and radius:
			vi.x *= d3; vi.y *= radius; vi.z *= radius;
			// Apply elevation angle:
			vix = vi.x*ce - vi.z*se;
			viz = vi.z*ce + vi.x*se;
			vi.x = vix; vi.z = viz;
			// Apply azimuth angle:
			vix = vi.x*ca - vi.y*sa;
			viy = vi.x*sa + vi.y*ca;
			vi.x = vix; vi.y = viy;
			// Shift to start position:
			vi.x += base.x; vi.y += base.y; vi.z += base.z;
			// Add vertex to array:
			vc[i] = vi;
		}
		
		// Copy triangles:
		final int nt = t.length;
		final Vector<Point3f> mesh = new Vector<Point3f>(nt);
		for (int i=0; i<nt; ++i) mesh.add(vc[t[i]]);
		return mesh;
	}
	
}
