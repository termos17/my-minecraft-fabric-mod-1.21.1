package dev.pvpvisual;

/** Цветовые темы. Возвращает RGB без альфа-канала (0xRRGGBB). */
public final class Theme {
	public static final String[] IDS = {"cyan", "pink", "purple", "red", "green", "gold", "rainbow"};
	public static final int COUNT = IDS.length;
	private static final int[] RGB = {0x00E5FF, 0xFF4DA6, 0xA855F7, 0xFF3B3B, 0x3BFF7A, 0xFFC107, -1};

	private Theme() {
	}

	public static String name(int index) {
		return Lang.t("pv.theme." + IDS[Math.floorMod(index, COUNT)]);
	}

	/** @param offset сдвиг оттенка 0..1 (имеет значение только для радуги) */
	public static int color(int index, float offset) {
		int i = Math.floorMod(index, COUNT);
		if (RGB[i] >= 0) {
			return RGB[i];
		}
		float hue = (System.currentTimeMillis() % 4000L) / 4000f + offset;
		return hsv(hue - (float) Math.floor(hue));
	}

	/** Градиент для 3D-эффектов: радуга для «Радуги», иначе переливающийся оттенок выбранного цвета. */
	public static int grad(int index, float t) {
		int i = Math.floorMod(index, COUNT);
		if (RGB[i] < 0) {
			return color(i, t);
		}
		double wave = 0.5 + 0.5 * Math.sin((t * 2.0 + (System.currentTimeMillis() % 3000L) / 3000.0) * Math.PI * 2);
		return lerp(RGB[i], 0xFFFFFF, (float) (0.1 + 0.45 * wave));
	}

	public static int lerp(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int hsv(float h) {
		float x = h * 6f;
		int i = ((int) x) % 6;
		float f = x - (int) x;
		float q = 1f - f;
		float r;
		float g;
		float b;
		switch (i) {
			case 0 -> { r = 1; g = f; b = 0; }
			case 1 -> { r = q; g = 1; b = 0; }
			case 2 -> { r = 0; g = 1; b = f; }
			case 3 -> { r = 0; g = q; b = 1; }
			case 4 -> { r = f; g = 0; b = 1; }
			default -> { r = 1; g = 0; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
