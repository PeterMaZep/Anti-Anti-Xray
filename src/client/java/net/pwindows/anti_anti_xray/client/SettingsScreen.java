package net.pwindows.anti_anti_xray.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;

public class SettingsScreen extends OptionsSubScreen {

    public Screen parent;
    private EditBox seedInput;
    private Button uploadButton;
    private ConfigManager.ServerConfig config;
    private String selectedDatapackName = null;

    public SettingsScreen(Component title, Screen parent) {
        super(parent, Minecraft.getInstance().options, title);
        this.parent = parent;
    }

    public SettingsScreen(Component title) {
        super(null, Minecraft.getInstance().options, title);
    }

    @Override
    protected void init() {
        super.init();
        System.out.println("SettingsScreen initialized!");
        config = ConfigManager.load();

        String savedDatapack = config.datapacks.get(ConfigManager.getCurrentDimension());
        if (savedDatapack != null) {
            selectedDatapackName = new File(savedDatapack).getName();
        }

        seedInput = new EditBox(
                this.font,
                this.width / 2 - 100,
                50,
                200,
                20,
                Component.literal("Seed")
        );
        seedInput.setHint(Component.literal("Enter seed..."));
        seedInput.setValue(config.seeds.getOrDefault(ConfigManager.getCurrentDimension(), ""));
        seedInput.setCanLoseFocus(true);
        this.setInitialFocus(seedInput);
        this.addRenderableWidget(seedInput);

        uploadButton = Button.builder(Component.literal("Upload Datapack"), btn -> {
                    System.out.println("Button clicked!");
                })
                .bounds(this.width / 2 - 100, 90, 200, 20)
                .build();
        this.addRenderableWidget(uploadButton);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = Minecraft.getInstance().mouseHandler.xpos()
                * this.width / Minecraft.getInstance().getWindow().getScreenWidth();
        double mouseY = Minecraft.getInstance().mouseHandler.ypos()
                * this.height / Minecraft.getInstance().getWindow().getScreenHeight();

        System.out.println("X: " + mouseX + " Y: " + mouseY);
        System.out.println("Button bounds: " + uploadButton.getX() + ", " + uploadButton.getY()
                + ", " + (uploadButton.getX() + uploadButton.getWidth())
                + ", " + (uploadButton.getY() + uploadButton.getHeight()));

        if (mouseX >= uploadButton.getX() && mouseX <= uploadButton.getX() + uploadButton.getWidth()
                && mouseY >= uploadButton.getY() && mouseY <= uploadButton.getY() + uploadButton.getHeight()) {
            System.out.println("Manual button hit!");
            openFileDialog();
            return true;
        }

        return super.mouseClicked(event, bl);
    }

    private void openFileDialog() {
        new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.zip"));
                filters.flip();
                String result = TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Datapack", null, filters, "ZIP Files (*.zip)", false
                );
                System.out.println("Dialog result: " + result);
                if (result != null) {
                    File selected = new File(result);
                    File saved = ConfigManager.saveDatapack(selected, ConfigManager.getCurrentDimension());
                    if (saved != null) {
                        selectedDatapackName = saved.getName();
                        config.datapacks.put(ConfigManager.getCurrentDimension(), saved.getAbsolutePath());
                        ConfigManager.save(config);
                    }
                }
            }
        }).start();
    }

    @Override
    protected void addOptions() {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, "Seed", this.width / 2 - 100, 38, 0xFFFFFFFF, true);
        graphics.text(this.font, "Datapack", this.width / 2 - 100, 78, 0xFFFFFFFF, true);

        String displayName = selectedDatapackName != null ? selectedDatapackName : "No datapack selected";
        graphics.text(this.font, displayName, this.width / 2 - 100, 115, 0xAAAAAA, true);
    }

    @Override
    public void onClose() {
        config.seeds.put(ConfigManager.getCurrentDimension(), seedInput.getValue());
        ConfigManager.save(config);
        this.minecraft.setScreen(this.parent);
    }
}