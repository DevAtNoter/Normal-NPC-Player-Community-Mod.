package net.devatnoter.normalnpcplayer.ai.growth;

import net.minecraft.nbt.CompoundTag;

/** Persistent factual counters used by AI queries. The AI may record results through explicit events. */
public final class GrowthChildAIStats {
    private static final String TAG = "GrowthChildAIStats";
    private int woodLogs;
    private int miningBlocks;
    private int mobsDefeated;

    public void addWood(int amount) { woodLogs = Math.max(0, woodLogs + amount); }
    public void addMiningBlocks(int amount) { miningBlocks = Math.max(0, miningBlocks + amount); }
    public void addMobDefeat() { mobsDefeated++; }
    public String woodSummary() { return woodLogs + " logs collected."; }
    public String miningSummary() { return miningBlocks + " blocks mined."; }
    public String mobDefeatSummary() { return mobsDefeated + " hostile mobs defeated."; }
    public void save(CompoundTag root) {
        CompoundTag t = new CompoundTag();
        t.putInt("WoodLogs", woodLogs); t.putInt("MiningBlocks", miningBlocks); t.putInt("MobsDefeated", mobsDefeated);
        root.put(TAG, t);
    }
    public void load(CompoundTag root) {
        if (!root.contains(TAG)) return;
        CompoundTag t = root.getCompound(TAG);
        woodLogs = t.getInt("WoodLogs"); miningBlocks = t.getInt("MiningBlocks"); mobsDefeated = t.getInt("MobsDefeated");
    }
}
