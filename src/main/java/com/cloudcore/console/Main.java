package com.cloudcore.console;

import com.cloudcore.console.core.CloudCoin;
import com.cloudcore.console.core.FileSystem;
import com.cloudcore.console.core.Stack;
import com.cloudcore.console.raida.MultiDetectResult;
import com.cloudcore.console.raida.RAIDA;
import com.cloudcore.console.raida.Response;
import com.cloudcore.console.raida.ServiceResponse;
import com.cloudcore.console.utils.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Main {


    public static void main(String[] args) {
        setup();

        Scanner reader = new Scanner(System.in);

        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                   CloudCoin Founders Edition                     " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                        Version: 1.0.062-j                        " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "          Used to Authenticate, Store and Payout CloudCoins       " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "      This Software is provided as is with all faults, defects    " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "          and errors, and without warranty of any kind.           " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                Free from the CloudCoin Consortium.               " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);

        while (true) {
            try {
                System.out.println();
                System.out.println("1. Echo RAIDA");
                System.out.println("2. Show CloudCoins");
                System.out.println("3. Import CloudCoins");
                System.out.println("4. Export CloudCoins");
                System.out.println("5. Fix Fracked Coins");
                System.out.println("6. Show Folders");
                System.out.println("7. Help");
                System.out.println("0. Exit");

                reader.hasNext();
                String input = reader.next();

                switch (input) {
                    case "1":
                        EchoRaida();
                        break;
                    case "2":
                        ShowCoins(FileSystem.RootPath);
                        break;
                    case "3":
                        ImportCoins();
                        break;
                    case "4":
                        System.out.println("Export amount:");
                        try {
                            int amount = reader.nextInt();
                            exportStack(amount, FileSystem.RootPath, FileSystem.ExportFolder, null);
                            System.out.println("CloudCoins exported.");
                        } catch (Exception e) {
                            System.out.println("Uncaught exception - " + e.getLocalizedMessage());
                        }
                        break;
                    case "5":
                        FrackFix();
                        break;
                    case "6":
                        Desktop.getDesktop().open(new File(FileSystem.RootPath));
                        break;
                    case "7":
                        helpMessage();
                        break;
                    case "0":
                        return;
                }
            } catch (Exception e) {
                System.out.println("Uncaught exception - " + e.getLocalizedMessage());
                e.printStackTrace();
                break;
            }
        }
    }

    private static void setup() {
        FileSystem.createDirectories();
    }

    public static String EchoRaida() {
        System.out.println("Starting Echo to RAIDA Network 1");
        System.out.println("----------------------------------");

        RAIDA raida = RAIDA.getInstance();
        ArrayList<CompletableFuture<Response>> tasks = raida.getEchoTasks();
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).get();
        } catch (Exception e) {
            System.out.println("RAIDA#PNC:" + e.getLocalizedMessage());
        }

        System.out.println("Ready Count - " + raida.getReadyCount());
        System.out.println("Not Ready Count - " + raida.getNotReadyCount());
        System.out.println(" ---------------------------------------------------------------------------------------------");
        System.out.println(" | Server   | Status | Message                               | Version | Time                |");
        for (int i = 0; i < raida.nodes.length; i++) {
            String idWhitespace = ((i+1) > 9) ? " " : "  ";
            String time = Long.toString(raida.nodes[i].responseTime);
            time = (time.length() < 4) ? "0." + time : time.substring(0, time.length() - 3) + '.' + time.substring(time.length() - 3);

            System.out.println(" ---------------------------------------------------------------------------------------------");
            System.out.println(" | RAIDA " + (i+1) + idWhitespace +
                    "| " + raida.nodes[i].RAIDANodeStatus +
                    " | " + "Execution Time (milliseconds) = " + time +
                    " |  " + ServiceResponse.version +
                    " | " + Utils.getSimpleDate() + " |");
        }
        System.out.println(" ---------------------------------------------------------------------------------------------");

        int readyCount = raida.getReadyCount();

        ServiceResponse response = new ServiceResponse();
        response.bankServer = "localhost";
        response.time = Utils.getDate();
        response.readyCount = Integer.toString(readyCount);
        response.notReadyCount = Integer.toString(raida.getNotReadyCount());
        if (readyCount > 20) {
            response.status = "ready";
            response.message = "The RAIDA is ready for counterfeit detection.";
        } else {
            response.status = "fail";
            response.message = "Not enough RAIDA servers can be contacted to import new coins.";
        }

        new SimpleLogger().LogGoodCall(Utils.createGson().toJson(response));
        return Utils.createGson().toJson(response);
    }

    public static String ShowCoins(String accountFolder) {
        int[] bankTotals = FileUtils.countCoins(accountFolder + FileSystem.BankPath);
        int[] frackedTotals = FileUtils.countCoins(accountFolder + FileSystem.FrackedPath);

        ServiceResponse response = new ServiceResponse();
        response.bankServer = "localhost";

        response.ones = bankTotals[1] + frackedTotals[1];
        response.fives = bankTotals[2] + frackedTotals[2];
        response.twentyfives = bankTotals[3] + frackedTotals[3];
        response.hundreds = bankTotals[4] + frackedTotals[4];
        response.twohundredfifties = bankTotals[5] + frackedTotals[5];

        int total = response.ones + response.fives + response.twentyfives + response.hundreds + response.twohundredfifties;

        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "    Total Coins in Bank:          " + total + "                               " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                 1s         5s         25s       100s       250s  " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "   Perfect:         " + bankTotals[0] + "          " + bankTotals[1] + "          " + bankTotals[2] +
                "          " + bankTotals[3] + "          " + bankTotals[4] + " " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "   Fracked:         " + frackedTotals[0] + "          " + frackedTotals[1] + "          " + frackedTotals[2] +
                "          " + frackedTotals[3] + "          " + frackedTotals[4] + " " + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BLUE_BACKGROUND +
                "                                                                  " + ConsoleColors.RESET);

        response.status = "coins_shown";
        response.message = "Coin totals returned.";
        new SimpleLogger().Log(Utils.createGson().toJson(response));
        return Utils.createGson().toJson(response);
    }

    public static void ImportCoins() {
        Frame frame = new JFrame();
        frame.setVisible(true);
        frame.setExtendedState(JFrame.ICONIFIED);
        frame.setExtendedState(JFrame.NORMAL);

        final JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CloudCoin (.stack)", "stack");
        fc.setFileFilter(filter);
        // Open the dialog using null as parent component if you are outside a
        // Java Swing application otherwise provide the parent comment instead
        int returnVal = fc.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            // Retrieve the selected file
            File file = fc.getSelectedFile();
            depositCoin(file.getAbsolutePath());
        }
    }

    public static void depositCoin(String stack) {
        ArrayList<CloudCoin> coins = FileUtils.loadCloudCoinsFromStack(stack);
        byte[] stackBytes = stack.getBytes(StandardCharsets.UTF_8);
        String accountFolder = FileSystem.RootPath;

        try {
            if (coins == null || coins.size() == 0) {
                Path path = Paths.get(accountFolder + FileSystem.TrashPath + new Date().toString());
                Files.createDirectories(path.getParent());
                Files.write(path, stackBytes, StandardOpenOption.CREATE_NEW);
            } else {
                FileSystem.saveCoinsSingleStack(coins, accountFolder + FileSystem.SuspectPath + CoinUtils.generateFilename(coins.get(0)) + ".stack");
            }
        } catch (IOException e) {
            e.printStackTrace();
            coins = null;
        }

        if (coins == null || coins.size() == 0) {
            /*response.status = "error";
            response.message = "Error: coins were valid.";
            response.receipt = "";
            response.account = getParameterForLogging(account);
            response.pk = getParameterForLogging(key);
            response.stack = getParameterForLogging(stack);
            new SimpleLogger().LogBadCall(Utils.createGson().toJson(response));
            return Utils.createGson().toJson(response);*/
            return;
        }

        System.out.println("start time: " + System.currentTimeMillis());
        MultiDetectResult detectResponse = detect(accountFolder);
        System.out.println("end time: " + System.currentTimeMillis());
        if (detectResponse != null && detectResponse.receipt != null) {
            /*response.status = "importing";
            response.message = "The stack file has been imported and detection will begin automatically so long as " +
                    "they are not already in bank. Please check your receipt.";
            response.receipt = detectResponse.receipt;
            new SimpleLogger().LogGoodCall(Utils.createGson().toJson(response));
            return Utils.createGson().toJson(response);*/
            return;
        }

        if (detectResponse != null) {
            /*response.message = "The stack files are already in the bank.";
            response.status = "complete";
            new SimpleLogger().LogGoodCall(Utils.createGson().toJson(response));
            return Utils.createGson().toJson(response);*/
            return;
        } else {
            /*response.message = "There was a server error, try again later.";
            response.status = "error";
            response.account = getParameterForLogging(account);
            response.pk = getParameterForLogging(key);
            response.stack = getParameterForLogging(stack);
            new SimpleLogger().LogBadCall(Utils.createGson().toJson(response));
            return Utils.createGson().toJson(response);*/
            return;
        }
    }

    private static MultiDetectResult detect(String accountFolder) {
        String receiptFileName = FileUtils.randomString(16);
        MultiDetectResult result = multi_detect(accountFolder, receiptFileName);

        if (result != null && result.receipt != null)
            result.coinsPassed = Grader.gradeDetectedFolder(accountFolder);

        return result;
    }

    private static MultiDetectResult multi_detect(String accountPath, String receiptFileName) {
        RAIDA raida = RAIDA.getInstance();

        try {
            return raida.detectMulti(receiptFileName, accountPath).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String exportStack(int amount, String accountFolder, String targetFolder, ServiceResponse response) {
        if (response == null)
            response = new ServiceResponse();

        int[] bankTotals = FileUtils.countCoins(accountFolder + FileSystem.BankPath);
        int[] frackedTotals = FileUtils.countCoins(accountFolder + FileSystem.FrackedPath);
        int[] totals = FileUtils.countCoins(accountFolder);
        bankTotals[0] += totals[0];
        bankTotals[1] += totals[1];
        bankTotals[2] += totals[2];
        bankTotals[3] += totals[3];
        bankTotals[4] += totals[4];
        bankTotals[5] += totals[5];

        int exp_1 = 0;
        int exp_5 = 0;
        int exp_25 = 0;
        int exp_100 = 0;
        int exp_250 = 0;
        if (amount >= 250 && bankTotals[5] + frackedTotals[5] > 0) {
            exp_250 = ((amount / 250) < (bankTotals[5] + frackedTotals[5])) ? (amount / 250) : (bankTotals[5] + frackedTotals[5]);
            amount -= (exp_250 * 250);
        }
        if (amount >= 100 && bankTotals[4] + frackedTotals[4] > 0) {
            exp_100 = ((amount / 100) < (bankTotals[4] + frackedTotals[4])) ? (amount / 100) : (bankTotals[4] + frackedTotals[4]);
            amount -= (exp_100 * 100);
        }
        if (amount >= 25 && bankTotals[3] + frackedTotals[3] > 0) {
            exp_25 = ((amount / 25) < (bankTotals[3] + frackedTotals[3])) ? (amount / 25) : (bankTotals[3] + frackedTotals[3]);
            amount -= (exp_25 * 25);
        }
        if (amount >= 5 && bankTotals[2] + frackedTotals[2] > 0) {
            exp_5 = ((amount / 5) < (bankTotals[2] + frackedTotals[2])) ? (amount / 5) : (bankTotals[2] + frackedTotals[2]);
            amount -= (exp_5 * 5);
        }
        if (amount >= 1 && bankTotals[1] + frackedTotals[1] > 0) {
            exp_1 = (amount < (bankTotals[1] + frackedTotals[1])) ? amount : (bankTotals[1] + frackedTotals[1]);
            amount -= (exp_1);
        }

        // Get the CloudCoins that will be used for the export.
        int totalSaved = exp_1 + (exp_5 * 5) + (exp_25 * 25) + (exp_100 * 100) + (exp_250 * 250);
        ArrayList<CloudCoin> totalCoins = FileSystem.loadFolderCoins(accountFolder + FileSystem.BankPath);
        totalCoins.addAll(FileSystem.loadFolderCoins(accountFolder + FileSystem.FrackedPath));
        totalCoins.addAll(FileSystem.loadFolderCoins(accountFolder));

        ArrayList<CloudCoin> onesToExport = new ArrayList<>();
        ArrayList<CloudCoin> fivesToExport = new ArrayList<>();
        ArrayList<CloudCoin> qtrToExport = new ArrayList<>();
        ArrayList<CloudCoin> hundredsToExport = new ArrayList<>();
        ArrayList<CloudCoin> twoFiftiesToExport = new ArrayList<>();

        for (int i = 0, totalCoinsSize = totalCoins.size(); i < totalCoinsSize; i++) {
            CloudCoin coin = totalCoins.get(i);
            int denomination = CoinUtils.getDenomination(coin);
            if (denomination == 1) {
                if (exp_1-- > 0) onesToExport.add(coin);
                else exp_1 = 0;
            } else if (denomination == 5) {
                if (exp_5-- > 0) fivesToExport.add(coin);
                else exp_5 = 0;
            } else if (denomination == 25) {
                if (exp_25-- > 0) qtrToExport.add(coin);
                else exp_25 = 0;
            } else if (denomination == 100) {
                if (exp_100-- > 0) hundredsToExport.add(coin);
                else exp_100 = 0;
            } else if (denomination == 250) {
                if (exp_250-- > 0) twoFiftiesToExport.add(coin);
                else exp_250 = 0;
            }
        }

        if (onesToExport.size() < exp_1 || fivesToExport.size() < exp_5 || qtrToExport.size() < exp_25
                || hundredsToExport.size() < exp_100 || twoFiftiesToExport.size() < exp_250) {
            response.message = "Request Error: Not enough CloudCoins for export.";
            System.out.println(response.message);
            return Utils.createGson().toJson(response);
        }

        ArrayList<CloudCoin> exportCoins = new ArrayList<>();
        exportCoins.addAll(onesToExport);
        exportCoins.addAll(fivesToExport);
        exportCoins.addAll(qtrToExport);
        exportCoins.addAll(hundredsToExport);
        exportCoins.addAll(twoFiftiesToExport);

        if (targetFolder == null) {
            Stack stack = new Stack(exportCoins);
            return Utils.createGson().toJson(stack);
        } else {
            String filename = (totalSaved + ".CloudCoin");
            filename = FileUtils.ensureFilenameUnique(filename, ".stack", targetFolder);
            String stack = FileSystem.saveCoinsSingleStack(exportCoins, targetFolder + filename);
            if (stack != null) {
                FileSystem.removeCoins(exportCoins, accountFolder + FileSystem.BankPath);
                FileSystem.removeCoins(exportCoins, accountFolder + FileSystem.FrackedPath);
                return stack;
            }
        }

        response.message = "Request Error: Could not withdraw CloudCoins.";
        System.out.println(response.message);
        new SimpleLogger().Log(Utils.createGson().toJson(response));
        return Utils.createGson().toJson(response);
    }

    private static void FrackFix() {
        FrackFixer frackFixer = new FrackFixer();
        FrackFixer.logger = new SimpleLogger();
        frackFixer.continueExecution = true;
        frackFixer.isFixing = true;
        frackFixer.fixAll();
        frackFixer.isFixing = false;
    }

    public static void helpMessage() {
        System.out.println("\nCustomer Service:\n" +
                "Open 9am to 11pm California Time(PST).\n" +
                "1 (530) 500 - 2646\n" +
                "CloudCoin.HelpDesk@gmail.com(unsecure)\n" +
                "CloudCoin.HelpDesk@Protonmail.com(secure if you get a free encrypted email account at ProtonMail.com)");
    }
}
