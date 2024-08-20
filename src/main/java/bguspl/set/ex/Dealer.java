package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    public volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * The list of the players that placed 3 tokens on the board.
     */
    public Queue<Integer> toCheckQueue;

    /**
     * The last time we restarted the timer
     */
    private long startTime = System.currentTimeMillis();

    /**
     * the Time out indicator
     */
    private final long TURN_TIME_INDICATOR = 0;

    /**
     * The dealer semaphore
     */
    protected Semaphore semaphore;

    /**
     * The class constructor
     *
     * @param env
     * @param table
     * @param players
     */
    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.toCheckQueue = new LinkedList<>();
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.semaphore = new Semaphore(1, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        for (Player player: players) {
            Thread playerThread = new Thread(player);
            playerThread.start();
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        try {
            Thread.sleep(env.config.endGamePauseMillies);
        }
        catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() - env.config.turnTimeoutMillis + 1000;
        updateTimerDisplay(true);
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            if (!toCheckQueue.isEmpty()) {
                int playerID = toCheckQueue.peek();
                if (players[playerID].placedTokens == env.config.featureSize) {
                    boolean isSet = dealerCheck(playerID);
                    if (isSet) {
                        removeCardsFromTable();
                        placeCardsOnTable();
                        updateTimerDisplay(true);
                    }
                    /*else {
                        removeTokensFromTable(playerID);
                    }*/
                    rewardOrPenalizePlayer(toCheckQueue.poll(), isSet);
                }
                else {
                    removeTokensFromTable(playerID);
                    players[toCheckQueue.poll()].releasePlayer();
                }
            }
        }
    }

    /**
     * Removes all the tokens of the specified player
     *
     * @param player
     */
    private void removeTokensFromTable(int player) {
        table.removePlayersTokens(player);
        players[player].removeAllTokens();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for(Player p: players){
            p.terminate();
        }
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        boolean toCheck = (terminate || env.util.findSets(deck, 1).size() == 0);
        return toCheck;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        synchronized (table) {
            int[] cardsToRemove = table.getPlayerTokenedCards(toCheckQueue.peek());
            if (cardsToRemove != null) {
                for (int card : cardsToRemove) {
                    Integer slot = table.cardToSlot[card];
                    if(slot != null) {
                        LinkedList<Integer> playersToRemoveToken = table.removeTokens(slot);
                        for (Integer player : playersToRemoveToken) {
                            players[player].decreasePlacedTokens();
                        }
                        table.removeCard(slot);
                    }
                }
            }
        }
    }

    /**
     * Checks if a player placed his tokens on a legal set
     *
     * @param player
     * @return true iff the player's tokens are placed on a legal set
     */
    private boolean dealerCheck(int player) {
        int [] cardsToCheck = table.getPlayerTokenedCards(player);
        boolean isSet = env.util.testSet(cardsToCheck);
        return isSet;
    }

    /**
     * Rewards a player if he was right and punishes the player otherwise
     *
     * @param player
     * @param wasRight
     */
    private void rewardOrPenalizePlayer(int player, boolean wasRight) {
        if (wasRight) {
            players[player].point();
        }
        else {
            players[player].penalty();
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        synchronized (table) {
            while ((!deck.isEmpty()) && (table.findEmptySlot() != -1)) {
                table.placeCard(randomChooseCardFromDeck(), table.findEmptySlot());
            }
            if (deck.isEmpty()) {
                List<Integer> onTable = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
                if (env.util.findSets(onTable, 1).size() == 0) {
                    terminate();
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        if(reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis){
            try {
                Thread.sleep(env.config.tableDelayMillis);
            }
            catch (InterruptedException ignored){}
        }
        else{
            try{
                Thread.sleep(10);
            }
            catch (InterruptedException ignored){}
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset){
            reshuffleTime = Long.MAX_VALUE;
            startTime = System.currentTimeMillis();
            if(env.config.turnTimeoutMillis > TURN_TIME_INDICATOR){
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            }
        }
        if(env.config.turnTimeoutMillis > TURN_TIME_INDICATOR){
            long timeLeft = reshuffleTime - System.currentTimeMillis();
            boolean warn = timeLeft < env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(timeLeft, warn);
        }
        else if(env.config.turnTimeoutMillis == TURN_TIME_INDICATOR){
            long timePassed = System.currentTimeMillis() - startTime;
            env.ui.setElapsed(timePassed);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized (table) {
            table.removeAllTokens();
            for (Player player: players) {
                player.removeAllTokens();
            }
            for (int i = 0; i < table.slotToCard.length; i++) {
                if (table.slotToCard[i] != null) {
                    int addToDeck = table.slotToCard[i];
                    table.removeCard(i);
                    deck.add(addToDeck);
                }
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = Integer.MIN_VALUE;
        int count = 0;
        for(Player p: players){
            if(p.getScore() > max){
                count = 1;
                max = p.getScore();
            }
            else{
                if(p.getScore() == max)
                    count++;
            }
        }
        int[] winners = new int[count];
        int i = 0;
        for(Player p: players) {
            if(p.getScore() == max) {
                winners[i] = p.id;
                i++;
            }
        }
        env.ui.announceWinner(winners);
    }

    /**
     * Removes a random card from deck
     *
     * @return the id of the removed card
     */
    private int randomChooseCardFromDeck(){
        Random rand = new Random();
        return deck.remove(rand.nextInt(deck.size()));
    }

    /**
     * Adds an id of a player to the line of potential sets that the dealer needs to check
     *
     * @param playerID
     */
    public void addToCheckList (int playerID) {
        toCheckQueue.add(playerID);
    }
}