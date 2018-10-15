package com.cloudcore.console.raida;

public class Receipt {


    public String receipt_id;

    public String time;

    public String timezone;

    public String bank_server;

    public int total_authentic;

    public int total_fracked;

    public int total_counterfeit;

    public int total_lost;

    public ReceiptDetail[] rd;
}

