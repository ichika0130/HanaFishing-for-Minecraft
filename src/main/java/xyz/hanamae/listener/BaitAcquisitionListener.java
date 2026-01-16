package xyz.hanamae.listener;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import xyz.hanamae.bait.BaitManager;
import xyz.hanamae.bait.BaitType;

import java.util.concurrent.ThreadLocalRandom;

public class BaitAcquisitionListener implements Listener {
    private final BaitManager baitManager;

    public BaitAcquisitionListener(BaitManager baitManager) {
        this.baitManager = baitManager;
    }

    @EventHandler
    public void onDirtBreak(BlockBreakEvent event) {
        // 只针对泥土和草方块
        if (event.getBlock().getType() == Material.DIRT || event.getBlock().getType() == Material.GRASS_BLOCK) {
            // 5% 概率掉落蚯蚓
            if (ThreadLocalRandom.current().nextDouble() < 0.05) {
                event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation(),
                        baitManager.createBait(BaitType.EARTHWORM)
                );
            }
        }
    }
}