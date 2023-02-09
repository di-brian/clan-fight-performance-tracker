package com.ClanFightPerformanceTracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("clanfightperformancetracker")
public interface ClanFightPerformanceTrackerConfig extends Config {
	@ConfigItem(position = 1, keyName = "showDPS", name = "Show DPS", description = "Display DPS")
	default boolean showDPS() {
		return true;
	}

	@ConfigItem(position = 2, keyName = "showTotalDamage", name = "Show damage dealt", description = "Display damage dealt")
	default boolean showDamageDealt() {
		return true;
	}

	@ConfigItem(position = 3, keyName = "showDamageTaken", name = "Show damage taken", description = "Display damage taken")
	default boolean showDamageTaken() {
		return true;
	}

	@ConfigItem(position = 4, keyName = "showKDR", name = "Show KDR", description = "Display KDR")
	default boolean showKDR() {
		return true;
	}

	@ConfigItem(position = 5, keyName = "showTankTime", name = "Show average tank time", description = "Display average tank time")
	default boolean showTankTime() {
		return true;
	}

	@ConfigItem(position = 6, keyName = "showReturnTime", name = "Show average return time", description = "Display average return time")
	default boolean showReturnTime() {
		return true;
	}

	@ConfigItem(position = 7, keyName = "showMaxTime", name = "Show amount of max hits", description = "Display amount of max hits")
	default boolean showMaxHits() {
		return true;
	}

	@ConfigItem(position = 8, keyName = "showSnareCounter", name = "Show number of successful snares", description = "Display number of successful snares")
	default boolean showSnares() {
		return true;
	}
}
