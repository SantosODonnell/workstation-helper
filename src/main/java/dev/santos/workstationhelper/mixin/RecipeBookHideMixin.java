package dev.santos.workstationhelper.mixin;

import dev.santos.workstationhelper.gui.FuelTab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Пока открыта вкладка «Топливо», книга рецептов игры лежит под нашим списком: она не должна «видеть» мышку
 * (подсветка рецептов) и показывать свои подсказки поверх наших.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookHideMixin {
	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int workstationhelper$hideMouseX(int mouseX) {
		return FuelTab.covers(this) ? -10000 : mouseX;
	}

	@ModifyVariable(method = "extractRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int workstationhelper$hideMouseY(int mouseY) {
		return FuelTab.covers(this) ? -10000 : mouseY;
	}

	@Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
	private void workstationhelper$noTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, Slot slot, CallbackInfo ci) {
		if (FuelTab.covers(this)) ci.cancel();
	}
}
