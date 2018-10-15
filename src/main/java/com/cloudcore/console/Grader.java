package com.cloudcore.console;

import com.cloudcore.console.core.CloudCoin;
import com.cloudcore.console.utils.Utils;
import com.cloudcore.console.core.FileSystem;
import com.cloudcore.console.utils.SimpleLogger;

import java.util.ArrayList;

public class Grader {

    /**
     * Categorizes coins into folders based on their pown results.
     */
    public static int gradeDetectedFolder(String folderPath) {
        System.out.println("grading " + folderPath + " + " + FileSystem.DetectedPath);
        ArrayList<CloudCoin> detectedCoins = FileSystem.loadFolderCoins(folderPath + FileSystem.DetectedPath);

        // Apply Grading to all detected coins at once.
        detectedCoins.forEach(detectedCoin -> gradeSimple(detectedCoin, folderPath));

        ArrayList<CloudCoin> coinsBank = new ArrayList<>();
        ArrayList<CloudCoin> coinsFracked = new ArrayList<>();
        ArrayList<CloudCoin> coinsCounterfeit = new ArrayList<>();
        ArrayList<CloudCoin> coinsLost = new ArrayList<>();

        System.out.println("moving coins");
        for (CloudCoin coin : detectedCoins) {
            if (coin.getFolder().equals(folderPath + FileSystem.BankPath)) coinsBank.add(coin);
            else if (coin.getFolder().equals(folderPath + FileSystem.FrackedPath)) coinsFracked.add(coin);
            else if (coin.getFolder().equals(folderPath + FileSystem.CounterfeitPath)) coinsCounterfeit.add(coin);
            else if (coin.getFolder().equals(folderPath + FileSystem.LostPath)) coinsLost.add(coin);
            else {
                System.out.println("folder doesn't match anything: " + coin.getFolder());
            }
            System.out.println("going to move coin " + coin.getSn() + " to " + coin.getFolder());
        }

        System.out.println("Coin Detection finished.");
        System.out.println("Total Passed Coins - " + (coinsBank.size() + coinsFracked.size()) + "");
        System.out.println("Total Failed Coins - " + coinsCounterfeit.size() + "");
        System.out.println("Total Lost Coins - " + coinsLost.size() + "");

        // Move Coins to their respective folders after sort
        FileSystem.moveCoins(coinsBank, folderPath + FileSystem.DetectedPath, folderPath + FileSystem.BankPath);
        FileSystem.moveCoins(coinsFracked, folderPath + FileSystem.DetectedPath, folderPath + FileSystem.FrackedPath);
        FileSystem.moveCoins(coinsCounterfeit, folderPath + FileSystem.DetectedPath, folderPath + FileSystem.CounterfeitPath);
        FileSystem.moveCoins(coinsLost, folderPath + FileSystem.DetectedPath, folderPath + FileSystem.LostPath);

        return coinsBank.size() + coinsFracked.size();
    }

    /**
     * Determines the coin's folder based on a simple grading schematic.
     */
    public static void gradeSimple(CloudCoin coin, String folderPath) {
        if (isPassingSimple(coin.getPown())) {
            if (isFrackedSimple(coin.getPown()))
                coin.setFolder(folderPath + FileSystem.FrackedPath);
            else
                coin.setFolder(folderPath + FileSystem.BankPath);
        }
        else {
            if (isHealthySimple(coin.getPown()))
                coin.setFolder(folderPath + FileSystem.CounterfeitPath);
            else
                coin.setFolder(folderPath + FileSystem.LostPath);
        }
    }

    /**
     * Determines the coin's folder based on a simple grading schematic.
     */
    public static void gradeSimple(CloudCoin coin) {
        if (isPassingSimple(coin.getPown())) {
            if (isFrackedSimple(coin.getPown()))
                coin.setFolder(FileSystem.FrackedFolder);
            else
                coin.setFolder(FileSystem.BankFolder);
        }
        else {
            if (isHealthySimple(coin.getPown()))
                coin.setFolder(FileSystem.CounterfeitFolder);
            else
                coin.setFolder(FileSystem.LostFolder);
        }
    }

    /**
     * Checks to see if the pown result is a passing grade.
     *
     * @return true if the pown result contains more than 20 passing grades.
     */
    public static boolean isPassingSimple(String pown) {
        return (Utils.charCount(pown, 'p') >= 20);
    }

    /**
     * Checks to see if the pown result is fracked.
     *
     * @return true if the pown result contains more than 5 fracked grades.
     */
    public static boolean isFrackedSimple(String pown) {
        return (pown.indexOf('f') != -1);
    }

    /**
     * Checks to see if the pown result is in good health. Unhealthy grades are errors and no-responses.
     *
     * @return true if the pown result contains more than 20 passing or failing grades.
     */
    public static boolean isHealthySimple(String pown) {
        return (Utils.charCount(pown, 'p') + Utils.charCount(pown, 'f') >= 20);
    }

    public static void updateLog(String message) {
        System.out.println(message);
        SimpleLogger.Info(message);
    }
}
