package com.ClanFightPerformanceTracker;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@PluginDescriptor(
		name = "Clan Fight Performance Tracker"
)
public class ClanFightPerformanceTrackerPlugin extends Plugin {

	private static final DecimalFormat DPS_FORMAT = new DecimalFormat("#0.0");
	@Getter(AccessLevel.PACKAGE)
	private final DpsMember userDPS = new DpsMember("user");
	boolean returning = false;
	boolean tanking = false;
	boolean shouldStartDPS = false;
	ArrayList<String> killMessages = new ArrayList<>(Arrays.asList("thinking challenging you",
			"was no match for you.", "didn't stand a chance against you.",
			"What an embarrassing performance by", "A humiliating defeat for",
			"You were clearly a better fighter than", "RIP ",
			"Can anyone defeat you? Certainly not ", "You have defeated ",
			"A certain crouching-over-face animation would be suitable for", "were an orange, you'd be the juicer",
			"was no match for your awesome awesomeness", "With a crushing blow you finish", "falls before your might",
			"You have stomped", "You have cleaned the floor",
			"nuff said", "Be proud of yourself", "into tiny pieces and sat on them"));
	@Inject
	private Client client;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private PerformanceOverlay overlay;
	@Inject
	private ClanFightPerformanceTrackerConfig config;
	private String username = "";
	private Integer lootKills = 0;
	private Integer startingKills = 0;
	private Integer endingKills = 0;
	private int chatMessageKDR = 0;
	private boolean usingRSKDR = true;
	private long lastTickMillis;
	@Getter(AccessLevel.PACKAGE)
	private Integer deaths = 0;
	long tankStartTime = 0;
	String lastTankTime = "NA";
	private List<Integer> tankTimes = new LinkedList<Integer>();
	private int hitsplatCount = 0;
	private int tankStartTick = 0;
	@Getter(AccessLevel.PACKAGE)
	private String averageTankTime = "NA";
	private List<Integer> returnTimes = new LinkedList<Integer>();
	long returnStartTime = 0;
	String lastReturnTime = "NA";
	private int returnStartTick = 0;
	@Getter(AccessLevel.PACKAGE)
	private String averageReturnTime = "NA";

	@Provides
	ClanFightPerformanceTrackerConfig provideConfig(ConfigManager configManager) {
		return (ClanFightPerformanceTrackerConfig) configManager.getConfig(ClanFightPerformanceTrackerConfig.class);
	}

	protected void startUp() {
		this.overlayManager.add(this.overlay);
	}

