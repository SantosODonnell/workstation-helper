package dev.santos.workstationhelper.scene;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.level.block.RenderShape;

/** Рисует {@link BlockScene}: каждый блок — как «блок вне мира» (так игра рисует падающий песок), рамки — линиями. */
public class BlockSceneRenderer extends PictureInPictureRenderer<BlockScene> {
	/** Заливка чуть больше блока, чтобы не мерцать с его гранями. Прозрачность ~45%. */
	private static final float LO = -0.01f, HI = 1.01f;
	private static final int ALPHA = 0x70;

	@Override
	public Class<BlockScene> getRenderStateClass() {
		return BlockScene.class;
	}

	/** Центр сцены — посередине окошка (по умолчанию игра ставит его у нижнего края). */
	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "workstationhelper block scene";
	}

	@Override
	protected void renderToTexture(BlockScene scene, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) return;
		mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
		pose.pushPose();
		// в окне ось Y смотрит вниз — переворачиваем, потом наклон и поворот сцены
		pose.rotate(Axis.ZP.rotationDegrees(180));
		pose.rotate(Axis.XP.rotationDegrees(scene.pitch()));
		pose.rotate(Axis.YP.rotationDegrees(scene.yaw()));
		for (BlockScene.Block b : scene.blocks()) {
			pose.pushPose();
			pose.translate(b.dx() - 0.5f, b.dy() - 0.5f, b.dz() - 0.5f);
			if (b.state().getRenderShape() == RenderShape.MODEL) {
				MovingBlockRenderState mb = new MovingBlockRenderState();
				mb.randomSeedPos = b.worldPos();
				mb.blockPos = b.worldPos();
				mb.blockState = b.state();
				mb.biome = level.getBiome(b.worldPos());
				mb.cardinalLighting = level.cardinalLighting();
				mb.lightEngine = level.getLightEngine();
				collector.submitMovingBlock(pose, mb, 0);
			}
			// отмеченный блок — полупрозрачная заливка поверх текстуры: видно с любой стороны, в отличие от линий
			if (b.frameColor() != 0) collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> tintBox(p, vc, b.frameColor()));
			pose.popPose();
		}
		pose.popPose();
	}

	/** Шесть граней куба цветом color с прозрачностью ALPHA. */
	private static void tintBox(com.mojang.blaze3d.vertex.PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer vc, int color) {
		int c = (ALPHA << 24) | (color & 0xFFFFFF);
		float[][] faces = {
			{LO, LO, LO, HI, LO, LO, HI, LO, HI, LO, LO, HI}, // низ
			{LO, HI, LO, LO, HI, HI, HI, HI, HI, HI, HI, LO}, // верх
			{LO, LO, LO, LO, HI, LO, HI, HI, LO, HI, LO, LO}, // север
			{LO, LO, HI, HI, LO, HI, HI, HI, HI, LO, HI, HI}, // юг
			{LO, LO, LO, LO, LO, HI, LO, HI, HI, LO, HI, LO}, // запад
			{HI, LO, LO, HI, HI, LO, HI, HI, HI, HI, LO, HI}, // восток
		};
		for (float[] f : faces) {
			for (int i = 0; i < 12; i += 3) vc.addVertex(p, f[i], f[i + 1], f[i + 2]).setColor(c);
		}
	}
}
