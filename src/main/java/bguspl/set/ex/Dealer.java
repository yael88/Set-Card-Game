package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private volatile boolean terminate;
    private Thread[] threads;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    private ConcurrentLinkedQueue<ArrayList<Object>> actions;

    Long startingTime;



    private AtomicBoolean shouldSleep;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        terminate = false;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        actions = new ConcurrentLinkedQueue<>();
        threads = new Thread[players.length];
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        startingTime = System.currentTimeMillis();

        shouldSleep = new AtomicBoolean(true);

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        for (int i = 0; i < players.length; i++) {
            threads[i] = new Thread(players[i]);
            threads[i].start();
        }
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            for (int i = 0; i < players.length; i++) {
                players[i].setCanRun(true);
            }
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
            actions.clear();
        }
        terminate();
        announceWinners();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            //table.hints();
            try {
                sleepUntilWokenOrTimeout();
            } catch (InterruptedException e) {
            }
            while (!actions.isEmpty()) {
                ArrayList<Object> action = actions.remove();
                ArrayList<Integer> setToCheck = (ArrayList<Integer>) (action.get(0));
                if (setToCheck.size() == 3) {
                    int id = (int) (action.get(1));
                    int[] arr = {table.slotToCard[setToCheck.get(0)], table.slotToCard[setToCheck.get(1)], table.slotToCard[setToCheck.get(2)]};
                    System.out.println("got set from: " + id);
                    System.out.println("the set i got: " + arr[0] + " " + arr[1] + " " + arr[2]);
                    System.out.println("answer: " + env.util.testSet(arr));
                    if (env.util.testSet(arr)) {
                        //point==-1
                        removeCardsFromTable(setToCheck);
                        players[id].addAction(-1);
                        placeCardsOnTable();
                        updateTimerDisplay(true);
                    } else {
                        //penalty==-2
                        players[id].addAction(-2);
                        synchronized (this) {
                            notifyAll();
                        }
                        updateTimerDisplay(false);
                    }
                    synchronized (this) {
                        notifyAll();
                    }
                }
            }
            shouldSleep.set(true);
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
        }
        for (int i = players.length - 1; i >= 0; i--) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        synchronized (this) {
            notifyAll();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable(ArrayList<Integer> arr) {//arr-> list of slots, not cards
        ArrayList<Integer> arr2 = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            arr2.add(arr.get(i));
        }
        for (int i = 0; i < arr2.size(); i++) {
            table.removeCard(arr2.get(i));
            for (int j = 0; j < players.length; j++) {
                players[j].removeToken(arr2.get(i));
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.canPlace(i)) {
                if (deck.size() > 0) {
                    table.placeCard(deck.remove(0), i);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() throws InterruptedException {
        //TODO: pay attention to timeout
        updateTimerDisplay(false);
        while (!terminate && System.currentTimeMillis() < reshuffleTime && shouldSleep.get()) {
            synchronized (this) {
                wait(1000);
            }
            updateTimerDisplay(false);
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            startingTime = System.currentTimeMillis();
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        } else {
            if (env.config.turnTimeoutMillis - (System.currentTimeMillis() - startingTime) > env.config.turnTimeoutWarningMillis) {
                env.ui.setCountdown(env.config.turnTimeoutMillis - (System.currentTimeMillis() - startingTime), false);
            } else {
                env.ui.setCountdown(env.config.turnTimeoutMillis - (System.currentTimeMillis() - startingTime), true);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                deck.add(table.slotToCard[i]);
                table.removeCard(i);
            }
            //return card to deck
        }
        for (int i = 0; i < players.length; i++) {
            players[i].newRound();
            players[i].setCanRun(false);
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int bestScore = players[0].getScore();
        int maxCount = 1;
        for (int i = 1; i < players.length; i++) {
            if (players[i].getScore() > bestScore) {
                bestScore = players[i].getScore();
                maxCount = 1;
            } else if (players[i].getScore() == bestScore) {
                maxCount++;
            }
        }
        int[] winners = new int[maxCount];
        int i = 0;
        for (int j = 0; j < players.length; j++) {
            if (players[j].getScore() == bestScore) {
                winners[i] = j;
                i++;
            }
        }
        env.ui.announceWinner(winners);
    }

    public void addAction(ArrayList<Integer> tokens, int id) {
        ArrayList<Object> objects = new ArrayList<>();
        objects.add(tokens);
        objects.add(id);
        System.out.println("add: " + id);
        actions.add(objects);
        shouldSleep.set(false);
        synchronized (this) {
            notifyAll();
        }
    }
}
