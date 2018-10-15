package com.cloudcore.console.raida;

public class ReceiptDetail {


    public int nn;

    public int sn;

    public String status;

    public String pown;

    public String note;


    public ReceiptDetail() {

    }

    public ReceiptDetail(int nn, int sn, String status, String pown, String note) {
        this.nn = nn;
        this.sn = sn;
        this.status = status;
        this.pown = pown;
        this.note = note;

    }
}
