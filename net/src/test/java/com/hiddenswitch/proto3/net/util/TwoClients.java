package com.hiddenswitch.proto3.net.util;

import com.hiddenswitch.proto3.net.GameSessions;
import com.hiddenswitch.proto3.net.Service;
import com.hiddenswitch.proto3.net.ServiceTestBase;
import com.hiddenswitch.proto3.net.client.RemoteGameContext;
import com.hiddenswitch.proto3.net.common.ClientConnectionConfiguration;
import com.hiddenswitch.proto3.net.common.ClientToServerMessage;
import com.hiddenswitch.proto3.net.common.MatchmakingResponse;
import com.hiddenswitch.proto3.net.common.ServerGameContext;
import com.hiddenswitch.proto3.net.models.CreateGameSessionRequest;
import com.hiddenswitch.proto3.net.models.CreateGameSessionResponse;
import com.hiddenswitch.proto3.server.PregamePlayerConfiguration;
import net.demilich.metastone.game.cards.CardCatalogue;
import net.demilich.metastone.game.cards.CardParseException;
import net.demilich.metastone.game.decks.Deck;
import net.demilich.metastone.game.decks.DeckCatalogue;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by bberman on 11/18/16.
 */
public class TwoClients {
	private RemoteGameContext playerContext1;
	private RemoteGameContext playerContext2;
	private Thread thread1;
	private Thread thread2;
	private String gameId;
	private GameSessions service;
	private Logger logger = LoggerFactory.getLogger(TwoClients.class);

	public ServerGameContext getServerGameContext() {
		return service.getGameContext(gameId);
	}

	public RemoteGameContext getPlayerContext1() {
		return playerContext1;
	}

	public RemoteGameContext getPlayerContext2() {
		return playerContext2;
	}

	public Thread getThread1() {
		return thread1;
	}

	public Thread getThread2() {
		return thread2;
	}

	public TwoClients invoke(GameSessions service) throws IOException, URISyntaxException, CardParseException {
		this.service = service;
		CardCatalogue.loadCardsFromPackage();

		CreateGameSessionRequest request = new CreateGameSessionRequest();
		AIPlayer player1 = new AIPlayer();
		AIPlayer player2 = new AIPlayer();
		PregamePlayerConfiguration pregame1 = new PregamePlayerConfiguration(player1.getConfiguredDeck(), "Player 1");
		pregame1.setPlayer(player1);
		PregamePlayerConfiguration pregame2 = new PregamePlayerConfiguration(player2.getConfiguredDeck(), "Player 2");
		pregame2.setPlayer(player2);

		request.setPregame1(pregame1);
		request.setPregame2(pregame2);
		request.setGameId(RandomStringUtils.randomAlphanumeric(8));

		CreateGameSessionResponse response = service.createGameSession(request);
		this.gameId = response.getGameId();
		// Manually override the player in the configurations
		playerContext1 = createRemoteGameContext(response.getConfigurationForPlayer1());
		playerContext2 = createRemoteGameContext(response.getConfigurationForPlayer2());
		playerContext1.ignoreEventOverride = true;
		playerContext2.ignoreEventOverride = true;
		thread1 = new Thread(playerContext1::play);
		thread2 = new Thread(playerContext2::play);
		return this;
	}

	public TwoClients invoke(MatchmakingResponse response1, Deck deck1, MatchmakingResponse response2, Deck deck2, String gameId, GameSessions service) {
		this.service = service;
		this.gameId = gameId;
		// Manually override the player in the configurations
		AIPlayer player1 = new AIPlayer(deck1);
		AIPlayer player2 = new AIPlayer(deck2);
		ClientToServerMessage firstMessage1 = response1.getConnection().getFirstMessage();
		ClientToServerMessage firstMessage2 = response2.getConnection().getFirstMessage();
		player1.setId(firstMessage1.getPlayer1().getId());
		player2.setId(firstMessage2.getPlayer1().getId());
		firstMessage1.setPlayer1(player1);
		firstMessage2.setPlayer1(player2);
		playerContext1 = createRemoteGameContext(response1.getConnection());
		playerContext2 = createRemoteGameContext(response2.getConnection());
		playerContext1.ignoreEventOverride = true;
		playerContext2.ignoreEventOverride = true;
		thread1 = new Thread(playerContext1::play);
		thread2 = new Thread(playerContext2::play);
		return this;
	}

	private RemoteGameContext createRemoteGameContext(ClientConnectionConfiguration configuration) {
		try {
			DeckCatalogue.loadDecksFromPackage();
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}

		return new RemoteGameContext(configuration);
	}

	public void play() {
		thread1.start();
		thread2.start();
	}

	public boolean isInterrupted() {
		return getThread1().isInterrupted() || getThread2().isInterrupted();
	}

	public boolean gameDecided() {
		return getPlayerContext1().gameDecided() && getPlayerContext2().gameDecided();
	}

	public void assertGameOver() {
		List<Throwable> exceptions = getServerGameContext().getExceptions();
		if (!gameDecided()
				|| isTimedOut()
				|| exceptions.size() > 0) {
			// Print some diagnostic information
			log("A match was not decided in this test by the deadline. Game information:");
		} else {
			logger.info("TwoClients match complete.");
		}
		ServiceTestBase.getContext().assertTrue(gameDecided());
		ServiceTestBase.getContext().assertFalse(isTimedOut());
		ServiceTestBase.getContext().assertTrue(exceptions.size() == 0);
		ServiceTestBase.getContext().assertTrue(playerContext1.getWinningPlayerId() == playerContext2.getWinningPlayerId());
		this.dispose();
	}

	public void log(String message) {
		logger.error(message);
		final ServerGameContext serverGameContext = getServerGameContext();
		if (serverGameContext == null) {
			logger.error("Server game context is null?");
		} else {
			logger.error(serverGameContext.toLongString());
			logger.error("Panic Dump:");
			serverGameContext.getLogic().panicDump();
		}
	}

	public void dispose() {
		thread1.interrupt();
		thread2.interrupt();
		playerContext1.dispose();
		playerContext2.dispose();
	}

	public boolean isTimedOut() {
		return !gameDecided() && playerContext1.isTimedOut() && playerContext2.isTimedOut();
	}

	public boolean isTimedOut(long time) {
		return !gameDecided() && playerContext1.isTimedOut(time) && playerContext2.isTimedOut(time);
	}

	public String getGameId() {
		return gameId;
	}
}