package dev.pvpvisual;

import java.util.ArrayDeque;

/** След из точек (новые — в начале). Точки стареют каждый тик и пропадают. */
public final class Trail {
	public static final class Pt {
		public final double x;
		public final double y;
		public final double z;
		public int age;

		Pt(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public final ArrayDeque<Pt> pts = new ArrayDeque<>();

	public void add(double x, double y, double z) {
		pts.addFirst(new Pt(x, y, z));
	}

	public void tick(int maxAge) {
		for (Pt p : pts) {
			p.age++;
		}
		while (!pts.isEmpty() && pts.peekLast().age > maxAge) {
			pts.removeLast();
		}
	}

	/** Расстояние до самой новой точки в квадрате (или MAX, если точек нет). */
	public double distSqToNewest(double x, double y, double z) {
		Pt p = pts.peekFirst();
		if (p == null) {
			return Double.MAX_VALUE;
		}
		double dx = p.x - x;
		double dy = p.y - y;
		double dz = p.z - z;
		return dx * dx + dy * dy + dz * dz;
	}
}
