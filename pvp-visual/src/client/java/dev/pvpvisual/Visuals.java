package dev.pvpvisual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** Состояние и логика эффектов. Сами 3D-эффекты рисует {@link WorldFx}. */
public final class Visuals {
	/** Расширяющееся кольцо на земле (прыжок / удар). */
	public static final class Ring {
		public final double x;
		public final double y;
		public final double z;
		public final double maxRadius;
		public final int life;
		public int age;

		Ring(double x, double y, double z, double maxRadius, int life) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.maxRadius = maxRadius;
			this.life = life;
		}
	}

	/** Последняя цель, по которой ты бил (для Target HUD и кольца на цели). */
	public static Entity target;
	public static int targetTicks;
	public static float displayHealth;
	/** Вспышка кольца-прицела после удара (в тиках). */
	public static int hitFlash;

	/** Подписи под прицелом (обновляются каждый кадр в WorldFx). */
	public static String hudPearl;
	public static String hudPotion;

	public static final List<Ring> RINGS = new ArrayList<>();
	public static final Trail SELF_TRAIL = new Trail();
	public static final Map<Integer, Trail> PEARL_TRAILS = new HashMap<>();

	private static boolean wasOnGround = true;

	private Visuals() {
	}

	// ---------------------------------------------------------------- удар

	public static void onAttack(Minecraft mc, Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return;
		}
		PvpConfig cfg = PvpConfig.get();
		if (target != entity) {
			displayHealth = living.getHealth();
		}
		target = entity;
		targetTicks = 80;
		hitFlash = 6;

		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		if (cfg.hitParticles) {
			hitBurst(level, entity, cfg);
		}
		if (cfg.hitWave) {
			RINGS.add(new Ring(entity.getX(), entity.getY() + 0.03, entity.getZ(),
					Math.max(1.0, entity.getBbWidth() * 2.2), 10));
		}
		if (cfg.hitSound) {
			playHitSound(mc, cfg, level.getRandom());
		}
	}

	private static void hitBurst(ClientLevel level, Entity entity, PvpConfig cfg) {
		RandomSource random = level.getRandom();
		int count = PvpConfig.PARTICLE_COUNTS[cfg.particleCountIndex];
		double cx = entity.getX();
		double cy = entity.getY() + entity.getBbHeight() * 0.55;
		double cz = entity.getZ();

		for (int i = 0; i < count; i++) {
			double dx = random.nextGaussian();
			double dy = random.nextGaussian();
			double dz = random.nextGaussian();
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1.0E-4) {
				continue;
			}
			dx /= len;
			dy /= len;
			dz /= len;

			double spread = 0.15 + random.nextDouble() * 0.35;
			double speed = 0.12 + random.nextDouble() * 0.2;
			double px = cx + dx * spread;
			double py = cy + dy * spread;
			double pz = cz + dz * spread;

			switch (cfg.hitStyle) {
				case 1 -> level.addParticle(ParticleTypes.CRIT, px, py, pz, dx * speed, dy * speed, dz * speed);
				case 2 -> level.addParticle(ParticleTypes.END_ROD, px, py, pz, dx * speed, dy * speed, dz * speed);
				case 3 -> level.addParticle(ParticleTypes.ENCHANTED_HIT, px, py, pz, dx * speed, dy * speed, dz * speed);
				default -> level.addParticle(
						new DustParticleOptions(Theme.color(cfg.eff(cfg.colRings), (float) i / count), 0.9f + random.nextFloat() * 0.5f),
						px, py, pz, 0, 0, 0);
			}
		}
	}

	private static void playHitSound(Minecraft mc, PvpConfig cfg, RandomSource random) {
		SoundEvent sound = switch (cfg.soundIndex) {
			case 1 -> SoundEvents.PLAYER_ATTACK_CRIT;
			case 2 -> SoundEvents.PLAYER_LEVELUP;
			default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
		};
		float pitch = 0.85f + random.nextFloat() * 0.5f; // случайная высота тона
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6f));
	}

	// ---------------------------------------------------------------- тик

	public static void onTick(Minecraft mc) {
		if (targetTicks > 0) {
			targetTicks--;
		}
		if (hitFlash > 0) {
			hitFlash--;
		}

		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			RINGS.clear();
			hudPearl = null;
			hudPotion = null;
			SELF_TRAIL.pts.clear();
			PEARL_TRAILS.clear();
			target = null;
			wasOnGround = true;
			return;
		}

		PvpConfig cfg = PvpConfig.get();

		// Кольца: старение
		Iterator<Ring> it = RINGS.iterator();
		while (it.hasNext()) {
			Ring ring = it.next();
			if (++ring.age > ring.life) {
				it.remove();
			}
		}

		// Кольцо при прыжке
		boolean onGround = player.onGround();
		if (cfg.jumpCircles && wasOnGround && !onGround && player.getDeltaMovement().y > 0.1
				&& !player.getAbilities().flying) {
			RINGS.add(new Ring(player.getX(), player.getY() + 0.03, player.getZ(), 2.2 * cfg.ringScale, 14));
		}
		wasOnGround = onGround;

		// След за игроком
		if (cfg.trail) {
			double d = SELF_TRAIL.distSqToNewest(player.getX(), player.getY(), player.getZ());
			if (d > 36.0) {
				SELF_TRAIL.pts.clear(); // телепорт / респавн
			}
			if (d > 0.0009) {
				SELF_TRAIL.add(player.getX(), player.getY(), player.getZ());
			}
			SELF_TRAIL.tick(cfg.trailLength);
		} else {
			SELF_TRAIL.pts.clear();
		}

		// След за эндер-жемчугами
		if (cfg.pearlTrail) {
			Set<Integer> seen = new HashSet<>();
			for (Entity entity : level.entitiesForRendering()) {
				if (entity.getType() == EntityType.ENDER_PEARL) {
					seen.add(entity.getId());
					Trail trail = PEARL_TRAILS.computeIfAbsent(entity.getId(), k -> new Trail());
					trail.add(entity.getX(), entity.getY() + 0.125, entity.getZ());
				}
			}
			Iterator<Map.Entry<Integer, Trail>> ti = PEARL_TRAILS.entrySet().iterator();
			while (ti.hasNext()) {
				Map.Entry<Integer, Trail> e = ti.next();
				e.getValue().tick(30);
				if (!seen.contains(e.getKey()) && e.getValue().pts.isEmpty()) {
					ti.remove();
				}
			}
		} else {
			PEARL_TRAILS.clear();
		}
	}
}
