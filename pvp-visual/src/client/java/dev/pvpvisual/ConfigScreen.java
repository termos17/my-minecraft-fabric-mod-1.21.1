package dev.pvpvisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Меню настроек: боковые вкладки, прокрутка, анимированные переключатели, числа, цвета, языки. */
public class ConfigScreen extends Screen {
	private static final int HEADER = 38;
	private static final int FOOTER = 22;
	private static final int SIDEBAR = 108;
	private static final int ROW_H = 22;
	private static final int CTRL_W = 118;
	private static final int TAB_H = 20;
	private static final int PAD = 10;
	private static final int COLORS_TAB = 5;

	private static final String[] TAB_KEYS = {
			"pv.tab.combat", "pv.tab.world", "pv.tab.pearls", "pv.tab.potions",
			"pv.tab.hands", "pv.tab.colors", "pv.tab.settings"};

	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0B4;

	private static int lastTab;

	private int tab = lastTab;
	private int scroll;
	private final List<List<Row>> tabs = new ArrayList<>();

	public ConfigScreen() {
		super(Component.literal("PvP Visual"));
	}

	// ------------------------------------------------------------ строки

	private abstract static class Row {
		final String labelKey;
		int ctrlX;
		int ctrlW;

		Row(String labelKey) {
			this.labelKey = labelKey;
		}

		abstract void renderControl(GuiGraphics g, Font font, int x, int y, boolean hover, int accent);

		abstract void click(int button, double mouseX);
	}

	private static final class Toggle extends Row {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private float anim;

		Toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
			super(key);
			this.get = get;
			this.set = set;
			this.anim = get.getAsBoolean() ? 1f : 0f;
		}

		@Override
		void renderControl(GuiGraphics g, Font font, int x, int y, boolean hover, int accent) {
			anim += ((get.getAsBoolean() ? 1f : 0f) - anim) * 0.3f;
			int w = 30;
			int h = 14;
			int bx = x + CTRL_W - w;
			ctrlX = bx;
			ctrlW = w;
			pill(g, bx, y, w, h, 0xFF000000 | Theme.lerp(0x4A4A5C, accent, anim));
			pill(g, bx + 2 + Math.round(anim * (w - h)), y + 2, h - 4, h - 4, WHITE);
		}

