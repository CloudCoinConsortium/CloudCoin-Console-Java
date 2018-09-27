package com.cloudcore.console;

import com.cloudcore.console.core.FileSystem;
import com.cloudcore.console.utils.SimpleLogger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {


    public static SimpleLogger logger;


    public static void main(String[] args) {
        try {
            setup();

            Grader grader = new Grader();
            grader.logger = logger;

            System.out.println("Grading coins...");
            grader.grade();
        } catch (Exception e) {
            System.out.println("Uncaught exception - " + e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    private static void setup() {
        FileSystem.createDirectories();

        logger = new SimpleLogger(FileSystem.LogsFolder + "logs" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")).toLowerCase() + ".log");
    }
}