	protected void shutDown() {
		this.overlayManager.remove(this.overlay);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
		Actor actor = hitsplatApplied.getActor();
		if (!(actor instanceof Player)) {
			return; // only record dps on players, this is a pvp plugin after all
		}

		// we're being hit, should we start tank timer?
		if (actor == (Actor) client.getLocalPlayer()) {
			shouldStartDPS = true;
			hitsplatCount++;
			// 4 or more hits on us, start it
			if (hitsplatCount >= 4 && tankStartTick == 0) {
				tankStartTick = client.getTickCount();
				tankStartTime = System.currentTimeMillis();
				tanking = true;
			} else if (hitsplatCount < 4) {
				tankStartTick = 0;
				tanking = false;
			}
			return;
		}

		Hitsplat hitsplat = hitsplatApplied.getHitsplat();
		if (hitsplat.isMine()) { // hits are ours, add to DPS
			shouldStartDPS = true;
			int hit = hitsplat.getAmount();
			userDPS.addDamage(hit);
			if (userDPS.isPaused()) {
				userDPS.unpause();
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (event.getType() != ChatMessageType.GAMEMESSAGE) {
			return;
		}
		// count kills from list of all known chat kill messages
		for (String s : killMessages) {
			if (event.getMessage().contains(s)) {
				chatMessageKDR++;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		lastTickMillis = System.currentTimeMillis();

		int hp = client.getLocalPlayer().getHealthRatio();

		if (hp == -1) {
			hitsplatCount = 0; // no hp bar up means we aren't getting hit anymore
			if (tankStartTick != 0) {
				// we died, now how long did we tank for?
				int tankTime = client.getTickCount() - tankStartTick;
				lastTankTime = String.valueOf(getTimer(tankStartTime));
				tankTimes.add(tankTime);
				averageTankTime = calcAverageTankTime();
			}
			tankStartTick = 0;
			tanking = false;
		}

		if (usingRSKDR) {
			if (client.getVarbitValue(8376) != 0) { // When we exit pvp zones this gets set to 0
				endingKills = client.getVarbitValue(8376); // So store this on each tick in case someone exits pvp zone
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (event.getVarbitId() == 8376) {
			if (startingKills == 0 && usingRSKDR) { // KDR is '0' when outside pvp, if someone enters pvp zone grab their kill count
				startingKills = client.getVarbitValue(8376); // and it can never be unset by leaving pvp
			}
		}

		if (event.getVarbitId() == Varbits.SHOW_PVP_KDR_STATS) { // edge case someone toggles KDR
			if (client.getVarbitValue(Varbits.SHOW_PVP_KDR_STATS) == 0) {
				usingRSKDR = false;
			} else {
				usingRSKDR = true;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		// if someone logs out. disconnects or is hopping, stop dps timer
		if (client.getGameState() == GameState.LOGIN_SCREEN
				|| client.getGameState() == GameState.CONNECTION_LOST || client.getGameState() == GameState.HOPPING) {
			if (shouldStartDPS) { // this won't be true until we start combat at least once
				userDPS.pause();
			}
		}
		// in game KDR is the best way to track a users kills/deaths, determine if user has it enabled when they log in
		if (client.getGameState() == GameState.LOGGED_IN && client.getVarbitValue(Varbits.SHOW_PVP_KDR_STATS) == 0) {
			usingRSKDR = false;
		} else {
			usingRSKDR = true;
			if (client.getWorldType().contains((WorldType.PVP))) { // reset kdr if someone goes to a pvp world, as pvp worlds have their own
				startingKills = 0;
				endingKills = 0;
			}
		}
	}

	@Subscribe
	public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived) {
		if ((playerLootReceived.getPlayer().isClanMember()) || (playerLootReceived.getPlayer().isFriendsChatMember())) {
			return; // we shouldn't count kills on "team" members
		}
		lootKills++;
	}

	@Subscribe
	public void onActorDeath(ActorDeath actorDeath) {
		Actor actor = actorDeath.getActor();
		if (actor == (Actor) client.getLocalPlayer()) {
			if (returnStartTick != 0 && client.getTickCount() <= returnStartTick + 10) { // takes a few ticks to de-spawn after death
				return;
			}
			if (tankStartTick != 0) {
				// we died, now how long did we tank for?
				int tankTime = client.getTickCount() - tankStartTick;
				lastTankTime = String.valueOf(getTimer(tankStartTime));
				tankTimes.add(tankTime);
				averageTankTime = calcAverageTankTime();
			}
			deaths++;
			returnStartTick = client.getTickCount();
			returnStartTime = System.currentTimeMillis();
			returning = true;
			tanking = false;
			hitsplatCount = 0;
			tankStartTick = 0;
			userDPS.pause();
		}
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned playerSpawned) {
		// calculate how long it took us to get back to the fight
		int localClanMembersCount = 0;
		if (returning && client.getTickCount() > returnStartTick + 9) { // takes a few ticks to de-spawn after death
			if (client.getVarbitValue(Varbits.PVP_SPEC_ORB) == 0) {
				return; // if we aren't in pvp we aren't back at the fight
			}
			for (Player player : client.getPlayers()) {
				if (player.isFriendsChatMember() || player.isClanMember()) {
					localClanMembersCount++;
				}
				if (localClanMembersCount > 10) { // if there's more than 10 teammates around, assume we're back to the fight
					returning = false;
					int returnTime = client.getTickCount() - returnStartTick;
					lastReturnTime = String.valueOf(getTimer(returnStartTime));
					returnTimes.add(returnTime);
					averageReturnTime = calcAverageReturnTime();
					return;
				}
			}
		}
	}

	String calcAverageTankTime() {
		int totalTankTime = 0;
		for (Integer tankTime : tankTimes) {
			totalTankTime += tankTime;
		}

		return DPS_FORMAT.format((totalTankTime / tankTimes.size()) * 0.6) + "s";
	}

	String calcAverageReturnTime() {
		int totalReturnTime = 0;
		for (Integer returnTime : returnTimes) {
			totalReturnTime += returnTime;
		}

		return DPS_FORMAT.format((totalReturnTime / returnTimes.size()) * 0.6) + "s";
	}

	int getKillCount() {
		// we consider KDR > Chat Messages > Loot in order of reliability
		if (usingRSKDR) {
			return endingKills - startingKills;
		} else if (chatMessageKDR != 0) {
			return chatMessageKDR;
		} else {
			return lootKills;
		}
	}

	int getTimer(final long startTime) {
		return (int) ((System.currentTimeMillis() - startTime) / 1000);
	}
}