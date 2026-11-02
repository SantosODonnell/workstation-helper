package dev.santos.workstationhelper.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Рисование в стиле окон Minecraft: серая панель как у инвентаря, вдавленные ячейки, тёмный текст. */
public final class Vanilla {
	public static final int BODY = 0xFFC6C6C6;
	public static final int LIGHT = 0xFFFFFFFF;
	public static final int SHADOW = 0xFF555555;
	public static final int BLACK = 0xFF000000;
	public static final int SLOT = 0xFF8B8B8B;
	public static final int SLOT_DARK = 0xFF373737;
	/** цвет подписей в окнах игры */
	public static final int TEXT = 0xFF404040;

	private Vanilla() {
	}

	/** Панель как фон инвентаря: чёрная рамка со срезанными углами, светлый верх-лево, тень низ-право. */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 2, y, x + w - 2, y + 1, BLACK);
		g.fill(x + 2, y + h - 1, x + w - 2, y + h, BLACK);
		g.fill(x, y + 2, x + 1, y + h - 2, BLACK);
		g.fill(x + w - 1, y + 2, x + w, y + h - 2, BLACK);
		g.fill(x + 1, y + 1, x + 2, y + 2, BLACK);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, BLACK);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, BLACK);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, BLACK);
		g.fill(x + 1, y + 2, x + w - 1, y + h - 2, BODY);
		g.fill(x + 2, y + 1, x + w - 2, y + h - 1, BODY);
		g.fill(x + 2, y + 1, x + w - 3, y + 3, LIGHT);
		g.fill(x + 1, y + 2, x + 3, y + h - 3, LIGHT);
		g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, SHADOW);
		g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, SHADOW);
	}

	/** Вдавленная область (как ячейка инвентаря): тёмный верх-лево, светлый низ-право. */
	public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
		g.fill(x, y, x + w, y + h, LIGHT);
		g.fill(x, y, x + w - 1, y + h - 1, SLOT_DARK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
	}

	/** Звук нажатия, как у кнопок и вкладок игры. */
	public static void clickSound() {
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
			net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	/** Размер мини-кнопки скрытия панелей: рамка и значок 8×8. */
	public static final int TOGGLE = 10;

	/** Мини-кнопка скрытия в правом верхнем углу окна стола (у наковальни — в левом, там своё место). */
	public static int toggleRightX(int left, int imageWidth) {
		return left + imageWidth - 3 - TOGGLE;
	}

	public static int toggleY(int top) {
		return top + 3;
	}

	/**
	 * Мини-кнопка скрытия панелей: серая кнопка игры и белый значок 8×8 толщиной 2 пикселя —
	 * «−», когда панели видны, «+», когда спрятаны.
	 */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean hover, boolean shown) {
		// кнопка как у игры, но обводка тёмно-серая (как контур карты на столе картографа), а не чёрная; при наведении — белая
		g.fill(x, y, x + TOGGLE, y + TOGGLE, hover ? 0xFFFFFFFF : 0xFF373737);
		g.fill(x + 1, y + 1, x + TOGGLE - 1, y + TOGGLE - 1, 0xFF6F6F6F);
		g.fill(x + 1, y + 1, x + TOGGLE - 1, y + 2, 0xFFAAAAAA);
		g.fill(x + 1, y + 1, x + 2, y + TOGGLE - 1, 0xFFAAAAAA);
		g.fill(x + 1, y + TOGGLE - 2, x + TOGGLE - 1, y + TOGGLE - 1, 0xFF565656);
		g.fill(x + TOGGLE - 2, y + 2, x + TOGGLE - 1, y + TOGGLE - 1, 0xFF565656);
		int ix = x + 1, iy = y + 1, c = 0xFFF5F5F5;
		g.fill(ix + 1, iy + 3, ix + 7, iy + 5, c);
		if (!shown) g.fill(ix + 3, iy + 1, ix + 5, iy + 7, c);
	}
}
