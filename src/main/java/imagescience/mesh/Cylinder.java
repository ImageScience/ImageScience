package imagescience.mesh;

import java.util.Vector;

import javax.vecmath.Point3f;

/** Triangular mesh of a cylinder in 3D. */
public class Cylinder {
	
	private Point3f[] v = null; // Vertices
	
	private int[] t = null; // Triangles
	
	private int lod = 0; // Level of detail
	
	private boolean cap = true; // Capping
	
	/** Constructs the mesh of a unit cylinder at the given level of detail.
		
		@param lod The level of detail. At minimum level {@code 0} the cylinder has 16 faces.
		
		@param cap Determines whether the cylinder is capped (closed) or open.
		
		@throws IllegalArgumentException If {@code lod} is less than {@code 0}.
	*/
	public Cylinder(final int lod, final boolean cap) {
		
		if (lod < 0) throw new IllegalArgumentException("Level of detail less than 0");
		
		this.lod = lod; this.cap = cap;
		
		// Create cylinder at given level:
		final int ni = 16*(int)Math.pow(2,lod);
		final int no = cap ? 2 : 0;
		final int nv = 2*ni + no;
		final int nt = 3*(2 + no)*ni;
		v = new Point3f[nv]; int vi = 0;
		t = new int[nt]; int ti = 0;
		if (cap) {
			v[vi++] = new Point3f(0,0,0);
			v[vi++] = new Point3f(1,0,0);
		}
		final double da = 2*Math.PI/ni;
		for (int i=0; i<ni; ++i) {
			final double a = i*da;
			final float ca = (float)Math.cos(a);
			final float sa = (float)Math.sin(a);
			v[vi++] = new Point3f(0,ca,sa);
			v[vi++] = new Point3f(1,ca,sa);
			if (i > 0) {
				t[ti++] = vi-4; t[ti++] = vi-2; t[ti++] = vi-3;
				t[ti++] = vi-3; t[ti++] = vi-2; t[ti++] = vi-1;
				if (cap) {
					t[ti++] = vi-4; t[ti++] = 0; t[ti++] = vi-2;
					t[ti++] = vi-3; t[ti++] = vi-1; t[ti++] = 1;
				}
			}
		}
		t[ti++] = vi-2; t[ti++] = no; t[ti++] = vi-1;
		t[ti++] = vi-1; t[ti++] = no; t[ti++] = no+1;
		if (cap) {
			t[ti++] = vi-2; t[ti++] = 0; t[ti++] = 2;
			t[ti++] = vi-1; t[ti++] = 3; t[ti++] = 1;
		}
	}
	
	/** Returns a copy of the cylinder mesh with given base and end position and correponding radii.
		
		@param p0 The center position of the cylinder base.
		
		@param r0 The radius of the cylinder base.
		
		@param p1 The center position of the cylinder end.
		
		@param r1 The radius of the cylinder end.
		
		@throws IllegalArgumentException If {@code r0} or {@code r1} is less than {@code 0}.
		
		@return A new {@code Vector<Point3f>} object containing the triangles of the cylinder mesh. Each vector element is one vertex of the mesh and each successive three elements constitute one triangle. For memory efficiency, vertices are shared between adjacent triangles. That is, the corresponding {@code Point3f} elements are handles of the same object.
	*/
	public Vector<Point3f> render(final Point3f p0, final float r0, final Point3f p1, final float r1) {
		
		if (r0 < 0) throw new IllegalArgumentException("Base radius less than 0");
		if (r1 < 0) throw new IllegalArgumentException("End radius less than 0");
		
		// Copy and transform vertices:
		final int nv = v.length;
		final Point3f[] vc = new Point3f[nv];
		final double dx = p1.x - p0.x;
		final double dy = p1.y - p0.y;
		final double dz = p1.z - p0.z;
		final double d2 = Math.sqrt(dx*dx+dy*dy);
		final double d3 = Math.sqrt(dx*dx+dy*dy+dz*dz);
		final double a = Math.atan2(dy,dx);
		final double e = Math.atan2(dz,d2);
		final float ca = (float)Math.cos(a);
		final float sa = (float)Math.sin(a);
		final float ce = (float)Math.cos(e);
		final float se = (float)Math.sin(e);
		if (cap) {
			vc[0] = new Point3f(p0);
			vc[1] = new Point3f(p1);
		}
		float vi0x, vi0y, vi0z;
		float vi1x, vi1y, vi1z;
		for (int i=(cap?2:0); i<nv; i+=2) {
			final Point3f vi0 = new Point3f(v[i]);
			final Point3f vi1 = new Point3f(v[i+1]);
			// Stretch to length and radii:
			vi0.x *= d3; vi0.y *= r0; vi0.z *= r0;
			vi1.x *= d3; vi1.y *= r1; vi1.z *= r1;
			// Apply elevation angle:
			vi0x = vi0.x*ce - vi0.z*se;
			vi0z = vi0.z*ce + vi0.x*se;
			vi0.x = vi0x; vi0.z = vi0z;
			vi1x = vi1.x*ce - vi1.z*se;
			vi1z = vi1.z*ce + vi1.x*se;
			vi1.x = vi1x; vi1.z = vi1z;
			// Apply azimuth angle:
			vi0x = vi0.x*ca - vi0.y*sa;
			vi0y = vi0.x*sa + vi0.y*ca;
			vi0.x = vi0x; vi0.y = vi0y;
			vi1x = vi1.x*ca - vi1.y*sa;
			vi1y = vi1.x*sa + vi1.y*ca;
			vi1.x = vi1x; vi1.y = vi1y;
			// Shift to start position:
			vi0.x += p0.x; vi0.y += p0.y; vi0.z += p0.z;
			vi1.x += p0.x; vi1.y += p0.y; vi1.z += p0.z;
			// Add vertices to array:
			vc[i] = vi0; vc[i+1] = vi1;
		}
		
		// Copy triangles:
		final int nt = t.length;
		final Vector<Point3f> mesh = new Vector<Point3f>(nt);
		for (int i=0; i<nt; ++i) mesh.add(vc[t[i]]);
		return mesh;
	}
	
}
