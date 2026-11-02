package dev.santos.workstationhelper.gui;

import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import dev.santos.workstationhelper.scene.BlockScene;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Панель у стола зачарования: 3D-сцена — стол и блоки вокруг. Полки, которые не работают, обведены красным;
 * наведи мышь на такую полку — увидишь причину. Сцену можно крутить мышкой.
 * Клик по книге на самом столе прячет и показывает панель.
 */
public final class EnchantingPanels {
	private static final int PAD = 7;
	/** для максимального зачарования (30 уровней) нужно 15 полок */
	private static final int MAX_SHELVES = 15;
	private static final int BAD = 0xFF8B0000;

	/** Полка, которая не работает: где она в сцене и почему. */
	private record Problem(float x, float y, float z, Component reason) {
	}

	/** Поворот сцены — помнится, пока запущена игра. */
	private static float yaw = 30;
	/** при первом открытии — сверху под 45° (у сцены ось Y перевёрнута, поэтому «сверху» — минус) */
	private static float pitch = -45;
	/** масштаб: 0 — самый дальний (100%), 1 — 125%, 2 — 150% */
	private static int zoom;
	private static final int ZOOM_MAX = 2;

	private final EnchantmentScreen screen;
	private final Minecraft mc;
	private final Font font;
	private boolean fits;
	private int lx, py, pw, ph;
	private BlockPos table;
	private int working;
	private final List<Problem> problems = new ArrayList<>();
	private final List<BlockScene.Block> sceneBlocks = new ArrayList<>();
	private boolean dragging;
	private int boxX, boxY, boxW, boxH;

	private EnchantingPanels(EnchantmentScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
	}

