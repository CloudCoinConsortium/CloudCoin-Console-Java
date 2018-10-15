package com.cloudcore.console.raida;

import com.cloudcore.console.Grader;
import com.cloudcore.console.core.CloudCoin;
import com.cloudcore.console.core.Config;
import com.cloudcore.console.core.FileSystem;
import com.cloudcore.console.utils.CoinUtils;
import com.cloudcore.console.utils.FileUtils;
import com.cloudcore.console.utils.SimpleLogger;
import com.cloudcore.console.utils.Utils;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RAIDA {


    /* Fields */

    public static SimpleLogger logger;

    public static RAIDA mainNetwork;
    public static ArrayList<RAIDA> networks = new ArrayList<>();

    public Node[] nodes = new Node[Config.nodeCount];

    public MultiDetectRequest multiRequest;
    public ArrayList<CloudCoin> coins;
    public Response[] responseArray = new Response[Config.nodeCount];

    public int networkNumber = 1;


    /* Constructors */

    private RAIDA() {
        for (int i = 0; i < Config.nodeCount; i++) {
            nodes[i] = new Node(i + 1);
        }
    }

    private RAIDA(Network network) {
        nodes = new Node[network.raida.length];
        this.networkNumber = network.nn;
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Node(i + 1, network.raida[i]);
        }
    }

    // Return Main RAIDA Network populated with default Nodes Addresses(Network 1)
    public static RAIDA getInstance() {
        if (mainNetwork != null)
            return mainNetwork;
        else {
            mainNetwork = new RAIDA();
            return mainNetwork;
        }
    }

    public static RAIDA getInstance(Network network) {
        return new RAIDA(network);
    }

    public ArrayList<CompletableFuture<Response>> getEchoTasks() {
        ArrayList<CompletableFuture<Response>> echoTasks = new ArrayList<>();
        for (int i = 0; i < nodes.length; i++) {
            echoTasks.add(nodes[i].echo());
        }
        return echoTasks;
    }

    // This method was introduced breaking the previously used Singleton pattern.
    // This was done in order to support multiple networks concurrently.
    // We can now have multiple RAIDA objects each containing different networks
    // RAIDA details are read from Directory URL first.
    // In case of failure, it falls back to a file on the file system
    public static ArrayList<RAIDA> instantiate() {
        String nodesJson = "";
        networks.clear();

        try {
            nodesJson = Utils.getHtmlFromURL(Config.URL_DIRECTORY);
        } catch (Exception e) {
            System.out.println(": " + e.getLocalizedMessage());
            e.printStackTrace();
            if (!Files.exists(Paths.get("directory.json"))) {
                System.out.println("RAIDA instantiation failed. No Directory found on server or local path");
                System.exit(-1);
                return null;
            }
            try {
                nodesJson = new String(Files.readAllBytes(Paths.get(Paths.get("").toAbsolutePath().toString()
                        + File.separator + "directory.json")));
            } catch (IOException e1) {
                System.out.println("| " + e.getLocalizedMessage());
                e1.printStackTrace();
            }
        }

        try {
            Gson gson = Utils.createGson();
            RAIDADirectory dir = gson.fromJson(nodesJson, RAIDADirectory.class);

            for (Network network : dir.networks) {
                System.out.println("Available Networks: " + network.raida[0].urls[0].url + " , " + network.nn);
                networks.add(RAIDA.getInstance(network));
            }
        } catch (Exception e) {
            System.out.println("RAIDA instantiation failed. No Directory found on server or local path");
            e.printStackTrace();
            System.exit(-1);
        }

        if (networks == null || networks.size() == 0) {
            System.out.println("RAIDA instantiation failed. No Directory found on server or local path");
            System.exit(-1);
            return null;
        }
        return networks;
    }

    public void getTickets(int[] triad, String[] ans, int nn, int sn, int denomination, int milliSecondsToTimeOut) {
        CompletableFuture task = getTicket(0, triad[0], nn, sn, ans[0], denomination);
        CompletableFuture task2 = getTicket(1, triad[1], nn, sn, ans[1], denomination);
        CompletableFuture task3 = getTicket(2, triad[2], nn, sn, ans[2], denomination);
        CompletableFuture[] taskList = new CompletableFuture[]{task, task2, task3};

        try {
            CompletableFuture.allOf(taskList).get(milliSecondsToTimeOut, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture getTicket(int i, int raidaID, int nn, int sn, String an, int d) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                responseArray[raidaID] = nodes[raidaID].getTicket(nn, sn, an, d).get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public static CompletableFuture<Object> processNetworkCoins(int NetworkNumber) {
        return processNetworkCoins(NetworkNumber, true);
    }

    public static CompletableFuture<Object> processNetworkCoins(int NetworkNumber, boolean ChangeANS) {
        return CompletableFuture.supplyAsync(() -> {
            FileSystem.loadFileSystem();
            FileSystem.detectPreProcessing();

            System.out.println("Getting coins...");
            ArrayList<CloudCoin> folderSuspectCoins = FileSystem.loadFolderCoins(FileSystem.SuspectFolder);
            ArrayList<CloudCoin> suspectCoins = new ArrayList<>();
            for (CloudCoin oldPredetectCoin : folderSuspectCoins) {
                if (NetworkNumber == oldPredetectCoin.getNn()) {
                    suspectCoins.add(oldPredetectCoin);
                }
            }

            FileSystem.predetectCoins = suspectCoins;

            if (suspectCoins.size() == 0) {
                System.out.println("No coins in Suspect folder! Finishing...");
                return null;
            }

            System.out.println("Getting network...");
            RAIDA raida = null;
            for (RAIDA network : RAIDA.networks) {
                if (network != null && NetworkNumber == network.networkNumber) {
                    raida = network;
                    break;
                }
            }

            if (raida == null)
                return null;

            // Process Coins in Lots of 200. Can be changed from Config File
            int LotCount = suspectCoins.size() / Config.multiDetectLoad;
            if (suspectCoins.size() % Config.multiDetectLoad > 0)
                LotCount++;

            int coinCount = 0;
            for (int i = 0; i < LotCount; i++) {
                ArrayList<CloudCoin> coins = new ArrayList<>();
                try { // Pick up to 200 Coins and send them to RAIDA
                    coins = new ArrayList<>(suspectCoins.subList(i * Config.multiDetectLoad, Math.min(suspectCoins.size(), 200)));
                    raida.coins = coins;
                } catch (Exception e) {
                    System.out.println(":" + e.getLocalizedMessage());
                    e.printStackTrace();
                }
                ArrayList<CompletableFuture<Node.MultiDetectResponse>> tasks = raida.getMultiDetectTasks(raida.coins, ChangeANS);
                try {
                    try {
                        System.out.println("Waiting for futures...");
                        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).get();
                    } catch (Exception e) {
                        System.out.println("RAIDA#PNC:" + e.getLocalizedMessage());
                    }

                    for (int j = 0; j < coins.size(); j++) {
                        CloudCoin coin = coins.get(j);
                        StringBuilder pownString = new StringBuilder();
                        coin.setPown("");
                        for (int k = 0; k < Config.nodeCount; k++) {
                            pownString.append(raida.nodes[k].multiResponse.responses[j].outcome, 0, 1);
                        }
                        coin.setPown(pownString.toString());
                        coinCount++;
                        //CoinUtils.setAnsToPans(coin); // TODO: COMMENTED OUT FOR TESTING, DO NOT ADD TO GITHUB
                        FileSystem.moveCoin(coin, FileSystem.SuspectFolder, FileSystem.DetectedFolder, ".stack");

                        updateLog("No. " + coinCount + ". Coin Detected. sn - " + coin.getSn() + ". Pass Count - " + CoinUtils.getPassCount(coin) +
                                ". Fail Count  - " + CoinUtils.getFailCount(coin) + ". Result - " + CoinUtils.getDetectionResult(coin) + "." + coin.getPown());
                        System.out.println("Coin Detected. sn - " + coin.getSn() + ". Pass Count - " + CoinUtils.getPassCount(coin) +
                                ". Fail Count  - " + CoinUtils.getFailCount(coin) + ". Result - " + CoinUtils.getDetectionResult(coin));
                    }
                } catch (Exception e) {
                    System.out.println("RAIDA#PNC: " + e.getLocalizedMessage());
                }
            }

            return null;
        });
    }

    public ArrayList<CompletableFuture<Node.MultiDetectResponse>> getMultiDetectTasks(ArrayList<CloudCoin> coins, boolean changeANs) {
        this.coins = coins;

        int[] nns = new int[coins.size()];
        int[] sns = new int[coins.size()];

        String[][] ans = new String[Config.nodeCount][];
        String[][] pans = new String[Config.nodeCount][];

        int[] dens = new int[coins.size()]; // Denominations
        ArrayList<CompletableFuture<Node.MultiDetectResponse>> detectTasks = new ArrayList<>(); // Stripe the coins

        for (int i = 0; i < coins.size(); i++) {
            CloudCoin coin = coins.get(i);
            if (changeANs)
                CoinUtils.generatePAN(coin);
            else
                CoinUtils.setAnsToPans(coin);
            nns[i] = coin.getNn();
            sns[i] = coin.getSn();
            dens[i] = CoinUtils.getDenomination(coin);
            System.out.println(coin.toString());
        }

        try {
            multiRequest = new MultiDetectRequest();
            multiRequest.timeout = Config.milliSecondsToTimeOutDetect;
            for (int nodeNumber = 0; nodeNumber < Config.nodeCount; nodeNumber++) {
                ans[nodeNumber] = new String[coins.size()];
                pans[nodeNumber] = new String[coins.size()];

                for (int i = 0; i < coins.size(); i++) {
                    ans[nodeNumber][i] = coins.get(i).getAn().get(nodeNumber);
                    pans[nodeNumber][i] = coins.get(i).pan[nodeNumber];
                }
                multiRequest.an[nodeNumber] = ans[nodeNumber];
                multiRequest.pan[nodeNumber] = pans[nodeNumber];
                multiRequest.nn = nns;
                multiRequest.sn = sns;
                multiRequest.d = dens;
            }
        } catch (Exception e) {
            System.out.println("/0" + e.getLocalizedMessage());
            e.printStackTrace();
        }

        try {
            for (int nodeNumber = 0; nodeNumber < Config.nodeCount; nodeNumber++) {
                detectTasks.add(nodes[nodeNumber].MultiDetect());
            }
        } catch (Exception e) {
            System.out.println("/1" + e.getLocalizedMessage());
            e.printStackTrace();
        }

        return detectTasks;
    }

    public CompletableFuture<MultiDetectResult> detectMulti(String receiptFilename, String folderPath) {
        return CompletableFuture.supplyAsync(() -> {
            boolean stillHaveSuspect = true;
            int coinNames = 0;

            MultiDetectResult result = new MultiDetectResult();

            while (stillHaveSuspect) {
                String[] suspectFileNames = FileUtils.selectFileNamesInFolder(folderPath + FileSystem.SuspectPath);

                for (String suspectFileName : suspectFileNames) {
                    try {
                        if (Files.exists(Paths.get(folderPath + FileSystem.BankPath + suspectFileName)) ||
                                Files.exists(Paths.get(folderPath + FileSystem.FrackedPath + suspectFileName)) ||
                                Files.exists(Paths.get(folderPath + FileSystem.DetectedPath + suspectFileName)))
                            FileSystem.moveFile(suspectFileName, folderPath + FileSystem.SuspectPath, folderPath + FileSystem.TrashPath, false);
                    } catch (SecurityException ex) {
                        ex.printStackTrace();
                    }
                }

                suspectFileNames = FileUtils.selectFileNamesInFolder(folderPath + FileSystem.SuspectPath);
                if (suspectFileNames.length == 0)
                    return result;

                if (result.cloudCoins.size() == 0)
                    result.cloudCoins = new ArrayList<>(suspectFileNames.length);

                coinNames = Math.min(suspectFileNames.length, Config.multiDetectLoad);
                if (suspectFileNames.length <= Config.multiDetectLoad)
                    stillHaveSuspect = false;

                //System.out.println("Authenticating " + coinNames + " coins out of " + suspectFileNames.length + ". More: " + stillHaveSuspect);

                ArrayList<CloudCoin> coins = new ArrayList<>(coinNames);
                Receipt receipt = createReceipt(coinNames, receiptFilename);

                for (int i = 0; i < coinNames; i++) {
                    //System.out.println("md dm: file: " + folderPath + FileSystem.SuspectPath + suspectFileNames[i]);
                    coins.add(FileUtils.loadCloudCoinsFromStack(folderPath + FileSystem.SuspectPath + suspectFileNames[i]).get(0));
                    //System.out.println("  Now scanning coin " + (i + 1) + " of " + suspectFileNames.length + " for counterfeit. SN 0:" + coins.get(i).getSn() + ", Denomination: " + CoinUtils.getDenomination(coins.get(i)));
                    ReceiptDetail detail = new ReceiptDetail();
                    detail.sn = coins.get(i).getSn();
                    detail.nn = coins.get(i).getNn();
                    detail.status = "suspect";
                    detail.pown = "uuuuuuuuuuuuuuuuuuuuuuuuu";
                    detail.note = "Waiting";
                    receipt.rd[i] = detail;
                }

                try {
                    String json = Utils.createGson().toJson(receipt);
                    Files.write(Paths.get(folderPath + FileSystem.ReceiptsPath + receiptFilename + ".json"), json.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                RAIDA raida = RAIDA.getInstance();
                int[] nns = new int[coins.size()];
                int[] sns = new int[coins.size()];
                String[][] ans = new String[Config.nodeCount][];
                String[][] pans = new String[Config.nodeCount][];

                int[] dens = new int[coins.size()]; // Denominations

                for (int i = 0; i < coins.size(); i++) {
                    CloudCoin coin = coins.get(i);
                    CoinUtils.generatePAN(coin);
                    nns[i] = coin.getNn();
                    sns[i] = coin.getSn();
                    dens[i] = CoinUtils.getDenomination(coin);
                }
                try {
                    raida.multiRequest = new MultiDetectRequest();
                    raida.multiRequest.timeout = Config.milliSecondsToTimeOutDetect;
                    for (int nodeNumber = 0; nodeNumber < Config.nodeCount; nodeNumber++) {
                        ans[nodeNumber] = new String[coins.size()];
                        pans[nodeNumber] = new String[coins.size()];

                        for (int i = 0; i < coins.size(); i++) {
                            ans[nodeNumber][i] = coins.get(i).getAn().get(nodeNumber);
                            pans[nodeNumber][i] = coins.get(i).pan[nodeNumber];
                        }
                        raida.multiRequest.an[nodeNumber] = ans[nodeNumber];
                        raida.multiRequest.pan[nodeNumber] = pans[nodeNumber];
                        raida.multiRequest.nn = nns;
                        raida.multiRequest.sn = sns;
                        raida.multiRequest.d = dens;
                    }
                } catch (Exception e) {
                    System.out.println("/0" + e.getLocalizedMessage());
                    e.printStackTrace();
                }

                ArrayList<CompletableFuture<Node.MultiDetectResponse>> detectTasks = new ArrayList<>();
                for (int nodeNumber = 0; nodeNumber < Config.nodeCount; nodeNumber++) {
                    detectTasks.add(raida.nodes[nodeNumber].MultiDetect());
                }

                try {
                    System.out.println("Waiting for futures...");
                    CompletableFuture.allOf(detectTasks.toArray(new CompletableFuture[0])).get();
                } catch (InterruptedException | ExecutionException e) {
                    System.out.println("RAIDA#PNC#NODES: " + e.getLocalizedMessage());
                }

                try {
                    for (int j = 0; j < coins.size(); j++) {
                        CloudCoin coin = coins.get(j);
                        coin.setFolder(folderPath + FileSystem.DetectedPath);
                        StringBuilder pownString = new StringBuilder();
                        coin.setPown("");
                        for (int k = 0; k < Config.nodeCount; k++)
                            pownString.append(raida.nodes[k].multiResponse.responses[j].outcome, 0, 1);
                        coin.setPown(pownString.toString());
                        //CoinUtils.setAnsToPans(coin); // TODO: COMMENTED OUT FOR TESTING
                        FileSystem.saveCoin(coin, folderPath + FileSystem.DetectedPath);
                    }
                    //FileSystem.saveCoinsSingleStack(coins, folderPath + FileSystem.DetectedPath);
                    FileSystem.removeCoins(coins, folderPath + FileSystem.SuspectPath);

                    for (int i = 0; i < coins.size(); i++) {
                        CloudCoin coin = coins.get(i);
                        if (Grader.isPassingSimple(coin.getPown())) {
                            if (Grader.isFrackedSimple(coin.getPown()))
                                receipt.total_fracked++;
                            else
                                receipt.total_authentic++;
                        }
                        else {
                            if (Grader.isHealthySimple(coin.getPown()))
                                receipt.total_counterfeit++;
                            else
                                receipt.total_lost++;
                        }

                        ReceiptDetail detail = new ReceiptDetail();
                        detail.sn = coin.getSn();
                        detail.nn = coin.getNn();
                        detail.status = CoinUtils.getDetectionResult(coin);
                        detail.pown = coin.getPown();
                        detail.note = "Deposit complete";
                        receipt.rd[i] = detail;
                    }

                    try {
                        String json = Utils.createGson().toJson(receipt);
                        Files.write(Paths.get(folderPath + FileSystem.ReceiptsPath + receiptFilename + ".json"), json.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    result.receipt = receiptFilename;
                    result.cloudCoins.addAll(coins);
                } catch (Exception e) {
                    System.out.println("RAIDA#PNC: " + e.getLocalizedMessage());
                }
            }
            return result;
        });
    }

    Receipt createReceipt(int length, String id) {
        Receipt receipt = new Receipt();
        receipt.time = new SimpleDateFormat("yyyy-MM-dd h:mm:ss").format(new Date());
        receipt.timezone = "UTC" + ZoneOffset.systemDefault().getRules().getOffset(Instant.now()).toString();
        receipt.bank_server = "localhost";
        receipt.total_authentic = 0;
        receipt.total_fracked = 0;
        receipt.total_counterfeit = 0;
        receipt.total_lost = 0;
        receipt.receipt_id = id;
        receipt.rd = new ReceiptDetail[length];
        return receipt;
    }

    public int getReadyCount() {
        int counter = 0;
        for (Node node : nodes) {
            if (Node.NodeStatus.Ready == node.RAIDANodeStatus)
                counter++;
        }
        return counter;
    }
    public int getNotReadyCount() {
        int counter = 0;
        for (Node node : nodes) {
            if (Node.NodeStatus.NotReady == node.RAIDANodeStatus)
                counter++;
        }
        return counter;
    }

    public static void updateLog(String message) {
        System.out.println(message);
        logger.Info(message);
    }
}

