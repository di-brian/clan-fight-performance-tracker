package com.ClanFightPerformanceTracker;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.*;
import java.text.DecimalFormat;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

public class PerformanceOverlay extends OverlayPanel {

	private static final DecimalFormat DPS_FORMAT = new DecimalFormat("#0.0");

	private final ClanFightPerformanceTrackerPlugin plugin;
	private final ClanFightPerformanceTrackerConfig config;
	private final Client client;

	@Inject
	private PerformanceOverlay(ClanFightPerformanceTrackerPlugin plugin, ClanFightPerformanceTrackerConfig config, Client client) {
		super(plugin);
		setPosition(OverlayPosition.TOP_RIGHT);
		setPriority(OverlayPriority.LOW);
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		panelComponent.setWrap(true);
		panelComponent.setOrientation(ComponentOrientation.HORIZONTAL);
		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Performance overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics) {
		if (this.config.showDPS()) {
			panelComponent.getChildren().add(LineComponent.builder()
					.left("DPS:")
					.right(DPS_FORMAT.format(plugin.getUserDPS().getDps())).build());
		}
		if (this.config.showDamageDealt()) {
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Damage Dealt:")
					.right(String.valueOf(plugin.getUserDPS().getDamage())).build());
		}
		if (this.config.showDamageTaken()) {
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Damage Taken:")
					.right(String.valueOf(plugin.getDamageTaken())).build());
		}
		if (this.config.showKDR()) {
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Kills: " + plugin.getKillCount())
					.right(" Deaths: " + plugin.getDeaths()).build());
		}
		if (this.config.showSnares() && this.plugin.isRegularSpellbook()) {
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Snares: ")
					.right(plugin.getSuccessfulSnares() + " / " + plugin.getSnares()).build());
		}
		if (this.config.showTankTime()) {
			if (plugin.tanking) {
				String tankTime = String.valueOf(plugin.getTimer(plugin.tankStartTime));
				panelComponent.getChildren().add(LineComponent.builder()
						.left("Avg tank: ")
						.right(plugin.getAverageTankTime() + "(" + tankTime + ")").build());
			} else {
				panelComponent.getChildren().add(LineComponent.builder()
						.left("Avg tank: ")
						.right(plugin.getAverageTankTime() + " (" + plugin.lastTankTime + ")").build());
			}
		}
		if (this.config.showReturnTime()) {
			if (plugin.returning) {
				String returnTime = String.valueOf(plugin.getTimer(plugin.returnStartTime));
				panelComponent.getChildren().add(LineComponent.builder()
						.left("Avg return: ")
						.right(plugin.getAverageReturnTime() + "(" + returnTime + ")").build());
			} else {
				panelComponent.getChildren().add(LineComponent.builder()
						.left("Avg return: ")
						.right(plugin.getAverageReturnTime() + " (" + plugin.lastReturnTime + ")").build());
			}
		}
		if (this.config.showMaxHits()) {
			panelComponent.getChildren().add(LineComponent.builder()
					.left("Max hits: ")
					.right(String.valueOf(plugin.getMaxHits())).build());
		}
		return super.render(graphics);
	}
}
