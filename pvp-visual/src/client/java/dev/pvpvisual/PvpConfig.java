package dev.pvpvisual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Настройки мода. Хранятся в .minecraft/config/pvpvisual.json */
public final class PvpConfig {
	public static final int[] PARTICLE_COUNTS = {8, 16, 32, 64};
	public static final int[] RING_SIZES = {4, 6, 8, 10};
	public static final String[] STYLE_KEYS = {"pv.style.colored", "pv.style.crit", "pv.style.stars", "pv.style.enchant"};
	public static final String[] SOUND_KEYS = {"pv.sound.orb", "pv.sound.crit", "pv.sound.levelup"};
	public static final String[] LINE_KEYS = {"pv.line.ribbon", "pv.line.dots", "pv.line.dashed"};
	public static final String[] MARKER_KEYS = {"pv.marker.ring", "pv.marker.cross", "pv.marker.beam", "pv.marker.ring_beam"};

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pvpvisual.json");
	private static PvpConfig instance = new PvpConfig();

	// --- Бой
	public boolean targetHud = true;
	public boolean ringCrosshair = true;
	public boolean hitParticles = true;
	public boolean hitSound = true;
	public boolean hitWave = true;
	public boolean targetRing = true;

	// --- Мир
	public boolean chinaHat = true;
	public float hatSize = 0.72f;
	public boolean jumpCircles = true;
	public float ringScale = 1.0f;
	public boolean trail = true;
	public int trailLength = 24;

	// --- Жемчуг
	public boolean pearlPrediction = true;
	public boolean pearlTrail = true;
	public boolean pearlEnemy = true;
	public int pearlLine = 0;
	public int pearlMarker = 3;

	// --- Зелья
	public boolean potionPredict = true;
	public boolean potionEnemy = true;
	public boolean potionLabel = true;
	public boolean potionArea = true;
	public int potionLine = 2;

	// --- Руки
	public boolean handsEnabled = false;
	public float handX = 0f;
	public float handY = 0f;
	public float handZ = 0f;
	public float handScale = 1f;
	public float handRoll = 0f;

	// --- Цвета (-1 = общий цвет)
	public int colorIndex = 0;
	public int colCrosshair = -1;
	public int colHud = -1;
	public int colTarget = -1;
	public int colRings = -1;
	public int colHat = -1;
	public int colTrail = -1;
	public int colPearl = -1;
	public int colPotion = -1;

	// --- Прочее
	public int language = 0;
	public int particleCountIndex = 1;
	public int hitStyle = 0;
	public int soundIndex = 0;
	public int ringSizeIndex = 1;

	public static PvpConfig get() {
		return instance;
	}

	/** Эффективный индекс цвета для функции: -1 → общий цвет. */
	public int eff(int feature) {
		return feature < 0 ? colorIndex : feature;
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				PvpConfig loaded = GSON.fromJson(Files.readString(FILE), PvpConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			}
		} catch (Exception e) {
			System.err.println("[PvpVisual] Не удалось прочитать конфиг, использую настройки по умолчанию: " + e);
		}
		clamp();
		save();
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(instance));
		} catch (IOException e) {
			System.err.println("[PvpVisual] Не удалось сохранить конфиг: " + e);
		}
	}

	private static void clamp() {
		PvpConfig c = instance;
		c.colorIndex = Math.floorMod(c.colorIndex, Theme.COUNT);
		c.colCrosshair = colorOrGlobal(c.colCrosshair);
		c.colHud = colorOrGlobal(c.colHud);
		c.colTarget = colorOrGlobal(c.colTarget);
		c.colRings = colorOrGlobal(c.colRings);
		c.colHat = colorOrGlobal(c.colHat);
		c.colTrail = colorOrGlobal(c.colTrail);
		c.colPearl = colorOrGlobal(c.colPearl);
		c.colPotion = colorOrGlobal(c.colPotion);
		c.language = Math.floorMod(c.language, Lang.CODES.length);
		c.particleCountIndex = Math.floorMod(c.particleCountIndex, PARTICLE_COUNTS.length);
		c.hitStyle = Math.floorMod(c.hitStyle, STYLE_KEYS.length);
		c.soundIndex = Math.floorMod(c.soundIndex, SOUND_KEYS.length);
		c.ringSizeIndex = Math.floorMod(c.ringSizeIndex, RING_SIZES.length);
		c.pearlLine = Math.floorMod(c.pearlLine, LINE_KEYS.length);
		c.potionLine = Math.floorMod(c.potionLine, LINE_KEYS.length);
		c.pearlMarker = Math.floorMod(c.pearlMarker, MARKER_KEYS.length);
		c.hatSize = Math.max(0.3f, Math.min(1.5f, c.hatSize));
		c.ringScale = Math.max(0.5f, Math.min(2.5f, c.ringScale));
		c.trailLength = Math.max(6, Math.min(80, c.trailLength));
		c.handX = Math.max(-1f, Math.min(1f, c.handX));
		c.handY = Math.max(-1f, Math.min(1f, c.handY));
		c.handZ = Math.max(-1f, Math.min(1f, c.handZ));
		c.handScale = Math.max(0.3f, Math.min(2f, c.handScale));
		c.handRoll = Math.max(-90f, Math.min(90f, c.handRoll));
	}

	private static int colorOrGlobal(int v) {
		return v < 0 ? -1 : Math.min(v, Theme.COUNT - 1);
	}
}
