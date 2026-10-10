package pt.inputrecorder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.ClientCommandHandler;

@Mod(modid = InputRecorderMod.MOD_ID, name = "Input Recorder", version = "1.1.0",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]",
        acceptableRemoteVersions = "*")
public final class InputRecorderMod {
    public static final String MOD_ID = "inputrecorder";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        RecorderController controller = new RecorderController();
        FMLCommonHandler.instance().bus().register(controller);
        MinecraftForge.EVENT_BUS.register(controller);
        ClientCommandHandler.instance.registerCommand(new RecorderCommand(controller));
    }
}
