package com.arcanum.menu;

import com.arcanum.registry.ModBlocks;
import com.arcanum.registry.ModMenus;
import com.arcanum.spell.SpellGating;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Büyü Masası container menüsü — kitap + 6 reagent slotlu gerçek bir GUI.
 * Oyuncu bir büyü kitabını (starter/dark) 0. slota, farklı reagent'ları 1-6.
 * slotlara koyar; hangi büyüyü öğrenmek istediğini {@link com.arcanum.fabric.client.SpellTableScreen}
 * içindeki dinamik listeden seçer. Öğrenirken o büyünün gerektirdiği reagent, 6
 * slotun HANGİSİNDE varsa oradan çekilir (tek tek malzeme değiştirmek gerekmez).
 * TÜM doğrulama sunucu tarafında {@link SpellGating#attemptLearn} içinde yapılır —
 * bu sınıf yalnızca slot/envanter iskeletini sağlar.
 */
public class SpellTableMenu extends AbstractContainerMenu {
    /** Kitap slotu — index 0. */
    private static final int BOOK_SLOT = 0;
    /** Reagent slot sayısı (index 1..6). */
    private static final int REAGENT_SLOTS = 6;
    /** Özel slot sayısı (kitap + 6 reagent). */
    private static final int SLOT_COUNT = 1 + REAGENT_SLOTS;

    private final Container bookContainer = new SimpleContainer(1) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    private final Container reagentContainer = new SimpleContainer(REAGENT_SLOTS);
    private final ContainerLevelAccess access;

    /** İstemci tarafı constructor — vanilla dual-constructor deseni. */
    public SpellTableMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, ContainerLevelAccess.NULL);
    }

    /** Sunucu tarafı constructor — gerçek level access ile. */
    public SpellTableMenu(int containerId, Inventory playerInv, ContainerLevelAccess access) {
        super(ModMenus.SPELL_TABLE_MENU.get(), containerId);
        this.access = access;

        // Kitap slotu (x=26,y=18) — yalnızca büyü kitabı kabul eder, tek adet.
        this.addSlot(new Slot(bookContainer, 0, 26, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SpellGating.isAnySpellBook(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Reagent slotları (y=40, x=26'dan 18'er artarak 6 slot) — yalnızca kabul
        // edilen malzemeler; farklı reagent'lar bir arada durabilir.
        for (int i = 0; i < REAGENT_SLOTS; i++) {
            this.addSlot(new Slot(reagentContainer, i, 26 + i * 18, 40) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SpellGating.isAnyReagent(stack);
                }
            });
        }

        // Oyuncu envanteri: 3x9 grid.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar.
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.SPELL_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();

            if (index < SLOT_COUNT) {
                // kitap/reagent slotundan envanter+hotbar'a
                if (!this.moveItemStackTo(stack, SLOT_COUNT, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // envanter/hotbar'dan önce kitap/reagent slotuna, sığmazsa envanter<->hotbar swap
                if (!this.moveItemStackTo(stack, 0, SLOT_COUNT, false)) {
                    int invStart = SLOT_COUNT;
                    int invEnd = SLOT_COUNT + 27;
                    int hotbarEnd = invEnd + 9;
                    if (index < invEnd) {
                        if (!this.moveItemStackTo(stack, invEnd, hotbarEnd, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!this.moveItemStackTo(stack, invStart, invEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return copy;
    }

    /** GUI'nin kitap slotundaki GERÇEK stack referansı (SpellGating.attemptLearn bunu shrink edebilir). */
    public ItemStack getBookStack() {
        return this.slots.get(BOOK_SLOT).getItem();
    }

    /** 6 reagent slotunu içeren GERÇEK container — attemptLearn gereken reagent'ı
     *  bunun içinde arar ve bulduğu slottan 1 adet tüketir. */
    public Container getReagentContainer() {
        return this.reagentContainer;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.clearContainer(player, bookContainer);
        this.clearContainer(player, reagentContainer);
    }
}
