package dev.pvpvisual;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

/** Изменение положения/размера рук и предметов от первого лица (вызывается из миксина). */
public final class HandFx {
	private static PoseStack active;

	private HandFx() {
	}

	public static void begin(PoseStack stack) {
		active = null;
		PvpConfig c = PvpConfig.get();
		if (!c.handsEnabled) {
			return;
		}
		stack.pushPose();
		stack.translate((double) c.handX, (double) c.handY, (double) c.handZ);
		stack.scale(c.handScale, c.handScale, c.handScale);
		if (c.handRoll != 0f) {
			stack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(c.handRoll)));
		}
		active = stack;
	}

	public static void end() {
		if (active != null) {
			active.popPose();
			active = null;
		}
	}
}
