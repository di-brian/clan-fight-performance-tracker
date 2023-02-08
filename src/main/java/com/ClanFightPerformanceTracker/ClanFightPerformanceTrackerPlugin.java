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
import java.util.*;

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
	private Integer lootKills = 0;
	private Integer startingKills = 0;
	private Integer endingKills = 0;
	private int chatMessageKDR = 0;
	private boolean usingRSKDR = true;
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
	@Getter(AccessLevel.PACKAGE)
	private int maxHits = 0;
	private int snareTick = 0;
	@Getter(AccessLevel.PACKAGE)
	private int snares = 0;
	@Getter(AccessLevel.PACKAGE)
	private int successfulSnares = 0;
	private final Map<Skill, Integer> previousSkillExpTable = new EnumMap<>(Skill.class);

	@Provides
	ClanFightPerformanceTrackerConfig provideConfig(ConfigManager configManager) {
		return (ClanFightPerformanceTrackerConfig) configManager.getConfig(ClanFightPerformanceTrackerConfig.class);
	}

	protected void startUp() { this.overlayManager.add(this.overlay); }

	protected void shutDown() {
		reset();
		this.overlayManager.remove(this.overlay);
	}

	public void reset(){
		lootKills = 0;
		startingKills = endingKills;
		endingKills = 0;
		chatMessageKDR = 0;
		deaths = 0;
		tankStartTime = 0;
		lastTankTime = "NA";
		tankTimes.clear();
		hitsplatCount = 0;
		tankStartTick = 0;
		averageTankTime = "NA";
		returnTimes.clear();
		returnStartTime = 0;
		lastReturnTime = "NA";
		returnStartTick = 0;
		averageReturnTime = "NA";
		maxHits = 0;
		snareTick = 0;
		snares = 0;
		successfulSnares = 0;
		userDPS.reset();
		returning = false;
		tanking = false;
		shouldStartDPS = false;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied hitsplatApplied) {
		Actor actor = hitsplatApplied.getActor();
		if (!(actor instanceof Player)) {
			return; // only record dps on players, this is a pvp plugin after all
		}

		// we're being hit, should we start tank timer?
		if (actor == (Actor) client.getLocalPlayer()) {
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
			if(hitsplat.getHitsplatType() == HitsplatID.DAMAGE_MAX_ME){
				maxHits++;
			}
			shouldStartDPS = true;
			int hit = hitsplat.getAmount();
			userDPS.addDamage(hit);
			if (userDPS.isPaused()) {
				userDPS.unpause();
			}
			if (snareTick != 0 && hit < 3 && client.getTickCount() < snareTick + 5 && isRegularSpellbook()) {
				snares++;
				successfulSnares++;

				// reset snare check
				snareTick = 0;
			} else if (snareTick != 0 && hit > 2) {
				snareTick = 0;
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged)
	{
		final Skill skill = statChanged.getSkill();
		final int xp = statChanged.getXp();

		Integer previous = previousSkillExpTable.put(skill, xp);
		if (skill == Skill.MAGIC) {
			int xpChange = xp - previous;
			if (59 < xpChange && xpChange < 66 && isRegularSpellbook()) {
				snareTick = client.getTickCount();
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

		// Checking the tick before the next mage attack tick - for failed snares (no hitsplat)
		if (snareTick != 0 && client.getTickCount() == snareTick + 4 && isRegularSpellbook()) {
			snares++;
			snareTick = 0;
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
			if (client.getVarbitValue(Varbits.SHOW_PVP_KDR_STATS) == 0 || nonRegularWorld(client.getWorldType())) {
				usingRSKDR = false;
			} else {
				usingRSKDR = true;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		// if someone logs out, disconnects or is hopping, stop dps timer
		if (client.getGameState() == GameState.LOGIN_SCREEN
				|| client.getGameState() == GameState.CONNECTION_LOST || client.getGameState() == GameState.HOPPING) {
			if (shouldStartDPS) { // this won't be true until we start combat at least once, so gets around any issues at first time login screen
				userDPS.pause();
			}
		}
		// in game KDR is the best way to track a users kills/deaths, determine if user has it enabled when they log in
		if (client.getGameState() == GameState.LOGGED_IN && client.getVarbitValue(Varbits.SHOW_PVP_KDR_STATS) == 0 || nonRegularWorld(client.getWorldType())) {
			usingRSKDR = false;
		} else {
			usingRSKDR = true;
		}
	}

	public boolean isRegularSpellbook() {
		return client.getVarbitValue(4070) == 0;
	}

	private boolean nonRegularWorld(EnumSet<WorldType> worldType){
		// All of these world types track KDR separately so just use kill messages on those to avoid any issues
		if(worldType.contains(WorldType.PVP) || worldType.contains(WorldType.BOUNTY) || worldType.contains(WorldType.HIGH_RISK)
		|| worldType.contains(WorldType.DEADMAN) || worldType.contains(WorldType.SEASONAL) || worldType.contains(WorldType.TOURNAMENT_WORLD)){
			return true;
		}
		return false;
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
			if (!client.getWorldType().contains(WorldType.PVP) && client.getVarbitValue(Varbits.IN_WILDERNESS) == 0) {
				return; // if you aren't in wilderness on a regular world you aren't back at the fight
			}
			if (client.getWorldType().contains(WorldType.PVP)) {
				// pvp world fights might not be in wilderness
				if(client.getVarbitValue(Varbits.MULTICOMBAT_AREA) == 0){
					return; // if you aren't in multi you aren't back
				}
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