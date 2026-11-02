package dev.santos.workstationhelper.mixin;

import dev.santos.workstationhelper.Config;
import java.util.Arrays;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Добавляет в «Настройки → Специальные возможности» переключатель «Помощник: Вкл/Выкл» — включает и выключает весь мод. */
@Mixin(AccessibilityOptionsScreen.class)
public abstract class AccessibilityOptionsMixin {
	@Inject(method = "options", at = @At("RETURN"), cancellable = true)
	private static void workstationhelper$addToggle(Options options, CallbackInfoReturnable<OptionInstance<?>[]> cir) {
		OptionInstance<Boolean> helper = OptionInstance.createBoolean("workstationhelper.option.helper", Config.get().helper, v -> {
			Config.get().helper = v;
			Config.save();
		});
		OptionInstance<?>[] list = cir.getReturnValue();
		OptionInstance<?>[] out = Arrays.copyOf(list, list.length + 1);
		out[list.length] = helper;
		cir.setReturnValue(out);
	}
}
