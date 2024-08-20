package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)


    /**
     * Mapping tokens per player and slot
     */
    protected boolean [][] tokensPTS;


    /**
     *
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {
        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokensPTS = new boolean[env.config.players][env.config.tableSize];
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {
        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * This method returns a possible legal sets of cards that are currently on the table.
     *
     * @return an array of slots that form a legal set
     */
    public int[] hintForAI() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        List<int[]> findSets = env.util.findSets(deck, Integer.MAX_VALUE);
        for(int[] set: findSets)
        {
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[] arrayToReturn = new int [slots.size()];
            int index = 0;
            for (Integer num: slots) {
                arrayToReturn[index] = num;
                index++;
            }
            return arrayToReturn;
        }
        return null;
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
            try {
                Thread.sleep(env.config.tableDelayMillis);
            } catch (InterruptedException ignored) {}
            cardToSlot[card] = slot;
            slotToCard[slot] = card;
            env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        int cardToRemove = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[cardToRemove] = null;
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void  placeToken(int player, int slot) {
        synchronized (this) {
            tokensPTS[player][slot] = true;
            env.ui.placeToken(player, slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        tokensPTS[player][slot] = false;
        env.ui.removeToken(player, slot);
        return tokensPTS[player][slot];
    }

    /**
     * Returns which cards were tokened by a player
     *
     * @param playerId
     * @return an Array of 3 cards contains of the cards the player placed his tokens on
     */
    public int[] getPlayerTokenedCards (int playerId) {
        synchronized (this) {
            int[] cardsToReturn = new int[env.config.featureSize];
            int foundTokens = 0;
            for (int i = 0; i < tokensPTS[playerId].length && foundTokens < 3; i++)
                if (tokensPTS[playerId][i]) {
                    Integer slotWeNeed = slotToCard[i];
                    if (slotWeNeed != null) {
                        cardsToReturn[foundTokens] = slotToCard[i];
                        foundTokens++;
                    }
                }
            return cardsToReturn;
        }
    }

    /**
     * Finds an empty slot on the game board
     *
     * @return the ID of an empty slot or -1 if there isn't one
     */
    public int findEmptySlot(){
        LinkedList<Integer> emptySlots = new LinkedList<>();
        for(int i = 0; i < slotToCard.length ; i++){
            if(slotToCard[i] == null){
                emptySlots.add(i);
            }
        }
        if (emptySlots.isEmpty()) {
            return -1;
        }
        else
        {
            Random rand = new Random();
            return emptySlots.get(rand.nextInt(emptySlots.size()));
        }
    }

    /**
     * Checks if a player placed a token on a specified slot
     *
     * @return True iff one of this player's tokens is placed on the slot
     */
    public boolean isPlayerTokenOnSlot(int playerId, int slot) {
        return tokensPTS[playerId][slot];
    }

    /**
     * Removes all tokens that are placed on a specified slot
     *
     * @param slot
     * @return a list of the players that placed a token on this slot
     */
    public LinkedList<Integer> removeTokens(int slot) {
        synchronized (this) {
            LinkedList<Integer> removedPlayers = new LinkedList<>();
            for (int i = 0; i < tokensPTS.length; i++) {
                if (tokensPTS[i][slot]) {
                    removedPlayers.add(i);
                    tokensPTS[i][slot] = false;
                }
            }
            env.ui.removeTokens(slot);
            return removedPlayers;
        }
    }

    /**
     * Removes all tokens from the board
     */
    public void removeAllTokens() {
        synchronized (this) {
            for (boolean[] tokensPT : tokensPTS) {
                Arrays.fill(tokensPT, false);
            }
            env.ui.removeTokens();
        }
    }

    /**
     * removes all tokens that were placed by a specified player from the board
     *
     * @param player
     * @return True iff all the player's tokens were placed on the board and removed successfully
     */
    public boolean removePlayersTokens (int player) {
        synchronized (this) {
            int foundTokens = 0;
            for (int i = 0; i < tokensPTS[player].length && foundTokens < env.config.featureSize; i++) {
                if (tokensPTS[player][i]) {
                    tokensPTS[player][i] = false;
                    env.ui.removeToken(player, i);
                    foundTokens++;
                }
            }
            return (foundTokens == env.config.featureSize);
        }
    }
}