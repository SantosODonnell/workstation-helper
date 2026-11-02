package dev.santos.workstationhelper.scene;

import dev.santos.workstationhelper.mixin.GuiGraphicsAccessor;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Маленькая 3D-сцена из блоков прямо в окне (как манекен у кузнечного стола). Блоки берутся из мира,
 * сцену можно поворачивать; отмеченные блоки обводятся рамкой. Годится и для других «мини-проекций».
 *
 * @param blocks блоки сцены: позиция в мире, сдвиг от центра сцены, состояние, цвет рамки (0 — без рамки)
 * @param yaw поворот вокруг вертикали, градусы
 * @param pitch наклон к зрителю, градусы
 */
public record BlockScene(List<Block> blocks, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale,
	ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {

	public record Block(BlockPos worldPos, float dx, float dy, float dz, BlockState state, int frameColor) {
	}

	/** Добавляет сцену в окно в прямоугольнике x0..x1, y0..y1; scale — сколько пикселей на один блок. */
	public static void draw(GuiGraphicsExtractor g, List<Block> blocks, float yaw, float pitch, int x0, int y0, int x1, int y1, float scale) {
		ScreenRectangle bounds = PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null);
		((GuiGraphicsAccessor) g).workstationhelper$guiRenderState()
			.addPicturesInPictureState(new BlockScene(blocks, yaw, pitch, x0, y0, x1, y1, scale, null, bounds));
	}
}