		@Override
		void click(int button, double mouseX) {
			set.accept(!get.getAsBoolean());
		}
	}

	/** Выбор из списка значений. ЛКМ — дальше, ПКМ — назад. Необязательная цветная плашка. */
	private static final class Cycle extends Row {
		private final Supplier<String> value;
		private final IntConsumer step;
		private final IntSupplier chip;

		Cycle(String key, Supplier<String> value, IntConsumer step, IntSupplier chip) {
			super(key);
			this.value = value;
			this.step = step;
			this.chip = chip;
		}

		@Override
		void renderControl(GuiGraphics g, Font font, int x, int y, boolean hover, int accent) {
			ctrlX = x;
			ctrlW = CTRL_W;
			int h = 16;
			pill(g, x, y - 1, CTRL_W, h, hover ? 0xFF3A3A4E : 0xFF2A2A3A);
			int arrow = hover ? 0xFF000000 | accent : GRAY;
			g.drawString(font, "<", x + 5, y + 3, arrow);
			g.drawString(font, ">", x + CTRL_W - 5 - font.width(">"), y + 3, arrow);
			String text = value.get();
			int chipW = 0;
			if (chip != null) {
				int rgb = chip.getAsInt();
				if (rgb >= 0) {
					chipW = 12;
					pill(g, x + 16, y + 1, 8, 8, 0xFF000000 | rgb);
				}
			}
			int maxW = CTRL_W - 36 - chipW;
			int tw = font.width(text);
			float s = tw > maxW ? maxW / (float) tw : 1f;
			int cx = x + 18 + chipW + (int) ((maxW - tw * s) / 2);
			drawScaled(g, font, text, cx, y + 3, s, WHITE);
		}

		@Override
		void click(int button, double mouseX) {
			step.accept(button == 1 ? -1 : 1);
		}
	}

	/** Числовое значение: клик слева — меньше, справа — больше, ПКМ — сброс. */
	private static final class Num extends Row {
		private final DoubleSupplier get;
		private final DoubleConsumer set;
		private final double min;
		private final double max;
		private final double step;
		private final double def;

		Num(String key, DoubleSupplier get, DoubleConsumer set, double min, double max, double step, double def) {
			super(key);
			this.get = get;
			this.set = set;
			this.min = min;
			this.max = max;
			this.step = step;
			this.def = def;
		}

		@Override
		void renderControl(GuiGraphics g, Font font, int x, int y, boolean hover, int accent) {
			ctrlX = x;
			ctrlW = CTRL_W;
			pill(g, x, y - 1, CTRL_W, 16, hover ? 0xFF3A3A4E : 0xFF2A2A3A);
			int arrow = hover ? 0xFF000000 | accent : GRAY;
			g.drawString(font, "<", x + 5, y + 3, arrow);
			g.drawString(font, ">", x + CTRL_W - 5 - font.width(">"), y + 3, arrow);
			String text = format(get.getAsDouble());
			g.drawString(font, text, x + (CTRL_W - font.width(text)) / 2, y + 3, WHITE);
			// тонкая шкала значения под числом
			double t = (get.getAsDouble() - min) / (max - min);
			g.fill(x + 8, y + 13, x + CTRL_W - 8, y + 14, 0x55FFFFFF);
			g.fill(x + 8, y + 13, x + 8 + (int) ((CTRL_W - 16) * Math.max(0, Math.min(1, t))), y + 14, 0xFF000000 | accent);
		}

		@Override
		void click(int button, double mouseX) {
			if (button == 1) {
				set.accept(def);
				return;
			}
			double v = get.getAsDouble() + (mouseX < ctrlX + ctrlW / 2.0 ? -step : step);
			v = Math.round(v / step) * step;
			set.accept(Math.max(min, Math.min(max, v)));
		}

		private String format(double v) {
			return step >= 1 ? String.valueOf((int) Math.round(v)) : String.format(Locale.ROOT, "%.2f", v);
		}
	}

	// ---------------------------------------------------------------- init

	@Override
	protected void init() {
		PvpConfig c = PvpConfig.get();
		tabs.clear();

		// Бой
		List<Row> combat = new ArrayList<>();
		combat.add(new Toggle("pv.opt.target_hud", () -> c.targetHud, v -> c.targetHud = v));
		combat.add(new Toggle("pv.opt.crosshair", () -> c.ringCrosshair, v -> c.ringCrosshair = v));
		combat.add(new Toggle("pv.opt.hit_particles", () -> c.hitParticles, v -> c.hitParticles = v));
		combat.add(new Toggle("pv.opt.hit_sound", () -> c.hitSound, v -> c.hitSound = v));
		combat.add(new Toggle("pv.opt.hit_wave", () -> c.hitWave, v -> c.hitWave = v));
		combat.add(new Toggle("pv.opt.target_ring", () -> c.targetRing, v -> c.targetRing = v));
		tabs.add(combat);

		// Мир
		List<Row> world = new ArrayList<>();
		world.add(new Toggle("pv.opt.hat", () -> c.chinaHat, v -> c.chinaHat = v));
		world.add(new Num("pv.opt.hat_size", () -> c.hatSize, v -> c.hatSize = (float) v, 0.3, 1.5, 0.05, 0.72));
		world.add(new Toggle("pv.opt.jump_rings", () -> c.jumpCircles, v -> c.jumpCircles = v));
		world.add(new Num("pv.opt.ring_scale", () -> c.ringScale, v -> c.ringScale = (float) v, 0.5, 2.5, 0.1, 1.0));
		world.add(new Toggle("pv.opt.trail", () -> c.trail, v -> c.trail = v));
		world.add(new Num("pv.opt.trail_len", () -> c.trailLength, v -> c.trailLength = (int) Math.round(v), 6, 80, 2, 24));
		tabs.add(world);

		// Жемчуг
		List<Row> pearls = new ArrayList<>();
		pearls.add(new Toggle("pv.opt.pearl_predict", () -> c.pearlPrediction, v -> c.pearlPrediction = v));
		pearls.add(new Toggle("pv.opt.pearl_trail", () -> c.pearlTrail, v -> c.pearlTrail = v));
		pearls.add(new Toggle("pv.opt.pearl_enemy", () -> c.pearlEnemy, v -> c.pearlEnemy = v));
		pearls.add(list("pv.opt.pearl_line", PvpConfig.LINE_KEYS, () -> c.pearlLine, v -> c.pearlLine = v));
		pearls.add(list("pv.opt.pearl_marker", PvpConfig.MARKER_KEYS, () -> c.pearlMarker, v -> c.pearlMarker = v));
		tabs.add(pearls);

		// Зелья
		List<Row> potions = new ArrayList<>();
		potions.add(new Toggle("pv.opt.potion_predict", () -> c.potionPredict, v -> c.potionPredict = v));
		potions.add(new Toggle("pv.opt.potion_enemy", () -> c.potionEnemy, v -> c.potionEnemy = v));
		potions.add(new Toggle("pv.opt.potion_label", () -> c.potionLabel, v -> c.potionLabel = v));
		potions.add(new Toggle("pv.opt.potion_area", () -> c.potionArea, v -> c.potionArea = v));
		potions.add(list("pv.opt.potion_line", PvpConfig.LINE_KEYS, () -> c.potionLine, v -> c.potionLine = v));
		tabs.add(potions);

		// Руки
		List<Row> hands = new ArrayList<>();
		hands.add(new Toggle("pv.opt.hands_on", () -> c.handsEnabled, v -> c.handsEnabled = v));
		hands.add(new Num("pv.opt.hand_x", () -> c.handX, v -> c.handX = (float) v, -1.0, 1.0, 0.05, 0.0));
		hands.add(new Num("pv.opt.hand_y", () -> c.handY, v -> c.handY = (float) v, -1.0, 1.0, 0.05, 0.0));
		hands.add(new Num("pv.opt.hand_z", () -> c.handZ, v -> c.handZ = (float) v, -1.0, 1.0, 0.05, 0.0));
		hands.add(new Num("pv.opt.hand_scale", () -> c.handScale, v -> c.handScale = (float) v, 0.3, 2.0, 0.05, 1.0));
		hands.add(new Num("pv.opt.hand_roll", () -> c.handRoll, v -> c.handRoll = (float) v, -90, 90, 5, 0));
		tabs.add(hands);

		// Цвета
		List<Row> colors = new ArrayList<>();
		colors.add(colorRow("pv.col.global", false, () -> c.colorIndex, v -> c.colorIndex = v));
		colors.add(colorRow("pv.col.crosshair", true, () -> c.colCrosshair, v -> c.colCrosshair = v));
		colors.add(colorRow("pv.col.hud", true, () -> c.colHud, v -> c.colHud = v));
		colors.add(colorRow("pv.col.target", true, () -> c.colTarget, v -> c.colTarget = v));
		colors.add(colorRow("pv.col.rings", true, () -> c.colRings, v -> c.colRings = v));
		colors.add(colorRow("pv.col.hat", true, () -> c.colHat, v -> c.colHat = v));
		colors.add(colorRow("pv.col.trail", true, () -> c.colTrail, v -> c.colTrail = v));
		colors.add(colorRow("pv.col.pearl", true, () -> c.colPearl, v -> c.colPearl = v));
		colors.add(colorRow("pv.col.potion", true, () -> c.colPotion, v -> c.colPotion = v));
		tabs.add(colors);

		// Настройки
		List<Row> settings = new ArrayList<>();
		settings.add(new Cycle("pv.opt.language", () -> Lang.languageName(c.language),
				d -> c.language = Math.floorMod(c.language + d, Lang.CODES.length), null));
		settings.add(new Cycle("pv.opt.particles", () -> String.valueOf(PvpConfig.PARTICLE_COUNTS[c.particleCountIndex]),
				d -> c.particleCountIndex = Math.floorMod(c.particleCountIndex + d, PvpConfig.PARTICLE_COUNTS.length), null));
		settings.add(list("pv.opt.style", PvpConfig.STYLE_KEYS, () -> c.hitStyle, v -> c.hitStyle = v));
		settings.add(list("pv.opt.sound", PvpConfig.SOUND_KEYS, () -> c.soundIndex, v -> c.soundIndex = v));
		settings.add(new Cycle("pv.opt.ring_size", () -> String.valueOf(PvpConfig.RING_SIZES[c.ringSizeIndex]),
				d -> c.ringSizeIndex = Math.floorMod(c.ringSizeIndex + d, PvpConfig.RING_SIZES.length), null));
		tabs.add(settings);
	}

	private static Row list(String labelKey, String[] valueKeys, IntSupplier get, IntConsumer set) {
		return new Cycle(labelKey, () -> Lang.t(valueKeys[get.getAsInt()]),
				d -> set.accept(Math.floorMod(get.getAsInt() + d, valueKeys.length)), null);
	}

	/** allowGlobal = true: значение -1 («Общий») входит в круг выбора. */
	private static Row colorRow(String labelKey, boolean allowGlobal, IntSupplier get, IntConsumer set) {
		int min = allowGlobal ? -1 : 0;
		int span = Theme.COUNT - min;
		return new Cycle(labelKey,
				() -> get.getAsInt() < 0 ? Lang.t("pv.val.global") : Theme.name(get.getAsInt()),
				d -> set.accept(min + Math.floorMod(get.getAsInt() - min + d, span)),
				() -> get.getAsInt() < 0 ? -1 : Theme.color(get.getAsInt(), 0f));
	}

	// -------------------------------------------------------------- геометрия

	private int pw() {
		return Math.min(430, this.width - 12);
	}

	private int ph() {
		return Math.min(240, this.height - 12);
	}

	private int px() {
		return (this.width - pw()) / 2;
	}

	private int py() {
		return (this.height - ph()) / 2;
	}

	private int rowsX() {
		return px() + SIDEBAR + 6;
	}

	private int rowsW() {
		return pw() - SIDEBAR - 6 - PAD;
	}

	private int rowsY() {
		return py() + HEADER + 8;
	}

	private int visibleRows() {
		return Math.max(1, (ph() - HEADER - 8 - FOOTER) / ROW_H);
	}

	private int maxScroll() {
		return Math.max(0, tabs.get(tab).size() - visibleRows());
	}

	// -------------------------------------------------------------- рендер

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta); // фон
		PvpConfig c = PvpConfig.get();
		int x = px();
		int y = py();
		int pw = pw();
		int ph = ph();
		int accent = Theme.color(c.colorIndex, 0f);

		panel(g, x - 2, y - 2, pw + 4, ph + 4, 0x55000000);
		panel(g, x, y, pw, ph, 0xF0101018);

		// Заголовок
		g.pose().pushMatrix();
		g.pose().scale(1.5f);
		g.drawString(this.font, "PvP Visual", (int) ((x + PAD + 2) / 1.5f), (int) ((y + 9) / 1.5f), WHITE);
		g.pose().popMatrix();
		String sub = Lang.t("pv.subtitle");
		g.drawString(this.font, sub, x + pw - PAD - this.font.width(sub), y + 14, GRAY);
		for (int i = 0; i < pw; i += 2) {
			int base = Theme.color(c.colorIndex, i / (float) pw);
			g.fill(x + i, y + 34, x + i + 2, y + 36, 0xFF000000 | Theme.lerp(base, 0xFFFFFF, i * 0.35f / pw));
		}

		// Боковые вкладки
		for (int i = 0; i < TAB_KEYS.length; i++) {
			int tx = x + PAD;
			int ty = y + HEADER + 8 + i * (TAB_H + 3);
			boolean selected = i == tab;
			boolean hover = mouseX >= tx && mouseX < tx + SIDEBAR - PAD && mouseY >= ty && mouseY < ty + TAB_H;
			pill(g, tx, ty, SIDEBAR - PAD, TAB_H, selected ? 0x44000000 | accent : hover ? 0x30FFFFFF : 0x18FFFFFF);
			if (selected) {
				g.fill(tx, ty + 3, tx + 2, ty + TAB_H - 3, 0xFF000000 | accent);
			}
			fit(g, Lang.t(TAB_KEYS[i]), tx + 8, ty + 6, SIDEBAR - PAD - 14, selected ? WHITE : GRAY);
		}

		// Строки
		List<Row> rows = tabs.get(tab);
		int visible = visibleRows();
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) {
			Row row = rows.get(i);
			int ry = rowsY() + (i - scroll) * ROW_H;
			boolean hover = inRow(mouseX, mouseY, i);
			pill(g, rowsX(), ry, rowsW(), ROW_H - 2, hover ? 0x40FFFFFF : 0x1EFFFFFF);
			fit(g, Lang.t(row.labelKey), rowsX() + 8, ry + (ROW_H - 2 - 8) / 2, rowsW() - CTRL_W - 24, WHITE);
			row.renderControl(g, this.font, rowsX() + rowsW() - CTRL_W - 8, ry + 4, hover, accent);
		}

		// Полоса прокрутки
		if (rows.size() > visible) {
			int trackH = visible * ROW_H - 2;
			int thumbH = Math.max(12, trackH * visible / rows.size());
			int thumbY = rowsY() + (trackH - thumbH) * scroll / Math.max(1, maxScroll());
			g.fill(x + pw - 6, rowsY(), x + pw - 4, rowsY() + trackH, 0x22FFFFFF);
			g.fill(x + pw - 6, thumbY, x + pw - 4, thumbY + thumbH, 0xFF000000 | accent);
		}

		fit(g, Lang.t("pv.hint"), x + PAD, y + ph - 15, pw - 2 * PAD, 0xFF70708A);
	}

	// ---------------------------------------------------------------- ввод

	private boolean inRow(double mx, double my, int index) {
		if (index < scroll || index >= scroll + visibleRows()) {
			return false;
		}
		int ry = rowsY() + (index - scroll) * ROW_H;
		return mx >= rowsX() && mx < rowsX() + rowsW() && my >= ry && my < ry + ROW_H - 2;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		int button = event.button();
		if (button != 0 && button != 1) {
			return super.mouseClicked(event, doubleClick);
		}

		for (int i = 0; i < TAB_KEYS.length; i++) {
			int tx = px() + PAD;
			int ty = py() + HEADER + 8 + i * (TAB_H + 3);
			if (mx >= tx && mx < tx + SIDEBAR - PAD && my >= ty && my < ty + TAB_H) {
				tab = i;
				lastTab = i;
				scroll = 0;
				return true;
			}
		}

		List<Row> rows = tabs.get(tab);
		for (int i = 0; i < rows.size(); i++) {
			if (inRow(mx, my, i)) {
				rows.get(i).click(button, mx);
				PvpConfig.save();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (maxScroll() > 0 && scrollY != 0) {
			scroll = Math.max(0, Math.min(maxScroll(), scroll + (scrollY > 0 ? -1 : 1)));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		PvpConfig.save();
		super.onClose();
	}

	// -------------------------------------------------------------- рисование

	/** Текст, который сжимается, если не помещается в maxW (длинные переводы). */
	private void fit(GuiGraphics g, String text, int x, int y, int maxW, int color) {
		int w = this.font.width(text);
		drawScaled(g, this.font, text, x, y, w > maxW ? maxW / (float) w : 1f, color);
	}

	private static void drawScaled(GuiGraphics g, Font font, String text, int x, int y, float s, int color) {
		if (s >= 0.999f) {
			g.drawString(font, text, x, y, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().scale(s);
		g.drawString(font, text, Math.round(x / s), Math.round((y + 4) / s - 4), color);
		g.pose().popMatrix();
	}

	private static void panel(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x + 2, y, x + w - 2, y + 1, color);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		g.fill(x, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	private static void pill(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x + 1, y, x + w - 1, y + 1, color);
		g.fill(x, y + 1, x + w, y + h - 1, color);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
	}
}
