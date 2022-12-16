package com.ClanFightPerformanceTracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanFightPerformanceTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClanFightPerformanceTrackerPlugin.class);
		RuneLite.main(args);
	}
}