package dev.santos.workstationhelper.logic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Предмет или книга в плане: какие на нём зачарования и какой штраф за прошлые наковальни.
 * Может быть настоящей вещью из инвентаря (slot ≥ 0) или воображаемой книгой.
 */
public final class Piece {
	public final Map<Holder<Enchantment>, Integer> ench;
	/** штраф наковальни (компонент repair_cost): 0, 1, 3, 7, 15… */
	public final int penalty;
	public final boolean book;
	/** вещь для иконки и поиска в инвентаре */
	public final ItemStack stack;
	/** номер слота в меню наковальни, откуда взята вещь; -1 — воображаемая или промежуточная */
	public final int slot;

	public Piece(Map<Holder<Enchantment>, Integer> ench, int penalty, boolean book, ItemStack stack, int slot) {
		this.ench = Collections.unmodifiableMap(new LinkedHashMap<>(ench));
		this.penalty = penalty;
		this.book = book;
		this.stack = stack;
		this.slot = slot;
	}

	/** Настоящая вещь из слота. */
	public static Piece of(ItemStack stack, int slot) {
		Map<Holder<Enchantment>, Integer> map = new LinkedHashMap<>();
		for (var e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) map.put(e.getKey(), e.getIntValue());
		// копия: сама вещь может уехать в наковальню, а иконка в плане должна остаться
		return new Piece(map, stack.getOrDefault(DataComponents.REPAIR_COST, 0), stack.is(Items.ENCHANTED_BOOK), stack.copy(), slot);
	}

	/** Воображаемая новая книга с одним зачарованием. */
	public static Piece book(Holder<Enchantment> e, int level) {
		return new Piece(Map.of(e, level), 0, true, bookStack(Map.of(e, level), 0), -1);
	}

	/** Иконка зачарованной книги с такими зачарованиями. */
	public static ItemStack bookStack(Map<Holder<Enchantment>, Integer> ench, int penalty) {
		ItemStack s = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		ench.forEach(m::set);
		s.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
		if (penalty > 0) s.set(DataComponents.REPAIR_COST, penalty);
		return s;
	}

	/** Сколько раз вещь уже побывала на наковальне (штраф = 2^n − 1). */
	public static int uses(int penalty) {
		return 32 - Integer.numberOfLeadingZeros(Math.max(0, penalty));
	}

	/**
	 * Та же ли это вещь: тот же предмет и те же зачарования. Штраф наковальни не сравниваем —
	 * готовую книгу, найденную или купленную, тоже узнаём, даже если её штраф другой.
	 */
	public boolean matches(ItemStack other) {
		if (other.isEmpty() || !other.is(stack.getItem())) return false;
		return of(other, -1).ench.equals(ench);
	}
}
