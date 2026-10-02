package dev.pvpvisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Настоящие 3D-эффекты: кольца, китайская шляпа, ленты-следы, траектории жемчуга и зелий,
 * метки приземления, кольцо на цели. Рисуется цветными квадами (debugFilledBox).
 */
public final class WorldFx {
	private static final int SEG = 48;

	private static double camX;
	private static double camY;
	private static double camZ;

	private static final class Path {
		final List<Vec3> pts = new ArrayList<>();
		Vec3 landing;
	}

	private WorldFx() {
	}

	public static void register() {
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldFx::render);
	}

	// ------------------------------------------------------------ главный проход

	private static void render(WorldRenderContext ctx) {
		Visuals.hudPearl = null;
		Visuals.hudPotion = null;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return;
		}
		PvpConfig cfg = PvpConfig.get();

		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;

		VertexConsumer vc = ctx.consumers().getBuffer(RenderTypes.debugFilledBox());
		Matrix4f m = ctx.matrices().last().pose();
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

		double px = Mth.lerp(pt, player.xo, player.getX());
		double py = Mth.lerp(pt, player.yo, player.getY());
		double pz = Mth.lerp(pt, player.zo, player.getZ());

		// 1. Кольца (прыжок / удар)
		int ringCi = cfg.eff(cfg.colRings);
		for (Visuals.Ring ring : Visuals.RINGS) {
			renderRing(vc, m, ring, pt, ringCi);
		}

		// 2. Китайская шляпа — только от третьего лица
		if (cfg.chinaHat && !mc.options.getCameraType().isFirstPerson() && !player.isSpectator()) {
			chinaHat(vc, m, px, py + player.getBbHeight(), pz, cfg.eff(cfg.colHat), cfg.hatSize);
		}

		// 3. Кольцо на цели
		if (cfg.targetRing) {
			targetRing(vc, m, pt, cfg.eff(cfg.colTarget));
		}

		// 4. Лента за игроком
		if (cfg.trail && !Visuals.SELF_TRAIL.pts.isEmpty()) {
			List<Vec3> pts = new ArrayList<>();
			pts.add(new Vec3(px, py, pz));
			for (Trail.Pt p : Visuals.SELF_TRAIL.pts) {
				pts.add(new Vec3(p.x, p.y, p.z));
			}
			ribbon(vc, m, pts, 0.14, 0.22, 0.03, 190, 0, cfg.eff(cfg.colTrail), false);
		}

		// 5. Ленты за жемчугами
		if (cfg.pearlTrail) {
			for (Entity entity : level.entitiesForRendering()) {
				if (entity.getType() != EntityType.ENDER_PEARL) {
					continue;
				}
				Trail trail = Visuals.PEARL_TRAILS.get(entity.getId());
				if (trail == null || trail.pts.isEmpty()) {
					continue;
				}
				List<Vec3> pts = new ArrayList<>();
				pts.add(new Vec3(Mth.lerp(pt, entity.xo, entity.getX()),
						Mth.lerp(pt, entity.yo, entity.getY()) + 0.125,
						Mth.lerp(pt, entity.zo, entity.getZ())));
				for (Trail.Pt p : trail.pts) {
					pts.add(new Vec3(p.x, p.y, p.z));
				}
				ribbon(vc, m, pts, 0.05, 0.1, -0.05, 235, 0, cfg.eff(cfg.colPearl), false);
			}
		}

		// 6. Траектория того, что у тебя в руке (жемчуг / зелье)
		heldPrediction(vc, m, level, player, cfg, px, py, pz);

		// 7. Летящие жемчуга и зелья (свои и чужие)
		if (cfg.pearlEnemy || cfg.potionEnemy) {
			flyingPredictions(vc, m, level, cfg, pt);
		}
	}

	// ------------------------------------------------------------- предсказания

	private static int kindOf(ItemStack s) {
		if (s.is(Items.ENDER_PEARL)) {
			return 1;
		}
		if (s.is(Items.SPLASH_POTION)) {
			return 2;
		}
		if (s.is(Items.LINGERING_POTION)) {
			return 3;
		}
		return 0;
	}

	private static void heldPrediction(VertexConsumer vc, Matrix4f m, ClientLevel level, LocalPlayer player,
			PvpConfig cfg, double px, double py, double pz) {
		ItemStack held = player.getMainHandItem();
		int kind = kindOf(held);
		if (kind == 0) {
			held = player.getOffhandItem();
			kind = kindOf(held);
		}
		if (kind == 0) {
			return;
		}
		boolean pearl = kind == 1;
		if (pearl ? !cfg.pearlPrediction : !cfg.potionPredict) {
			return;
		}

		// Параметры броска: жемчуг — скорость 1.5, гравитация 0.03; зелье — 0.5, 0.05 и подъём на 20°.
		Vec3 vel = throwVelocity(player, pearl ? 1.5 : 0.5, pearl ? 0.0 : -20.0);
		Vec3 start = new Vec3(px, py + player.getEyeHeight() - 0.1, pz);
		Path path = simulate(level, player, start, vel, pearl ? 0.03 : 0.05);

		int ci = cfg.eff(pearl ? cfg.colPearl : cfg.colPotion);
		int line = pearl ? cfg.pearlLine : cfg.potionLine;
		drawPath(vc, m, path.pts, line, 0.03, 0.06, -0.03, 225, 90, ci);

		if (path.landing != null) {
			if (pearl) {
				marker(vc, m, cfg.pearlMarker, path.landing, ci, 1.0);
				Vec3 d = path.landing.subtract(player.position());
				Visuals.hudPearl = String.format(Locale.ROOT, "%.1f m", d.length());
			} else {
				marker(vc, m, 0, path.landing, ci, 1.0);
				if (cfg.potionArea) {
					areaRing(vc, m, path.landing, kind == 2 ? 4.0 : 3.0, ci, 1.0);
				}
			}
		}
		if (!pearl && cfg.potionLabel) {
			Visuals.hudPotion = held.getHoverName().getString();
		}
	}

	private static void flyingPredictions(VertexConsumer vc, Matrix4f m, ClientLevel level, PvpConfig cfg, float pt) {
		for (Entity e : level.entitiesForRendering()) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
			boolean pearl = id.equals("ender_pearl");
			boolean splash = id.equals("splash_potion") || id.equals("potion");
			boolean lingering = id.equals("lingering_potion");
			if (!(pearl && cfg.pearlEnemy) && !((splash || lingering) && cfg.potionEnemy)) {
				continue;
			}
			Vec3 vel = e.getDeltaMovement();
			if (vel.lengthSqr() < 1.0E-4) {
				continue;
			}
			Vec3 start = new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()) + 0.1,
					Mth.lerp(pt, e.zo, e.getZ()));
			Path path = simulate(level, e, start, vel, pearl ? 0.03 : 0.05);

			int ci = cfg.eff(pearl ? cfg.colPearl : cfg.colPotion);
			drawPath(vc, m, path.pts, 2, 0.03, 0.06, -0.03, 190, 50, ci);
			if (path.landing != null) {
				if (pearl) {
					marker(vc, m, cfg.pearlMarker, path.landing, ci, 0.85);
				} else {
					marker(vc, m, 0, path.landing, ci, 0.85);
					if (cfg.potionArea) {
						areaRing(vc, m, path.landing, lingering ? 3.0 : 4.0, ci, 0.85);
					}
				}
			}
		}
	}

	/** Начальная скорость броска (как Projectile.shootFromRotation) плюс скорость игрока. */
	private static Vec3 throwVelocity(LocalPlayer p, double speed, double pitchOffsetDeg) {
		double yaw = Math.toRadians(p.getYRot());
		double pitch = Math.toRadians(p.getXRot());
		double off = Math.toRadians(pitchOffsetDeg);
		Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch + off), Math.cos(yaw) * Math.cos(pitch))
				.normalize().scale(speed);
		Vec3 mv = p.getDeltaMovement();
		return dir.add(mv.x, p.onGround() ? 0.0 : mv.y, mv.z);
	}

	private static Path simulate(ClientLevel level, Entity owner, Vec3 start, Vec3 velocity, double gravity) {
		Path path = new Path();
		Vec3 pos = start;
		Vec3 vel = velocity;
		path.pts.add(pos);
		for (int step = 0; step < 160; step++) {
			Vec3 next = pos.add(vel);
			var hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			if (hit.getType() != HitResult.Type.MISS) {
				path.landing = hit.getLocation();
				path.pts.add(path.landing);
				break;
			}
			path.pts.add(next);
			pos = next;
			vel = vel.scale(0.99).add(0.0, -gravity, 0.0);
			if (pos.y < level.getMinY() - 16) {
				break;
			}
		}
		return path;
	}

	// ----------------------------------------------------------------- эффекты

	private static void renderRing(VertexConsumer vc, Matrix4f m, Visuals.Ring ring, float pt, int ci) {
		double t = Math.min(1.0, (ring.age + pt) / ring.life);
		double ease = 1.0 - Math.pow(1.0 - t, 3.0);
		double fade = 1.0 - t;
		double r = 0.3 + (ring.maxRadius - 0.3) * ease;
		double thick = 0.06 + 0.22 * fade;

		annulus(vc, m, ring.x, ring.y, ring.z, Math.max(0.0, r - thick), r, 0, 235 * fade, ci, 0f);
		annulus(vc, m, ring.x, ring.y + 0.005, ring.z, r - 0.02, r + 0.03, 255 * fade, 255 * fade, ci, 0.15f);
	}

	private static void chinaHat(VertexConsumer vc, Matrix4f m, double x, double headTop, double z, int ci, double r) {
		double baseY = headTop + 0.02;
		double apexY = baseY + 0.58 * r;
		int seg = 40;
		int apexColor = argb(Theme.grad(ci, 0.5f), 225);

		for (int k = 0; k < seg; k++) {
			double a0 = Math.PI * 2 * k / seg;
			double a1 = Math.PI * 2 * (k + 1) / seg;
			int c0 = argb(Theme.grad(ci, k / (float) seg), 125);
			int c1 = argb(Theme.grad(ci, (k + 1) / (float) seg), 125);
			quad(vc, m,
					x, apexY, z, apexColor,
					x, apexY, z, apexColor,
					x + Math.cos(a1) * r, baseY, z + Math.sin(a1) * r, c1,
					x + Math.cos(a0) * r, baseY, z + Math.sin(a0) * r, c0);
		}
		annulus(vc, m, x, baseY, z, r - 0.05, r + 0.05, 235, 235, ci, 0f);
	}

	private static void targetRing(VertexConsumer vc, Matrix4f m, float pt, int ci) {
		Entity e = Visuals.target;
		if (e == null || e.isRemoved() || Visuals.targetTicks <= 0) {
			return;
		}
		double fade = Math.min(1.0, Visuals.targetTicks / 10.0);
		double ex = Mth.lerp(pt, e.xo, e.getX());
		double ey = Mth.lerp(pt, e.yo, e.getY());
		double ez = Mth.lerp(pt, e.zo, e.getZ());
		double r = Math.max(0.45, e.getBbWidth() * 0.8 + 0.2);
		double h = Math.max(0.6, e.getBbHeight());

		double phase = (System.currentTimeMillis() % 1800L) / 1800.0;
		double s = 0.5 - 0.5 * Math.cos(phase * Math.PI * 2);
		double sign = Math.sin(phase * Math.PI * 2) > 0 ? 1.0 : -1.0;
		double y = ey + 0.05 + s * (h - 0.1);

		for (int i = 0; i < 8; i++) {
			double a = 215 * fade * (1.0 - i / 8.0);
			annulus(vc, m, ex, y - sign * i * 0.05, ez, r - 0.07, r, a, a, ci, i * 0.02f);
		}
		annulus(vc, m, ex, ey + 0.03, ez, r - 0.04, r, 110 * fade, 110 * fade, ci, 0.5f);
	}

	// ------------------------------------------------------------------ метки

	/** Метка приземления: 0 кольцо, 1 крест, 2 луч, 3 кольцо + луч. k — общая прозрачность 0..1. */
	private static void marker(VertexConsumer vc, Matrix4f m, int style, Vec3 p, int ci, double k) {
		double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 180.0);
		double y = p.y + 0.03;
		if (style == 0 || style == 3) {
			annulus(vc, m, p.x, y, p.z, 0.0, 0.3, 150 * k, 0, ci, 0f);
			annulus(vc, m, p.x, y + 0.004, p.z, 0.26 + 0.05 * pulse, 0.34 + 0.05 * pulse, 235 * k, 235 * k, ci, 0f);
		}
		if (style == 1) {
			double spin = System.currentTimeMillis() / 1500.0;
			bar(vc, m, p.x, y, p.z, spin, 0.7, 0.09, ci, 235 * k);
			bar(vc, m, p.x, y, p.z, spin + Math.PI / 2, 0.7, 0.09, ci, 235 * k);
			annulus(vc, m, p.x, y, p.z, 0.0, 0.25, 90 * k, 0, ci, 0f);
		}
		if (style == 2 || style == 3) {
			beam(vc, m, p.x, p.y + 0.02, p.z, 0.1, 2.6, ci, 210 * k, 0);
		}
	}

	/** Круг зоны действия зелья (радиус в блоках): заливка + градиентный край + тонкий ободок. */
	private static void areaRing(VertexConsumer vc, Matrix4f m, Vec3 p, double radius, int ci, double k) {
		double y = p.y + 0.02;
		annulus(vc, m, p.x, y, p.z, 0.0, radius, 38 * k, 38 * k, ci, 0f);
		annulus(vc, m, p.x, y + 0.004, p.z, radius - 0.35, radius, 0, 150 * k, ci, 0.25f);
		annulus(vc, m, p.x, y + 0.008, p.z, radius - 0.03, radius + 0.03, 235 * k, 235 * k, ci, 0.25f);
	}

	// -------------------------------------------------------------- примитивы

	/** Плоское кольцо (XZ). a0 — прозрачность у внутреннего края, a1 — у внешнего. */
	private static void annulus(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
			double r0, double r1, double a0, double a1, int ci, float phase) {
		for (int k = 0; k < SEG; k++) {
			double ang0 = Math.PI * 2 * k / SEG;
			double ang1 = Math.PI * 2 * (k + 1) / SEG;
			int rgb0 = Theme.grad(ci, k / (float) SEG + phase);
			int rgb1 = Theme.grad(ci, (k + 1) / (float) SEG + phase);
			double c0 = Math.cos(ang0);
			double s0 = Math.sin(ang0);
			double c1 = Math.cos(ang1);
			double s1 = Math.sin(ang1);
			quad(vc, m,
					cx + c0 * r0, cy, cz + s0 * r0, argb(rgb0, a0),
					cx + c0 * r1, cy, cz + s0 * r1, argb(rgb0, a1),
					cx + c1 * r1, cy, cz + s1 * r1, argb(rgb1, a1),
					cx + c1 * r0, cy, cz + s1 * r0, argb(rgb1, a0));
		}
	}

	/** Вертикальный полупрозрачный столб (многогранная призма). a0 — снизу, a1 — сверху. */
	private static void beam(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
			double radius, double height, int ci, double a0, double a1) {
		int n = 12;
		for (int k = 0; k < n; k++) {
			double ang0 = Math.PI * 2 * k / n;
			double ang1 = Math.PI * 2 * (k + 1) / n;
			int rgb = Theme.grad(ci, k / (float) n);
			quad(vc, m,
					cx + Math.cos(ang0) * radius, cy, cz + Math.sin(ang0) * radius, argb(rgb, a0),
					cx + Math.cos(ang1) * radius, cy, cz + Math.sin(ang1) * radius, argb(rgb, a0),
					cx + Math.cos(ang1) * radius, cy + height, cz + Math.sin(ang1) * radius, argb(rgb, a1),
					cx + Math.cos(ang0) * radius, cy + height, cz + Math.sin(ang0) * radius, argb(rgb, a1));
		}
	}

	/** Плоская полоса через точку (центр), повёрнутая на угол ang. */
	private static void bar(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz,
			double ang, double len, double width, int ci, double alpha) {
		double dx = Math.cos(ang) * len / 2;
		double dz = Math.sin(ang) * len / 2;
		double nx = -Math.sin(ang) * width / 2;
		double nz = Math.cos(ang) * width / 2;
		int c0 = argb(Theme.grad(ci, 0f), alpha);
		int c1 = argb(Theme.grad(ci, 0.5f), alpha);
		quad(vc, m,
				cx - dx + nx, cy, cz - dz + nz, c0,
				cx - dx - nx, cy, cz - dz - nz, c0,
				cx + dx - nx, cy, cz + dz - nz, c1,
				cx + dx + nx, cy, cz + dz + nz, c1);
	}

	/** Маленький октаэдр-«точка» (три пересекающихся квада). */
	private static void dot(VertexConsumer vc, Matrix4f m, double x, double y, double z, double s, int argb) {
		quad(vc, m, x - s, y, z, argb, x + s, y, z, argb, x + s, y + s * 2, z, argb, x - s, y + s * 2, z, argb);
		quad(vc, m, x, y, z - s, argb, x, y, z + s, argb, x, y + s * 2, z + s, argb, x, y + s * 2, z - s, argb);
		quad(vc, m, x - s, y + s, z - s, argb, x + s, y + s, z - s, argb, x + s, y + s, z + s, argb, x - s, y + s, z + s, argb);
	}

	/** Путь по точкам в выбранном стиле: 0 — лента, 1 — точки, 2 — пунктир. */
	private static void drawPath(VertexConsumer vc, Matrix4f m, List<Vec3> pts, int style, double halfW, double height,
			double yOff, double aStart, double aEnd, int ci) {
		if (style == 1) {
			int n = pts.size();
			for (int i = 0; i < n; i += 2) {
				float u = i / (float) Math.max(1, n - 1);
				Vec3 p = pts.get(i);
				dot(vc, m, p.x, p.y + yOff, p.z, 0.05, argb(Theme.grad(ci, u * 1.5f), Mth.lerp(u, aStart, aEnd)));
			}
		} else {
			ribbon(vc, m, pts, halfW, height, yOff, aStart, aEnd, ci, style == 2);
		}
	}

	/**
	 * Лента вдоль точек (первая — «голова»): вертикальная + горизонтальная полосы, видна с любого ракурса.
	 * Прозрачность меняется от aStart к aEnd, цвет — градиент. dashed — рисовать через один сегмент.
	 */
	private static void ribbon(VertexConsumer vc, Matrix4f m, List<Vec3> pts, double halfW, double height,
			double yOff, double aStart, double aEnd, int ci, boolean dashed) {
		int n = pts.size();
		if (n < 2) {
			return;
		}
		for (int i = 0; i < n - 1; i++) {
			if (dashed && (i / 2) % 2 == 1) {
				continue;
			}
			Vec3 p0 = pts.get(i);
			Vec3 p1 = pts.get(i + 1);
			float u0 = i / (float) (n - 1);
			float u1 = (i + 1) / (float) (n - 1);
			int c0 = argb(Theme.grad(ci, u0 * 1.5f), Mth.lerp(u0, aStart, aEnd));
			int c1 = argb(Theme.grad(ci, u1 * 1.5f), Mth.lerp(u1, aStart, aEnd));

			double y0 = p0.y + yOff;
			double y1 = p1.y + yOff;

			quad(vc, m,
					p0.x, y0, p0.z, c0,
					p1.x, y1, p1.z, c1,
					p1.x, y1 + height, p1.z, c1,
					p0.x, y0 + height, p0.z, c0);

			double dx = p1.x - p0.x;
			double dz = p1.z - p0.z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len > 1.0E-4) {
				double nx = -dz / len * halfW;
				double nz = dx / len * halfW;
				double yy0 = y0 + height * 0.5;
				double yy1 = y1 + height * 0.5;
				quad(vc, m,
						p0.x + nx, yy0, p0.z + nz, c0,
						p0.x - nx, yy0, p0.z - nz, c0,
						p1.x - nx, yy1, p1.z - nz, c1,
						p1.x + nx, yy1, p1.z + nz, c1);
			}
		}
	}

	/** Квад, нарисованный с обеих сторон (не зависит от отсечения задних граней). */
	private static void quad(VertexConsumer vc, Matrix4f m,
			double x1, double y1, double z1, int c1,
			double x2, double y2, double z2, int c2,
			double x3, double y3, double z3, int c3,
			double x4, double y4, double z4, int c4) {
		vtx(vc, m, x1, y1, z1, c1);
		vtx(vc, m, x2, y2, z2, c2);
		vtx(vc, m, x3, y3, z3, c3);
		vtx(vc, m, x4, y4, z4, c4);
		vtx(vc, m, x4, y4, z4, c4);
		vtx(vc, m, x3, y3, z3, c3);
		vtx(vc, m, x2, y2, z2, c2);
		vtx(vc, m, x1, y1, z1, c1);
	}

	private static void vtx(VertexConsumer vc, Matrix4f m, double x, double y, double z, int argb) {
		vc.addVertex(m, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).setColor(argb);
	}

	private static int argb(int rgb, double alpha) {
		int a = (int) Math.max(0.0, Math.min(255.0, alpha));
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}
