package dev.pvpvisual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ItemInHandRenderer;

import dev.pvpvisual.HandFx;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	/** В начале отрисовки каждой руки применяем смещение/масштаб/наклон к PoseStack. */
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
	private PoseStack pvpvisual$begin(PoseStack stack) {
		HandFx.begin(stack);
		return stack;
	}

	/** В конце — возвращаем PoseStack в исходное состояние. */
	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void pvpvisual$end(CallbackInfo ci) {
		HandFx.end();
	}
}
