package com.ytci.kristminer;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class KristMiner {
    static class WorkerThread extends Thread {
        private static final Object accSendingSolutionLock = new Object();
        private static final Object sendingSolutionLock = new Object();
        private static boolean sendingSolution = false;

        private String address;
        private String id;

        //private final Object lock = new Object();
        private final ReentrantLock lock = new ReentrantLock();
        private final Object unpauseReady = new Object();
        private int work;
        private String addrWBlock;

        public WorkerThread(String address, String id) {
            this.address = address;
            this.id = id;
        }

        @Override
        public void run() {
            try {
                long nonce = 0;
                while(true) {
                    {
                        if(KristMiner.isPaused()) {
                            synchronized(unpauseReady) {
                                unpauseReady.wait();
                            }
                        }
                        lock.lock();
                        int i = 0;
                        for(; i < 100000; i++, nonce++) {
                            String nonceStr = Long.toString(nonce, 36);
                            long hashNum = SHA256.hashToLong(SHA256.digest((addrWBlock + nonceStr).getBytes(StandardCharsets.UTF_8)));
                            if(hashNum < work) {
                                synchronized(sendingSolutionLock) {
                                    if(getSendingSolution()) {
                                        break;
                                    }
                                    setSendingSolution(true);
                                }
                                System.out.println("Solved! Nonce: " + id + nonceStr);
                                KristMiner.pause(true);
                                KristMiner.addBlocksDone(1);
                                KristMiner.submitBlock(id + nonceStr);
                                break;
                            }
                        }
                        KristMiner.addHashesDone(i + 1);
                        lock.unlock();
                        //System.out.println("Nonce #" + nonce);
                    }
                }
            } catch(Exception e) {
                System.err.println("Error in miner thread:");
                e.printStackTrace();
            }
        }

        private static void setSendingSolution(boolean ss) {
            synchronized(accSendingSolutionLock) {
                sendingSolution = ss;
            }
        }

        private static boolean getSendingSolution() {
            synchronized(accSendingSolutionLock) {
                return sendingSolution;
            }
        }

        public void sendParams(String block, int work) {
            //System.out.println("sending " + paused);
            {
                lock.lock();
                this.work = work;
                this.addrWBlock = address + block + id;
                lock.unlock();
            }
            //System.out.println("sent");
        }

        public void unpause() {
            synchronized(unpauseReady) {
                unpauseReady.notify();
            }
        }
    }

    static class APIThread extends Thread {
        private static final DecimalFormat format = new DecimalFormat("0.000");

        private String address;
        private int updateMS;
        private final Object ready;

        public APIThread(String address, int updateMS, Object ready) {
            this.address = address;
            this.updateMS = updateMS;
            this.ready = ready;
        }

        @Override
        public void run() {
            String block, oldBlock = "";
            int work, oldWork = 0;
            int balance, oldBalance = 0;
            try {
                block = APICalls.getLastBlock();
                oldBlock = block;
                System.out.println("Beginning on block: " + block);
                KristMiner.block = block;
                for(int i = 0; i < numThreads; i++) {
                    workers.get(i).sendParams(block, KristMiner.work);
                }
                //KristMiner.blockChanged(block);
                work = Integer.parseInt(APICalls.getWork());
                oldWork = work;
                KristMiner.workChanged(work);
                balance = Integer.parseInt(APICalls.getBalance(address));
                oldBalance = balance;
                KristMiner.balanceChanged(balance);
                synchronized(ready) {
                    ready.notify();
                }
                Thread.sleep(updateMS);
                while(true) {
                    System.out.println(block + " " + work + " " + balance + "KST" + " @ " + format.format(KristMiner.getHPS()/1000000) + "MH/s @ " + format.format(KristMiner.getBPM()) + "B/min Done: " + KristMiner.getBlocksDone());
                    long lastTime = System.currentTimeMillis();
                    block = APICalls.getLastBlock();
                    if(block.length() != 12) {
                        block = oldBlock;
                    }
                    if(!block.equals(oldBlock)) {
                        oldBlock = block;
                        KristMiner.blockChanged(block);
                    }
                    try {
                        work = Integer.parseInt(APICalls.getWork());
                    } catch(Exception e) {
                        work = oldWork;
                    }
                    if(work != oldWork) {
                        oldWork = work;
                        KristMiner.workChanged(work);
                    }
                    try {
                        balance = Integer.parseInt(APICalls.getBalance(address));
                    } catch(Exception e) {
                        balance = oldBalance;
                    }
                    if(balance != oldBalance) {
                        oldBalance = balance;
                        KristMiner.balanceChanged(balance);
                    }
                    long sleepTime = updateMS - (System.currentTimeMillis() - lastTime);
                    if(sleepTime > 0)
                        Thread.sleep(sleepTime);
                }
            } catch(Exception e) {
                System.err.println("Error in API poll thread: ");
                e.printStackTrace();
            }
        }
    }

    static List<WorkerThread> workers = new ArrayList<>();
    static APIThread apiThread;
    static int numThreads;
    static String address;
    static boolean paused = false;
    static final Object newBlockReady = new Object();
    static final Object pausedLock = new Object();
    static final Object submitReady = new Object();
    static final Object hashesDoneLock = new Object();
    static final Object blocksDoneLock = new Object();

    static String nonceSubmission = "";
    static long hashesDone = 0;
    static int blocksDone = 0;
    static long startTime = 0;

    static String block;
    static int work;
    static int balance;

    private static void showUsage() {
        System.out.println("Arguments: <address> <threads> [prefix]");
        System.out.println("    address: The krist address to mine for.");
        System.out.println("    threads: The number of mining threads to spawn. In general,\n" +
                           "             this should be less than or equal to the number of\n" +
                           "             CPU cores on your system.");
        System.out.println("    prefix:  A prefix for the submitted nonces. If you run     \n" +
                           "             multiple miners for the same address, this should \n" +
                           "             be unique for each miner.");
        System.exit(0);
    }

    public static void main(String[] args) {
        /*byte[] h = SHA256.digest("asdashfbhejf8".getBytes());
        for(int i = 0; i < h.length; i++) {
            System.out.print((((int)h[i]) + 0) + ";");
        }
        System.out.println("\n" + SHA256.bytesToHex(h));
        System.out.println(SHA256.hashToLong(h));
        System.exit(0);*/
        //System.out.println(SHA256.hashToLong(SHA256.digest("khic3jtob0000000001da55160969".getBytes(StandardCharsets.UTF_8))));
        //System.exit(0);

        if(args.length < 2) showUsage();
        address = args[0];
        try {
            numThreads = Integer.parseInt(args[1]);
        } catch(NumberFormatException e) {
            System.out.println("threads must be a positive integer.");
            showUsage();
        }
        if(numThreads < 1) {
            System.out.println("threads must be a positive integer.");
            showUsage();
        }

        startTime = System.currentTimeMillis();

        System.out.print("Getting sync node... ");
        APICalls.updateSyncNode();
        System.out.println("DONE");

        String prefix = args.length > 2 ? args[2] : "";
        for(int i = 0; i < numThreads; i++) {
            WorkerThread t = new WorkerThread(address, prefix + Integer.toString(i, 16));
            workers.add(t);
        }

        final Object ready = new Object();
        apiThread = new APIThread(address, 1000, ready);
        apiThread.start();
        try {
            synchronized(ready) {
                ready.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for(int i = 0; i < numThreads; i++) {
            workers.get(i).start();
        }

        while(true) {
            synchronized(submitReady) {
                try {
                    submitReady.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            APICalls.submitBlock(address, nonceSubmission);
            try {
                synchronized(newBlockReady) {
                    System.out.println("Waiting for the next block.");
                    newBlockReady.wait();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            WorkerThread.setSendingSolution(false);
            pause(false);
            for(int i = 0; i < numThreads; i++) {
                workers.get(i).unpause();
            }
        }
    }

    public static void addHashesDone(int amt) {
        synchronized(hashesDoneLock) {
            hashesDone += amt;
        }
    }

    public static double getHPS() {
        synchronized(hashesDoneLock) {
            return ((double)hashesDone)/(System.currentTimeMillis() - startTime) * 1000;
        }
    }

    public static void addBlocksDone(int amt) {
        synchronized(blocksDoneLock) {
            blocksDone += amt;
        }
    }

    public static int getBlocksDone() {
        synchronized(blocksDoneLock) {
            return blocksDone;
        }
    }

    public static double getBPM() {
        synchronized(blocksDoneLock) {
            return ((double)blocksDone)/(System.currentTimeMillis() - startTime) * 1000 * 60;
        }
    }

    public static boolean isPaused() {
        synchronized(pausedLock) {
            return paused;
        }
    }

    public static void pause(boolean p) {
        synchronized(pausedLock) {
            paused = p;
        }
    }

    public static void blockChanged(String block) {
        System.out.println("Block changed! New block: " + block);
        KristMiner.block = block;
        if(isPaused()) {
            for(int i = 0; i < numThreads; i++) {
                workers.get(i).sendParams(block, work);
            }
            synchronized(newBlockReady) {
                newBlockReady.notify();
            }
        } else {
            pause(true);
            for(int i = 0; i < numThreads; i++) {
                workers.get(i).sendParams(block, work);
            }
            for(int i = 0; i < numThreads; i++) {
                workers.get(i).unpause();
            }
            pause(false);
        }
    }

    public static void workChanged(int work) {
        System.out.println("Work changed! New work: " + work);
        KristMiner.work = work;
        for(int i = 0; i < numThreads; i++) {
            workers.get(i).sendParams(block, work);
        }
    }

    public static void balanceChanged(int balance) {
        KristMiner.balance = balance;
    }

    public static void submitBlock(String nonce) {
        nonceSubmission = nonce;
        synchronized(submitReady) {
            submitReady.notify();
        }
    }
}