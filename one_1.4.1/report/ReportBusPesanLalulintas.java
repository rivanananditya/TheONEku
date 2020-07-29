/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package report;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Provides the inter-contact duration data for making probability density
 * function
 *
 * @author Gregorius Bima, Sanata Dharma University
 */
public class ReportBusPesanLalulintas extends Report implements MessageListener {

    public static final String NODE_ID = "ToNodeID";
    private String pesan;
    private int nrofDropped;
    private int nrofRemoved;
    private int nrofStarted;
    private int nrofAborted;
    private int nrofRelayed;
    private int nrofDelivered;
    private int nrofCreated;
    private Map<String, Double> creationTimes;
    private List<Double> latencies;

    public ReportBusPesanLalulintas() {
        init();
        Settings s = getSettings();
        if (s.contains(NODE_ID)) {
            pesan = s.getSetting(NODE_ID);
        } else {
            pesan = "L";
        }

    }

    @Override
    public void init() {
        super.init();
        this.nrofCreated = 0;
        this.nrofDelivered = 0;
        this.nrofDropped = 0;
        this.nrofRemoved = 0;
        this.nrofStarted = 0;
        this.nrofAborted = 0;
        this.nrofRelayed = 0;
        latencies = new LinkedList<>();
        creationTimes = new HashMap<>();
    }

    @Override
    public void done() {
        double deliveryProb = 0; // delivery probability
        double responseProb = 0; // request-response success probability
//        double overHead = Double.NaN;	// overhead ratio

        if (this.nrofCreated > 0) {
            deliveryProb = (1.0 * this.nrofDelivered) / this.nrofCreated;
        }
        String output = "Pesan" + pesan + "\n";

        output += "nrofCreated = " + this.nrofCreated + "\n";
        output += "nrofDropped = " + this.nrofDropped + "\n";
        output += "nrofRemoved = " + this.nrofRemoved + "\n";
        output += "nrofStarted = " + this.nrofStarted + "\n";
        output += "nrofAborted = " + this.nrofAborted + "\n";
        output += "nrofRelayed = " + this.nrofRelayed + "\n";
        output += "ndelivered = " + this.nrofDelivered + "\n";
        output += "deliveryProb = " + deliveryProb + "\n";
        output += "Latency = " + getAverage(latencies);
        write(output);
        super.done();
    }

    @Override
    public void newMessage(Message m) {
        if (String.valueOf(m.getId().charAt(0)).equals(pesan)) {
            this.creationTimes.put(m.getId(), getSimTime());
            this.nrofCreated++;
        }

    }

    @Override
    public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
        if (String.valueOf(m.getId().charAt(0)).equals(pesan)) {
            this.nrofStarted++;
        }
    }

    @Override
    public void messageDeleted(Message m, DTNHost where, boolean dropped) {
        if (String.valueOf(m.getId().charAt(0)).equals(pesan)) {
            if (dropped) {
                this.nrofDropped++;
            } else {
                this.nrofRemoved++;
            }
        }

    }

    @Override
    public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
        if (String.valueOf(m.getId().charAt(0)).equals(pesan)) {
            this.nrofAborted++;
        }
    }

    @Override
    public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean finalTarget) {

        if (String.valueOf(m.getId().charAt(0)).equals(pesan)) {   
            this.nrofRelayed++;
        }
        
        if (finalTarget) {
            //            System.out.println(String.valueOf(m.getId().charAt(0)) + "\t" + pesan + "\t" + String.valueOf(m.getId().charAt(0)).equals(pesan));
//            System.out.println();
            if (String.valueOf(m.getId().charAt(0)).equals(pesan)) {
                this.nrofDelivered++;
                this.latencies.add(getSimTime() - this.creationTimes.get(m.getId()));
            }
        }

    }

}
