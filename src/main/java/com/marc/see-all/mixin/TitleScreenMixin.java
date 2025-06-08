package com.marc.seeall.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    /**
     * Injects before the rendering of the title screen buttons to remove the singleplayer button.
     * This method targets the 'initWidgets' method (or equivalent in newer MC versions)
     * which is responsible for adding the default buttons to the title screen.
     * The @At(value = "TAIL") ensures this code runs after all original buttons have been added.
     * Then, we iterate through the list of children (which includes buttons) and remove the one
     * associated with the singleplayer world select screen.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void seeall$removeSingleplayerButton(CallbackInfo ci) {
        // Iterate through the list of drawable elements (buttons, widgets, etc.)
        // This is a common way to modify screen elements added by the base game.
        this.children().removeIf(widget -> {
            // Check if the widget is a ButtonWidget
            if (widget instanceof ButtonWidget button) {
                // The singleplayer button's text is typically "Singleplayer"
                // It's safer to check the actual screen it leads to if possible,
                // but for simple removal, checking the text is often sufficient.
                // In Minecraft 1.21.5, the singleplayer button's text is still "Singleplayer".
                return button.getMessage().equals(Text.translatable("menu.singleplayer"));
            }
            return false;
        });
    }
}
