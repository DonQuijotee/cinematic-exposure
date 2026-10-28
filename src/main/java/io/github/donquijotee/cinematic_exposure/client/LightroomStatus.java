package io.github.donquijotee.cinematic_exposure.client;

import io.github.donquijotee.cinematic_exposure.mixin.LightroomChecksInvoker;
import io.github.mortuusars.exposure.Config;
import io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity;
import io.github.mortuusars.exposure.world.level.LevelUtil;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** A per-tick UI reading. Exposure's server remains the authority for whether a print may proceed. */
public record LightroomStatus(boolean sufficient, int current, int required, List<Component> problems) {
    public static LightroomStatus read(LightroomBlockEntity entity) {
        int required = Config.Server.LIGHTROOM_LIGHT_REQUIREMENT.get();
        var level = entity.getLevel();
        var above = entity.getBlockPos().above();
        int current = 0;
        if (level != null) {
            current = LevelUtil.getLightLevelAt(level, entity.isRefracted() ? above.above() : above);
            if (entity.isRefracted()) for (Direction side : Direction.Plane.HORIZONTAL)
                current = Math.max(current, LevelUtil.getLightLevelAt(level, above.relative(side)));
        }
        boolean enough = entity.hasSufficientLightLevel();
        List<Component> problems = new ArrayList<>();
        if (!enough) problems.add(text("light.low"));
        var frame = entity.getSelectedFrame();
        if (frame == null) problems.add(text("missing.film"));
        else if (entity.getItem(1).isEmpty()) problems.add(text("missing.paper"));
        else {
            var checks = (LightroomChecksInvoker)entity;
            var process = checks.cinematicExposure$process(frame);
            if (!checks.cinematicExposure$paper(frame, process)) problems.add(text("invalid.paper"));
            if (!checks.cinematicExposure$dyes(frame, process)) problems.add(text("missing.dyes"));
            if (!entity.canOutputToResultSlot(frame, process)) problems.add(text("blocked.output"));
        }
        if (entity.isPrinting()) problems.add(text("printing"));
        return new LightroomStatus(enough, current, required, List.copyOf(problems));
    }
    public static Component text(String key) { return Component.translatable("cinematic_exposure.lightroom."+key); }
    public Component label() { return text(sufficient ? "light.ok" : "light.low"); }
    public Component detail() { return Component.translatable("cinematic_exposure.lightroom.light.detail", current, required); }
    public int color() { return sufficient ? 0xFF8BE28B : 0xFFFFC15E; }
}
