package pt.inputrecorder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;

public final class RecorderCommand extends CommandBase {
    private final RecorderController controller;
    public RecorderCommand(RecorderController controller) { this.controller = controller; }
    @Override public String getCommandName() { return "rec"; }
    @Override public List<String> getCommandAliases() { return Arrays.asList("recorder"); }
    @Override public String getCommandUsage(ICommandSender sender) { return "/rec menu | record [nome] | play [nome] | loop [on|off|status] | stop | list | load <nome>"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }

    @Override public void processCommand(ICommandSender sender, String[] args) {
        String action = args.length == 0 ? "menu" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (args.length > 2) { controller.message(getCommandUsage(sender)); return; }
        
        try {
            if ("menu".equals(action) || "gui".equals(action)) controller.requestMenu();
            else if ("record".equals(action)) controller.requestRecord(args.length > 1 ? args[1] : null);
            else if ("play".equals(action)) controller.requestPlay(args.length > 1 ? args[1] : null);
            else if ("loop".equals(action)) {
                if (args.length == 1) controller.toggleLoop();
                else if ("on".equalsIgnoreCase(args[1])) controller.setLoop(true);
                else if ("off".equalsIgnoreCase(args[1])) controller.setLoop(false);
                else if ("status".equalsIgnoreCase(args[1])) controller.loopStatus();
                else controller.message("Use /rec loop [on|off|status].");
            }
            else if ("stop".equals(action)) controller.stop("Parado.");
            else if ("load".equals(action) && args.length == 2) controller.load(args[1]);
            else if ("list".equals(action)) {
                List<String> names = controller.list();
                controller.message(names.isEmpty() ? "Nenhuma gravacao salva." : "Gravacoes (mais recentes primeiro):");
                for (int i = 0; i < Math.min(names.size(), 30); i++) controller.message(names.get(i));
                if (names.size() > 30) controller.message("Mostrando 30 de " + names.size() + "; veja config/inputrecorder/recordings.");
            } else {
                controller.message("F6 gravar/salvar | F7 reproduzir | F8 parar | F9 loop | F10 menu.");
                controller.message(getCommandUsage(sender));
                controller.message("Grava o jogo a 20 ticks/s; abrir menus ou perder foco encerra a sessao.");
            }
        } catch (IOException | IllegalArgumentException exception) { controller.message(exception.getMessage()); }
    }

    @Override public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "menu", "record", "play", "loop", "stop", "list", "load", "help");
        if (args.length == 2 && "loop".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "on", "off", "status");

        if (args.length == 2 && ("play".equals(args[0]) || "load".equals(args[0]))) {
            try { return getListOfStringsMatchingLastWord(args, controller.list()); }
            catch (IOException exception) { return null; }
        }
        return null;
    }
}
