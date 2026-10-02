package dev.pvpvisual;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/** 2D-элементы интерфейса: кольцевой прицел и Target HUD. */
public final class Hud {
	private static final Identifier TARGET_HUD = Identifier.fromNamespaceAndPath("pvpvisual", "target_hud");
	private static final Identifier INFO_HUD = Identifier.fromNamespaceAndPath("pvpvisual", "info_lines");

	private Hud() {
	}

	public static void register() {
		// Подменяем ванильный прицел. Условия показа (от первого лица и т.д.) сохраняются.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, delta) -> {
			if (PvpConfig.get().ringCrosshair) {
				renderRing(graphics);
			} else {
				original.render(graphics, delta);
			}
		});
		HudElementRegistry.addLast(TARGET_HUD, Hud::renderTargetHud);
		HudElementRegistry.addLast(INFO_HUD, Hud::renderInfoLines);
	}

	// ---------------------------------------------------------------- кольцо

	private static void renderRing(GuiGraphics graphics) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		PvpConfig cfg = PvpConfig.get();
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		int radius = PvpConfig.RING_SIZES[cfg.ringSizeIndex];

		// Кольцо заполняется по кулдауну атаки; после удара вспыхивает красным.
		float charge = Math.min(1f, mc.player.getAttackStrengthScale(0f));
		float flash = Visuals.hitFlash / 6f;
		int rgb = Theme.lerp(Theme.color(cfg.eff(cfg.colCrosshair), 0f), 0xFF3030, flash);
		int bright = 0xFF000000 | rgb;
		int dim = 0x55000000 | rgb;

		int steps = (int) (Math.PI * 2 * radius * 1.6);
		for (int i = 0; i < steps; i++) {
			double angle = -Math.PI / 2 + Math.PI * 2 * i / steps;
			int x = cx + (int) Math.round(Math.cos(angle) * radius);
			int y = cy + (int) Math.round(Math.sin(angle) * radius);
			graphics.fill(x, y, x + 1, y + 1, (float) i / steps <= charge ? bright : dim);
		}
		graphics.fill(cx, cy, cx + 1, cy + 1, bright); // точка в центре
	}

	// ------------------------------------------------------------ Target HUD

	private static void renderTargetHud(GuiGraphics graphics, DeltaTracker delta) {
		PvpConfig cfg = PvpConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (!cfg.targetHud || mc.player == null || mc.options.hideGui) {
			return;
		}
		Entity entity = Visuals.target;
		if (!(entity instanceof LivingEntity living) || entity.isRemoved() || Visuals.targetTicks <= 0) {
			return;
		}

		// Плавное исчезновение в последние 10 тиков
		float fade = Math.min(1f, Visuals.targetTicks / 10f);
		int alpha = (int) (255 * fade);
		if (alpha < 8) {
			return;
		}

		float max = Math.max(1f, living.getMaxHealth());
		float health = Math.max(0f, living.getHealth());
		Visuals.displayHealth += (health - Visuals.displayHealth) * 0.12f; // плавная анимация полоски
		float ratio = Math.min(1f, Visuals.displayHealth / max);

		int accent = Theme.color(cfg.eff(cfg.colHud), 0f);
		int width = 124;
		int height = 38;
		int x = graphics.guiWidth() / 2 + 40;
		int y = graphics.guiHeight() / 2 - height / 2;

		// Фон + цветная полоса слева
		graphics.fill(x, y, x + width, y + height, ((int) (alpha * 0.6f) << 24));
		graphics.fill(x, y, x + 2, y + height, (alpha << 24) | accent);

		// Ник
		graphics.drawString(mc.font, entity.getName().getString(), x + 7, y + 5, (alpha << 24) | 0xFFFFFF);

		// Полоска здоровья
		int barX = x + 7;
		int barY = y + 18;
		int barW = width - 14;
		int barH = 6;
		graphics.fill(barX, barY, barX + barW, barY + barH, ((int) (alpha * 0.35f) << 24) | 0xFFFFFF);
		int healthColor = Theme.lerp(0xFF3030, 0x3BFF5A, ratio);
		graphics.fill(barX, barY, barX + Math.round(barW * ratio), barY + barH, (alpha << 24) | healthColor);

		// Золотые сердца (поглощение) поверх полоски
		float absorption = living.getAbsorptionAmount();
		if (absorption > 0f) {
			int absW = Math.round(barW * Math.min(1f, absorption / max));
			graphics.fill(barX, barY + barH - 2, barX + absW, barY + barH, (alpha << 24) | 0xFFD700);
		}

		// Текст: HP и дистанция
		String info = String.format(Locale.ROOT, "%.1f HP   %.1f m", health, mc.player.distanceTo(entity));
		graphics.drawString(mc.font, info, x + 7, y + 27, (alpha << 24) | 0xC8C8C8);
	}

	// ------------------------------------------------- подписи под прицелом

	/** Дистанция до точки падения жемчуга и название зелья в руке (заполняется в WorldFx). */
	private static void renderInfoLines(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.screen != null) {
			return;
		}
		PvpConfig cfg = PvpConfig.get();
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + PvpConfig.RING_SIZES[cfg.ringSizeIndex] + 12;

		if (Visuals.hudPearl != null) {
			int rgb = Theme.color(cfg.eff(cfg.colPearl), 0f);
			graphics.drawCenteredString(mc.font, Visuals.hudPearl, cx, y, 0xFF000000 | rgb);
			y += 11;
		}
		if (Visuals.hudPotion != null) {
			int rgb = Theme.color(cfg.eff(cfg.colPotion), 0f);
			graphics.drawCenteredString(mc.font, Visuals.hudPotion, cx, y, 0xFF000000 | rgb);
		}
	}
}
