package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The queue for actions the thread needs to do by order.
     */
    private BlockingQueue<Integer> actionQueue;

    /**
     * Number of tokens the player placed on board
     */
    protected int placedTokens;

    /**
     * The player's dealer
     */
    private Dealer dealer;

    /**
     * True iff the player is pending for a set check
     */
    private AtomicBoolean lock;

    /**
     * Time the player needs to sleep after getting notified
     */
    private double sleepTime;

    /**
     * The period of time between 2 following key presses of a UI player
     */
    private final int UI_DELAY_TIME = 400;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actionQueue = new ArrayBlockingQueue<>(3);
        this.placedTokens = 0;
        this.dealer = dealer;
        this.lock = new AtomicBoolean();

    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createSmartArtificialIntelligence();
        while (!terminate) {
            if(!actionQueue.isEmpty()){
                act(actionQueue.remove());
            }
            else{
                synchronized (this) {
                    notifyAll();
                }
            }
        }
        if (!human) {
            try {
                aiThread.join();
            }
            catch (InterruptedException ignored) {}
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional STUPID thread for an AI (computer) player.
     * The main loop of this thread repeatedly generates key presses.
     * If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                Random rand = new Random();
                keyPressed(rand.nextInt(env.config.tableSize));
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ignored) {
                }
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Creates an additional SMART thread for an AI (computer) player.
     * This AI is programed to claim a correct set pretty often.
     * The main loop of this thread repeatedly generates key presses.
     * If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createSmartArtificialIntelligence() {
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            int i = 0;
            int[] slotsToSet = new int[env.config.featureSize];
            int[] notToSet = new int [env.config.featureSize];
            while (!terminate) {
                try {
                    Thread.sleep(UI_DELAY_TIME);
                }
                catch (InterruptedException ignored) {}
                i = (i % 9);
                if (i < 3) {
                    Random rand = new Random();
                    notToSet[i] = rand.nextInt(env.config.tableSize);
                    keyPressed(notToSet[i]);
                }
                else {
                    if (i < 6) {
                        keyPressed(notToSet[i % 3]);
                    }
                    else {
                        if (i == 6) {
                            slotsToSet = table.hintForAI();
                        }
                        if (slotsToSet != null) {
                            keyPressed(slotsToSet[(i % 6)]);
                        }
                    }
                }
                i++;
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (table.slotToCard[slot] != null) {
            actionQueue.add(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        sleepTime = env.config.pointFreezeMillis;
        env.ui.setFreeze(id, env.config.pointFreezeMillis);
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /**
     * Notifies a player that claimed a set that isn't valid anymore
     */
    public void releasePlayer() {
        sleepTime = 0;
        synchronized (lock){
            lock.notifyAll();
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        sleepTime = env.config.penaltyFreezeMillis;
        synchronized (lock){
            lock.notifyAll();
        }
    }

    /**
     * A getter for a player's score
     *
     * @return the player's score
     */
    public int getScore() {
        return score;
    }

    /**
     * preforming all the actions that follow a slot pick (by a key press)
     *
     * @param slot
     */
    public void act(int slot){
        if(placedTokens < env.config.featureSize) {
            if (!table.isPlayerTokenOnSlot(id, slot)) {
                table.placeToken(id, slot);
                placedTokens++;
                if (placedTokens == env.config.featureSize) {
                    try {
                        synchronized (lock) {
                            dealer.semaphore.acquire();
                            dealer.addToCheckList(this.id);
                            dealer.semaphore.release();
                            lock.wait();
                            Thread.sleep((long) sleepTime);
                            env.ui.setFreeze(id, 0);
                        }
                    } catch (InterruptedException ignored) {}
                }
            } else {
                table.removeToken(id, slot);
                decreasePlacedTokens();
            }
            synchronized (this) {
                if (actionQueue.size() < env.config.featureSize) {
                    notifyAll();
                }
            }
        }
        else{
            if (table.isPlayerTokenOnSlot(id, slot)){
                table.removeToken(id, slot);
                decreasePlacedTokens();
            }
            synchronized (this) {
                if (actionQueue.size() < 3) {
                    notifyAll();
                }
            }
        }
    }

    /**
     * Decreases the number of the player's placed tokens by 1
     */
    public void decreasePlacedTokens () {
        placedTokens--;
    }

    /**
     * sets the number of the player's placed tokens to zero
     */
    public void removeAllTokens() {
        placedTokens = 0;
    }
}