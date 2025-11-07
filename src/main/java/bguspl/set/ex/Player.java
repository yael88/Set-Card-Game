package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
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

    public ArrayList<Integer> tokens;

    public volatile ConcurrentLinkedQueue<Integer> queue;//volatile to give access to the Dealer

    private Dealer dealer;

    private AtomicBoolean canRun;



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
        tokens=new ArrayList<>();
        queue=new ConcurrentLinkedQueue<>();
        this.dealer=dealer;
        canRun=new AtomicBoolean(false);
       // shouldSleep=new AtomicBoolean(true);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            try {
                synchronized (this)  {wait();}
            } catch (InterruptedException e) {}
            while(!queue.isEmpty()){
                int slot=queue.remove();
                System.out.println(id+ ": i have a task: "+ slot);
                if(slot>=0) {
                    boolean exists = table.removeToken(id, slot);
                    if (!exists) {
                        if(table.slotToCard[slot]!=null) {
                            if (tokens.size() < 3) {
                                table.placeToken(id, slot);
                                tokens.add(slot);
                                if (tokens.size() == 3) {
                                    canRun.set(false);
                                    System.out.println("stopped now");
                                    queue.clear();
                                    dealer.addAction(tokens, id);
                                    synchronized (this){notifyAll();}
                                }
                            }
                        }
                    } else {
                        if (tokens.indexOf(slot) != -1) {
                            tokens.remove(tokens.indexOf(slot));
                        }
                    }
                }else {
                    if(slot==-1){
                        System.out.println("found it-point");
                        point();
                    }
                    if(slot==-2){
                        System.out.println("found it-penalty");
                        penalty();
                    }
                }
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                Random r = new Random();
                int slot=r.nextInt(env.config.tableSize);
                if(tokens.size()<=3) {
                    keyPressed(slot);
                }
//                try {
//                    synchronized (this) {
//                       wait(1);
//                   }
//                } catch (InterruptedException ignored) {
//                }

            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate=true;
        synchronized (this){notifyAll();}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if(canRun.get()) {
            queue.add(slot);
            System.out.println("added key press");
            synchronized (this){notifyAll();}
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        //for(int i=0;i<tokens.size();i++){
        //    table.removeToken(id,tokens.get(i));
       // }
        tokens.clear();
        queue.clear();
        try {
            for(int i=0;i<=env.config.pointFreezeMillis;i+=1000){
                env.ui.setFreeze(id,env.config.pointFreezeMillis-i);
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        canRun.set(true);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        queue.clear();
        try {
            for(int i=0;i<=env.config.penaltyFreezeMillis;i+=1000){
                env.ui.setFreeze(id,env.config.penaltyFreezeMillis-i);
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        canRun.set(true);
    }

    public int getScore() {
        return score;
    }

    public void addAction(int action){
        queue.add(action);
        synchronized (this){notifyAll();}
    }

    public void newRound(){
        tokens.clear();
        queue.clear();
    }

    public void setCanRun(boolean boo){
        System.out.println(id+" can run: "+boo);
        canRun.set(boo);
    }

    public void removeToken(int slot){
        if(tokens.indexOf(slot)!=-1) {
            tokens.remove(tokens.indexOf(slot));
            System.out.println("removed token from player "+id);
        }
    }
}
