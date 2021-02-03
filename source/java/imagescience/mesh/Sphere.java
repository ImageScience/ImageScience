package imagescience.mesh;

import java.util.Hashtable;
import java.util.Vector;

import org.scijava.vecmath.Point3f;

/** Triangular mesh of a sphere in 3D. The mesh is created by recursive subdivision of an icosahedron. */
public class Sphere {
	
	private Point3f[] v = null; // Vertices
	
	private int[] t = null; // Triangles
	
	private int lod = 0; // Level of detail
	
	/** Constructs the mesh of a unit sphere at the given level of detail.
		
		@param lod The level of detail. The number of subdivisions of the icosahedron. At minimum level {@code 0} the sphere has 20 faces.
		
		@throws IllegalArgumentException If {@code lod} is less than {@code 0}.
	*/
	public Sphere(final int lod) {
		
		if (lod < 0) throw new IllegalArgumentException("Level of detail less than 0");
		
		this.lod = lod;
		
		// Create icosahedron at base level:
		final float a = 0.525731112119133606f;
		final float b = 0.850650808352039932f;
		
		v = new Point3f[] {
			new Point3f(-a,0,b),
			new Point3f(a,0,b),
			new Point3f(-a,0,-b),
			new Point3f(a,0,-b),
			new Point3f(0,b,a),
			new Point3f(0,b,-a),
			new Point3f(0,-b,a),
			new Point3f(0,-b,-a),
			new Point3f(b,a,0),
			new Point3f(-b,a,0),
			new Point3f(b,-a,0),
			new Point3f(-b,-a,0)
		};
		
		t = new int[] {
			0, 1, 4,
			0, 4, 9,
			9, 4, 5,
			4, 8, 5,
			4, 1, 8,
			8, 1, 10,
			8, 10, 3,
			5, 8, 3,
			5, 3, 2,
			2, 3, 7,
			7, 3, 10,
			7, 10, 6,
			7, 6, 11,
			11, 6, 0,
			0, 6, 1,
			6, 10, 1,
			9, 11, 0,
			9, 2, 11,
			9, 5, 2,
			7, 11, 2
		};
		
		// Subdivide icosahedron to given level of detail:
		for (int l=0; l<lod; ++l) {
			final int nv = v.length, nt = t.length;
			final int nvl = nv + nt/2, ntl = 4*nt;
			final Point3f[] vl = new Point3f[nvl]; int vli=0;
			for (int i=0; i<nv; ++i) vl[vli++] = v[i];
			final int[] tl = new int[ntl]; int tli=0;
			final Hashtable<String,Integer> map = new Hashtable<String,Integer>(nt/2);
			for (int i=0; i<nt; i+=3) { // Subdivide every triangle
				final int vi0 = t[i], vi1 = t[i+1], vi2 = t[i+2];
				final String k01 = (vi0 < vi1) ? vi0+"-"+vi1 : vi1+"-"+vi0;
				final String k02 = (vi0 < vi2) ? vi0+"-"+vi2 : vi2+"-"+vi0;
				final String k12 = (vi1 < vi2) ? vi1+"-"+vi2 : vi2+"-"+vi1;
				Integer m01 = map.get(k01), m02 = map.get(k02), m12 = map.get(k12);
				if (m01 == null) { // Create mid-point of edge vi0-vi1
					final Point3f p = new Point3f(v[vi0]);
					p.x += v[vi1].x; p.y += v[vi1].y; p.z += v[vi1].z;
					final double len = Math.sqrt(p.x*p.x+p.y*p.y+p.z*p.z);
					p.x /= len; p.y /= len; p.z /= len;
					m01 = vli; map.put(k01,vli); vl[vli++] = p;
				}
				if (m02 == null) { // Create mid-point of edge vi0-vi2
					final Point3f p = new Point3f(v[vi0]);
					p.x += v[vi2].x; p.y += v[vi2].y; p.z += v[vi2].z;
					final double len = Math.sqrt(p.x*p.x+p.y*p.y+p.z*p.z);
					p.x /= len; p.y /= len; p.z /= len;
					m02 = vli; map.put(k02,vli); vl[vli++] = p;
				}
				if (m12 == null) { // Create mid-point of edge vi1-vi2
					final Point3f p = new Point3f(v[vi1]);
					p.x += v[vi2].x; p.y += v[vi2].y; p.z += v[vi2].z;
					final double len = Math.sqrt(p.x*p.x+p.y*p.y+p.z*p.z);
					p.x /= len; p.y /= len; p.z /= len;
					m12 = vli; map.put(k12,vli); vl[vli++] = p;
				}
				tl[tli++] = vi0; tl[tli++] = m01; tl[tli++] = m02;
				tl[tli++] = m01; tl[tli++] = vi1; tl[tli++] = m12;
				tl[tli++] = m01; tl[tli++] = m12; tl[tli++] = m02;
				tl[tli++] = m02; tl[tli++] = m12; tl[tli++] = vi2;
			}
			v = vl; t = tl;
		}
	}
	
	/** Returns a copy of the sphere mesh at the given center position and with given radius.
		
		@param center The center position of the sphere.
		
		@param radius The radius of the sphere.
		
		@throws IllegalArgumentException If {@code radius} is less than {@code 0}.
		
		@return A new {@code Vector<Point3f>} object containing the triangles of the sphere mesh. Each vector element is one vertex of the mesh and each successive three elements constitute one triangle. For memory efficiency, vertices are shared between adjacent triangles. That is, the corresponding {@code Point3f} elements are handles of the same object.
	*/
	public Vector<Point3f> render(final Point3f center, final float radius) {
		
		if (radius < 0) throw new IllegalArgumentException("Radius less than 0");
		
		// Copy and transform vertices:
		final int nv = v.length;
		final Point3f[] vc = new Point3f[nv];
		for (int i=0; i<nv; ++i) {
			final Point3f vi = new Point3f(v[i]);
			vi.x = vi.x*radius + center.x;
			vi.y = vi.y*radius + center.y;
			vi.z = vi.z*radius + center.z;
			vc[i] = vi;
		}
		
		// Copy triangles:
		final int nt = t.length;
		final Vector<Point3f> mesh = new Vector<Point3f>(nt);
		for (int i=0; i<nt; ++i) mesh.add(vc[t[i]]);
		return mesh;
	}
	
}