	public static void attach(EnchantmentScreen screen) {
		EnchantingPanels p = new EnchantingPanels(screen);
		p.layout();
		p.findTable();
		p.analyze();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		ScreenEvents.afterTick(screen).register(s -> p.analyze());
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		ScreenMouseEvents.allowMouseDrag(screen).register((s, e, dx, dy) -> !p.drag(dx, dy));
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !p.release(e.x(), e.y()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private boolean shown() {
		return fits && table != null && Config.get().enchantPanels;
	}

	private void layout() {
		int left = acc().workstationhelper$leftPos();
		pw = Math.min(150, left - 4);
		fits = pw >= 100;
		ph = acc().workstationhelper$imageHeight();
		py = acc().workstationhelper$topPos();
		lx = left - pw - 2;
	}

	/** Стол — блок, на который смотрел игрок, открывая окно; если не вышло — ближайший стол рядом. */
	private void findTable() {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) return;
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
			&& level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE)) {
			table = hit.getBlockPos();
			return;
		}
		BlockPos me = mc.player.blockPosition();
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(me.offset(-5, -3, -5), me.offset(5, 3, 5))) {
			if (level.getBlockState(p).is(Blocks.ENCHANTING_TABLE) && p.distSqr(me) < best) {
				best = p.distSqr(me);
				table = p.immutable();
			}
		}
	}

	// ======================== проверка полок ========================

	/**
	 * Проверяет все книжные полки вокруг стола по правилам игры: полка работает, если стоит в кольце на 2 блока от стола
	 * (на уровне стола или на блок выше) и между ней и столом воздух. Заодно собирает блоки для 3D-сцены.
	 */
	private void analyze() {
		problems.clear();
		sceneBlocks.clear();
		working = 0;
		ClientLevel level = mc.level;
		if (level == null || table == null) return;
		for (int dy = -1; dy <= 2; dy++) {
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos off = new BlockPos(dx, dy, dz);
					BlockPos pos = table.offset(off);
					BlockState st = level.getBlockState(pos);
					// сцена чуть опущена, чтобы стол и ряд полок над ним были по центру
					float sy = dy - 0.5f;
					int frame = 0;
					if (st.is(BlockTags.ENCHANTMENT_POWER_PROVIDER)) {
						Component reason = null;
						if (EnchantingTableBlock.BOOKSHELF_OFFSETS.contains(off)) {
							if (EnchantingTableBlock.isValidBookShelf(level, table, off)) working++;
							else {
								BlockState mid = level.getBlockState(table.offset(dx / 2, dy, dz / 2));
								reason = Component.translatable("workstationhelper.ench.blocked", mid.getBlock().getName());
							}
						} else if (dy > 1) {
							reason = Component.translatable("workstationhelper.ench.too_high");
						} else if (dy < 0) {
							reason = Component.translatable("workstationhelper.ench.too_low");
						} else if (Math.max(Math.abs(dx), Math.abs(dz)) > 2) {
							reason = Component.translatable("workstationhelper.ench.too_far");
						} else {
							reason = Component.translatable("workstationhelper.ench.too_close");
						}
						if (reason != null) {
							problems.add(new Problem(dx, sy, dz, reason));
							frame = BAD;
						}
					}
					// в сцене: стол, полки и всё на уровне стола и на блок выше (там и стоят помехи)
					boolean inScene = st.is(BlockTags.ENCHANTMENT_POWER_PROVIDER) || (dy >= 0 && dy <= 1 && !st.isAir());
					if (inScene) sceneBlocks.add(new BlockScene.Block(pos, dx, sy, dz, st, frame));
				}
			}
		}
	}

	// ======================== отрисовка ========================

	/** Книга на самом столе (примерно x 16–50, y 18–42) — клик по ней прячет панель; кликается на пиксель меньше книги. */
	/** Мини-кнопка «−/+» в правом верхнем углу окна стола. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private boolean onBook(double mx, double my) {
		int ty = Vanilla.toggleY(acc().workstationhelper$topPos());
		return mx >= toggleX() && mx < toggleX() + Vanilla.TOGGLE && my >= ty && my < ty + Vanilla.TOGGLE;
	}

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		if (fits && table != null) {
			boolean hover = onBook(mx, my);
			Vanilla.toggle(g, toggleX(), Vanilla.toggleY(acc().workstationhelper$topPos()), hover, Config.get().enchantPanels);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().enchantPanels ? "workstationhelper.ench.hide" : "workstationhelper.ench.show"), mx, my);
		}
		if (!shown()) return;
		Vanilla.panel(g, lx, py, pw, ph);
		g.text(font, Component.translatable("workstationhelper.ench.scene"), lx + PAD, py + 7, Vanilla.TEXT, false);
		int bx = lx + PAD, by = py + 18, bw = pw - 2 * PAD, bh = ph - 18 - PAD;
		Vanilla.inset(g, bx, by, bw, bh, Vanilla.SLOT);
		boxX = bx;
		boxY = by;
		boxW = bw;
		boxH = bh;
		// 7 блоков в ширину сцены должны поместиться по диагонали; масштаб +25% за шаг
		float scale = Math.min(bw, bh) / 10.5f * (1 + 0.25f * zoom);
		BlockScene.draw(g, List.copyOf(sceneBlocks), yaw, pitch, bx + 1, by + 1, bx + bw - 1, by + bh - 1, scale);

		// сколько полок работает — в углу сцены
		int col = working >= MAX_SHELVES ? 0xFF55FF55 : 0xFFFFFFFF;
		g.text(font, Component.translatable("workstationhelper.ench.count", Math.min(working, MAX_SHELVES), MAX_SHELVES), bx + 4, by + 4, col, true);
		// масштаб внизу — видно, что колёсико приближает
		g.text(font, Component.translatable("workstationhelper.ench.zoom", 100 + 25 * zoom), bx + 4, by + bh - 12, 0xFFE0E0E0, true);

		if (!dragging) hoverReason(g, mx, my, bx + bw / 2f, by + bh / 2f, scale);
	}

	/**
	 * Наведение на красную полку: та же раскладка, что у сцены (поворот вокруг вертикали, потом наклон, потом зеркало),
	 * переводим центр каждой полки в точку на экране. Если под мышью несколько — берём ближнюю к зрителю.
	 */
	private void hoverReason(GuiGraphicsExtractor g, int mx, int my, float cx, float cy, float scale) {
		double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
		Problem best = null;
		double bestDepth = -Double.MAX_VALUE;
		for (Problem pr : problems) {
			double x1 = pr.x() * Math.cos(a) + pr.z() * Math.sin(a);
			double z1 = -pr.x() * Math.sin(a) + pr.z() * Math.cos(a);
			double y2 = pr.y() * Math.cos(p) - z1 * Math.sin(p);
			double z2 = pr.y() * Math.sin(p) + z1 * Math.cos(p);
			double sx = cx - x1 * scale, sy = cy - y2 * scale;
			if (Math.abs(mx - sx) <= scale * 0.55 && Math.abs(my - sy) <= scale * 0.55 && z2 > bestDepth) {
				bestDepth = z2;
				best = pr;
			}
		}
		if (best != null) g.setTooltipForNextFrame(font, best.reason(), mx, my);
	}

	// ======================== мышь ========================

	private boolean click(double mx, double my) {
		if (fits && table != null && onBook(mx, my)) {
			Config.get().enchantPanels = !Config.get().enchantPanels;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		if (!shown()) return false;
		if (mx >= boxX && my >= boxY && mx < boxX + boxW && my < boxY + boxH) {
			dragging = true;
			return true;
		}
		return overPanel(mx, my);
	}

	private boolean overPanel(double mx, double my) {
		return mx >= lx && my >= py && mx < lx + pw && my < py + ph;
	}

	/** Тянешь мышкой — сцена поворачивается: влево-вправо вокруг стола, вверх-вниз — наклон. */
	private boolean drag(double dx, double dy) {
		if (!dragging) return false;
		yaw = (yaw + (float) dx * 2.5f) % 360f;
		pitch = Math.clamp(pitch + (float) dy * 1.5f, -89f, 89f);
		return true;
	}

	/** Колёсико над сценой — масштаб: 100%, 125%, 150%. */
	private boolean scroll(double mx, double my, double v) {
		if (!shown() || !(mx >= boxX && my >= boxY && mx < boxX + boxW && my < boxY + boxH)) return false;
		zoom = Math.clamp(zoom + (int) Math.signum(v), 0, ZOOM_MAX);
		return true;
	}

	private boolean release(double mx, double my) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return shown() && overPanel(mx, my);
	}
}
