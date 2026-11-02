package dev.santos.workstationhelper.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к состоянию отрисовки окна — чтобы добавить свою 3D-сцену (как игра добавляет манекен и книгу). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
	@Accessor("guiRenderState")
	GuiRenderState workstationhelper$guiRenderState();
}
