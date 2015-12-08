package imagescience.mesh;

import java.util.Vector;

import org.scijava.vecmath.Point3f;

/** Triangular mesh of a cube in 3D. */
public class Cube {
	
	private Point3f[] v = null; // Vertices
	
	private int[] t = null; // Triangles
	
	/** Constructs the mesh of a unit cube. */
	public Cube() {
		
		// Create cube:
		final float h = 0.5f;
		
		v = new Point3f[] {
			new Point3f(-h,-h,-h),
			new Point3f(h,-h,-h),
			new Point3f(h,h,-h),
			new Point3f(-h,h,-h),
			new Point3f(-h,-h,h),
			new Point3f(h,-h,h),
			new Point3f(h,h,h),
			new Point3f(-h,h,h)
		};
		
		t = new int[] {
			0, 2, 1,
			0, 3, 2,
			0, 5, 4,
			0, 1, 5,
			1, 6, 5,
			1, 2, 6,
			0, 4, 7,
			7, 3, 0,
			2, 3, 7,
			7, 6, 2,
			4, 5, 6,
			6, 7, 4
		};
	}
	
	/** Returns a copy of the cube mesh at the given center position and with given edge length.
		
		@param center The center position of the cube.
		
		@param edge The edge length of the cube.
		
		@throws IllegalArgumentException If {@code edge} is less than {@code 0}.
		
		@return A new {@code Vector<Point3f>} object containing the triangles of the cube mesh. Each vector element is one vertex of the mesh and each successive three elements constitute one triangle. For memory efficiency, vertices are shared between adjacent triangles. That is, the corresponding {@code Point3f} elements are handles of the same object.
	*/
	public Vector<Point3f> render(final Point3f center, final float edge) {
		
		if (edge < 0) throw new IllegalArgumentException("Edge length less than 0");
		
		// Copy and transform vertices:
		final int nv = v.length;
		final Point3f[] vc = new Point3f[nv];
		for (int i=0; i<nv; ++i) {
			final Point3f vi = new Point3f(v[i]);
			// Stretch to edge length:
			vi.x *= edge; vi.y *= edge; vi.z *= edge;
			// Shift to center position:
			vi.x += center.x; vi.y += center.y; vi.z += center.z;
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
