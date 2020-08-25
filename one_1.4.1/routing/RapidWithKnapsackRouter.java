/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.Settings;
import core.SimClock;
import core.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import routing.community.Duration;
import routing.rapid.DelayEntry;
import routing.rapid.DelayTable;
import routing.rapid.MeetingEntry;

/**
 *
 * @author jarkom
 */
public class RapidWithKnapsackRouter implements RoutingDecisionEngineRapidKnapsack {

    private DelayTable delayTable;
    private double timestamp;
    private final UtilityAlgorithm ALGORITHM = UtilityAlgorithm.AVERAGE_DELAY;
    private static final double INFINITY = 99999;
    private static final String knapsackSend = "send";
    private static final String UTILITY = "utility";
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;
//    LinkedList<Message> listSend;
    LinkedList<Integer> listSend01;
    LinkedList<Integer> lengthMsg;
    LinkedList<Double> utilityMsg;
    LinkedList<Message> tempMsg;
    LinkedList<Message> tempMsgTerpilih;
    private double[][] bestSolution;
    //124
    //62499
    private static final int lengthAwal = 124;

    public RapidWithKnapsackRouter(Settings s) {
        super();
        delayTable = null;
        timestamp = 0.0;
    }

    @Override
    public void initi(DTNHost host, List<MessageListener> mListeners) {
        delayTable = new DelayTable(host);
//        System.out.println(host);
    }

    public RapidWithKnapsackRouter(RapidWithKnapsackRouter r) {
        super();
        delayTable = r.delayTable;
        timestamp = r.timestamp;
        startTimestamps = new HashMap<DTNHost, Double>();
        connHistory = new HashMap<DTNHost, List<Duration>>();
//        listSend = new LinkedList<Message>();
        listSend01 = new LinkedList<Integer>();
        lengthMsg = new LinkedList<>();
        utilityMsg = new LinkedList<>();
        tempMsg = new LinkedList<>();
        tempMsgTerpilih = new LinkedList<>();
        bestSolution = r.bestSolution;
    }

    private enum UtilityAlgorithm {
        AVERAGE_DELAY,
        MISSED_DEADLINES,
        MAXIMUM_DELAY;
    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost otherHost) {
        System.out.println(thisHost + " con " + otherHost);
        //ambil waktu awal connect
        this.startTimestamps.put(otherHost, SimClock.getTime());

        //rapid
        timestamp = SimClock.getTime();
        synchronizeDelayTables(otherHost);
        synchronizeMeetingTimes(otherHost);
        updateDelayTableStat(thisHost, otherHost);
//        synchronizeAckedMessageIDs(otherHost);
//        updateAckedMessageIds(thisHost);
        this.delayTable.dummyUpdateConnection1(otherHost);

        if (this.getSyaratKnapsack(thisHost, otherHost)) {
            this.getUtilityMsgToArr(thisHost, otherHost);
            knapsackSend(thisHost, otherHost);
            System.out.println(tempMsgTerpilih);
        }
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost otherHost) {

    }

//    @Override
//    public List<Message> pesanTerpilih() {
////        System.out.println(tempMsgTerpilih);
//        return this.tempMsgTerpilih;
//    }
    @Override
    public void connectionDown(Connection con, DTNHost thisHost, DTNHost otherHost) {
        //rapid
        double times = SimClock.getTime() - this.timestamp;
        this.delayTable.updateConnection(con, times);

        double time = 0;
        if (this.startTimestamps.containsKey(otherHost)) {
            time = this.startTimestamps.get(otherHost);
        }
//        double time = this.startTimestamps.get(otherHost);
        double etime = SimClock.getTime();
        List<Duration> history;
        if (!this.connHistory.containsKey(otherHost)) {
            history = new LinkedList<Duration>();
            this.connHistory.put(otherHost, history);
        } else {
            history = this.connHistory.get(otherHost);
        }

        // add this connection to the list
        if (etime - time > 0) {
            history.add(new Duration(time, etime));
        }

        this.connHistory.put(otherHost, history);
        this.startTimestamps.remove(otherHost);

//        this.listSend.clear();
        this.listSend01.clear();
        this.lengthMsg.clear();
        this.utilityMsg.clear();
        this.tempMsg.clear();
        this.tempMsgTerpilih.clear();
//        System.out.println("temp down "+tempMsgTerpilih);
        bestSolution = null;
        System.out.println(thisHost + " con down " + otherHost);
    }

    @Override
    public void deletedAckMsgInDelayTable(DTNHost thisHost) {
//        deleteAckedMessages(thisHost);
    }

    @Override
    public boolean newMessage(Message m) {
        return true;
    }

    @Override
    public void updateDelayTableEntryNewMessage(Message m, DTNHost thisHost) {
        updateDelayTableEntry(m, thisHost, estimateDelay(m, thisHost, true), SimClock.getTime());
        m.updateProperty(knapsackSend, 0);
        m.updateProperty(UTILITY, estimateDelay(m, thisHost, true));
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        if (m.getTo() == aHost) {
//            this.delayTable.addAckedMessageIds(m.getId());
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        if (thisHost.getRouter().hasMessage(m.getId())) {
            return false;
        } else {
            return m.getTo() != thisHost;
        }
    }

    @Override
    public void updateDelayTableReceiveMsg(Message m, DTNHost from, DTNHost thisHost) {
        double time = SimClock.getTime();
        double delay = estimateDelay(m, thisHost, true);
        updateDelayTableEntry(m, thisHost, delay, time);
        this.getOtherDecisionEngine(from).updateDelayTableEntry(m, thisHost, delay, time);
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        System.out.println("psan " + m);
        if (tempMsgTerpilih != null) {           
            if (tempMsgTerpilih.contains(m)) {
                return true;

            }
        }
        m.updateProperty(UTILITY, getMarginalUtility(m, otherHost, thisHost));
        double utility = (double) m.getProperty(UTILITY);
        System.out.println("util " + utility);
        return utility > 0;
    }

    public boolean getSyaratKnapsack(DTNHost thisHost, DTNHost otherHost) {
        int retriction = this.getRetrictionForSend(thisHost, otherHost);
        int isiBuffer = thisHost.getRouter().getBufferSize() - thisHost.getRouter().getFreeBufferSize();
        return isiBuffer > retriction;
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        return false;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return false;
    }

    @Override
    public RoutingDecisionEngineRapidKnapsack replicate() {
        return new RapidWithKnapsackRouter(this);
    }

    private RapidWithKnapsackRouter getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();
        assert otherRouter instanceof DecisionEngineRapidKnapsackRouter : "This router only works "
                + " with other routers of same type";

        return (RapidWithKnapsackRouter) ((DecisionEngineRapidKnapsackRouter) otherRouter).getDecisionEngine();
    }

    private void synchronizeDelayTables(DTNHost otherHost) {
//        DTNHost otherHost = con.getOtherNode(getHost());
//        RapidRouter otherRouter = (RapidRouter) otherHost.getRouter();
        RapidWithKnapsackRouter otherRouter = this.getOtherDecisionEngine(otherHost);
        DelayEntry delayEntry = null;
        DelayEntry otherDelayEntry = null;

        //synchronize all delay table entries
        for (Map.Entry<String, DelayEntry> entry1 : this.delayTable.getDelayEntries()) {
            Message m = entry1.getValue().getMessage();
            delayEntry = this.delayTable.getDelayEntryByMessageId(m.getId());
            assert (delayEntry != null);
            otherDelayEntry = otherRouter.delayTable.getDelayEntryByMessageId(m.getId());

            if (delayEntry.getDelays() == null) {
                continue;
            }
            //for all hosts check delay entries and create new if they doesn't exist
            //Entry<DTNHost host, Tuple<Double delay, Double lastUpdate>>
            for (Map.Entry<DTNHost, Tuple<Double, Double>> entry : delayEntry.getDelays()) {
                DTNHost thisHost = entry.getKey();
                Double myDelay = entry.getValue().getKey();
                Double myTime = entry.getValue().getValue();

                //create a new host entry if host entry at other host doesn't exist
                if ((otherDelayEntry == null) || (!otherDelayEntry.contains(thisHost))) {
                    //parameters: 
                    //m The message 
                    //thisHost The host which contains a copy of this message
                    //myDelay The estimated delay
                    //myTime The entry was last changed
                    otherRouter.updateDelayTableEntry(m, thisHost, myDelay, myTime);
                } else {
                    //check last update time of other hosts entry and update it 
                    if (otherDelayEntry.isOlderThan(thisHost, delayEntry.getLastUpdate(thisHost))) {
                        //parameters: 
                        //m The message 
                        //thisHost The host which contains a copy of this message
                        //myDelay The estimated delay
                        //myTime The entry was last changed 

                        otherRouter.updateDelayTableEntry(m, thisHost, myDelay, myTime);
                    }

                    if ((otherDelayEntry.isAsOldAs(thisHost, delayEntry.getLastUpdate(thisHost))) && (delayEntry.getDelayOf(thisHost) > otherDelayEntry.getDelayOf(thisHost))) {
                        //parameters: 
                        //m The message 
                        //thisHost The host which contains a copy of this message
                        //myDelay The estimated delay
                        //myTime The entry was last changed 

                        otherRouter.updateDelayTableEntry(m, thisHost, myDelay, myTime);
                    }
                }
            }
        }
    }

    private void synchronizeMeetingTimes(DTNHost otherHost) {
//        DTNHost otherHost = con.getOtherNode(getHost());
//        RapidRouter otherRouter = (RapidRouter) otherHost.getRouter();
        RapidWithKnapsackRouter otherRouter = this.getOtherDecisionEngine(otherHost);
        MeetingEntry meetingEntry = null;
        MeetingEntry otherMeetingEntry = null;

        //synchronize all meeting time entries
        for (int i = 0; i < this.delayTable.getMeetingMatrixDimension(); i++) {
            for (int k = 0; k < this.delayTable.getMeetingMatrixDimension(); k++) {
                meetingEntry = this.delayTable.getMeetingEntry(i, k);

                if (meetingEntry != null) {
                    otherMeetingEntry = otherRouter.delayTable.getMeetingEntry(i, k);
                    //create a new meeting entry if meeting entry at other host doesn't exist
                    if (otherMeetingEntry == null) {
                        otherRouter.delayTable.setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                    } else {
                        //check last update time of other hosts entry and update it 
                        if (otherMeetingEntry.isOlderThan(meetingEntry.getLastUpdate())) {
                            otherRouter.delayTable.setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                        }

                        if ((otherMeetingEntry.isAsOldAs(meetingEntry.getLastUpdate())) && (meetingEntry.getAvgMeetingTime() > otherMeetingEntry.getAvgMeetingTime())) {
                            otherRouter.delayTable.setAvgMeetingTime(i, k, meetingEntry.getAvgMeetingTime(), meetingEntry.getLastUpdate(), meetingEntry.getWeight());
                        }
                    }
                }
            }
        }
    }

    private void updateDelayTableStat(DTNHost thisHost, DTNHost otherHost) {
//        DTNHost thisHost = con.getOtherNode(otherHost);
//        RapidRouter otherRouter = ((RapidRouter) otherHost.getRouter());
        RapidWithKnapsackRouter otherRouter = this.getOtherDecisionEngine(otherHost);
        int from = otherHost.getAddress();

        for (Message m : thisHost.getMessageCollection()) {
            int to = m.getTo().getAddress();
            MeetingEntry entry = otherRouter.getDelayTable().getMeetingEntry(from, to);
            if (entry != null) {
                this.delayTable.getDelayEntryByMessageId(m.getId()).setChanged(true);
            }
        }
    }

    public DelayTable getDelayTable() {
        return delayTable;
    }

    private void synchronizeAckedMessageIDs(DTNHost otherHost) {
//        DTNHost otherHost = con.getOtherNode(getHost());
//        RapidRouter otherRouter = (RapidRouter) otherHost.getRouter();
        RapidWithKnapsackRouter otherRouter = this.getOtherDecisionEngine(otherHost);
        this.delayTable.addAllAckedMessageIds(otherRouter.delayTable.getAllAckedMessageIds());
        otherRouter.delayTable.addAllAckedMessageIds(this.delayTable.getAllAckedMessageIds());

        assert (this.delayTable.getAllAckedMessageIds().equals(otherRouter.delayTable.getAllAckedMessageIds()));
    }

    /**
     * Deletes the messages from the message buffer that are known to be ACKed
     * and also delete the message entries of the ACKed messages in the delay
     * table
     */
    private void deleteAckedMessages(DTNHost thisHost) {
//        DTNHost thisHost = con.getOtherNode(otherHost);
        for (String id : this.delayTable.getAllAckedMessageIds()) {
            if (thisHost.getRouter().hasMessage(id)) {
                //delete messages from the message buffer
                thisHost.deleteMessage(id, true);
            }

            //delete messages from the delay table
            if (this.delayTable.getDelayEntryByMessageId(id) != null) {
                assert (this.delayTable.removeEntry(id) == true);
            }
        }
    }

    /**
     * Delete old message ids from the acked message id set which time to live
     * was passed
     */
    private void updateAckedMessageIds(DTNHost thisHost) {
//        DTNHost thisHost = con.getOtherNode(otherHost);
        ArrayList<String> removableIds = new ArrayList<String>();

        for (String id : this.delayTable.getAllAckedMessageIds()) {
            Message m = thisHost.getRouter().getMessage(id);
            if ((m != null) && (m.getTtl() <= 0)) {
                removableIds.add(id);
            }
        }

        if (removableIds.size() > 0) {
            this.delayTable.removeAllAckedMessageIds(removableIds);
        }
    }

    /**
     * Updates an delay table entry for given message and host
     *
     * @param m The message
     * @param host The host which contains a copy of this message
     * @param delay The estimated delay
     * @param time The entry was last changed
     */
    public void updateDelayTableEntry(Message m, DTNHost thisHost, double delay, double time) {
        DelayEntry delayEntry;

        if ((delayEntry = this.delayTable.getDelayEntryByMessageId(m.getId())) == null) {
            delayEntry = new DelayEntry(m);
            this.delayTable.addEntry(delayEntry);
        }
        assert ((delayEntry != null) && (this.delayTable.getDelayEntryByMessageId(m.getId()) != null));

        if (delayEntry.contains(thisHost)) {
            delayEntry.setHostDelay(thisHost, delay, time);
        } else {
            delayEntry.addHostDelay(thisHost, delay, time);
        }
    }

    /*
	 * Estimate-Delay Algorithm
	 * 
	 * 1) Sort packets in Q in decreasing order of T(i). Let b(i) be the sum of 
	 * sizes of packets that precede i, and B the expected transfer opportunity 
	 * in bytes between X and Z.
	 * 
	 * 2. X by itself requires b(i)/B meetings with Z to deliver i. Compute the 
	 * random variable MX(i) for the corresponding delay as
	 * MX(i) = MXZ + MXZ + . . . b(i)/B times (4)
	 *  
	 * 3. Let X1 , . . . , Xk ⊇ X be the set of nodes possessing a replica of i. 
	 * Estimate remaining time a(i) as  
	 * a(i) = min(MX1(i), . . . , MXk(i))
	 * 
	 * 4. Expected delay D(i) = T(i) + E[a(i)]
     */
    /**
     * Returns the expected delay for a message if this message was sent to a
     * specified host
     *
     * @param msg The message which will be transfered
     * @return the expected packet delay
     */
    private double estimateDelay(Message msg, DTNHost thisHost, boolean recompute) {
        double remainingTime = 0.0;	//a(i): random variable that determines the remaining time to deliver message i
        double packetDelay = 0.0;	//D(i): expected delay of message i

        //if delay table entry for this message doesn't exist or the delay table
        //has changed recompute the delay entry
        if ((recompute) && ((this.delayTable.delayHasChanged(msg.getId())) || (this.delayTable.getDelayEntryByMessageId(msg.getId()).getDelayOf(thisHost) == null) /*|| (delayTable.getEntryByMessageId(msg.getId()).getDelayOf(host) == INFINITY))*/)) {
            // compute remaining time by using metadata
            remainingTime = computeRemainingTime(msg, thisHost);
            packetDelay = Math.min(INFINITY, computePacketDelay(msg, remainingTime));

            //update delay table
            updateDelayTableEntry(msg, thisHost, packetDelay, SimClock.getTime());
        } else {
            packetDelay = this.delayTable.getDelayEntryByMessageId(msg.getId()).getDelayOf(thisHost);
        }

        return packetDelay;
    }

    private double computeRemainingTime(Message msg, DTNHost thisHost) {
        double transferTime = INFINITY;		//MX(i):random variable for corresponding transfer time delay
        double remainingTime = 0.0;		//a(i): random variable that determines the	remaining time to deliver message i

        remainingTime = computeTransferTime(msg, msg.getTo(), thisHost);
        if (this.delayTable.getDelayEntryByMessageId(msg.getId()) != null) {
            //Entry<DTNHost host, Tuple<Double delay, Double lastUpdate>>
            for (Map.Entry<DTNHost, Tuple<Double, Double>> entry : this.delayTable.getDelayEntryByMessageId(msg.getId()).getDelays()) {
                DTNHost host = entry.getKey();
                if (host == thisHost) {
                    continue;	// skip
                }
                transferTime = getOtherDecisionEngine(host).computeTransferTime(msg, msg.getTo(), host); //MXm(i)	with m element of [0...k]
                remainingTime = Math.min(transferTime, remainingTime);	//a(i) = min(MX0(i), MX1(i), ... ,MXk(i)) 
            }
        }
//        System.out.println(remainingTime);
        return remainingTime;
    }

    private double computeTransferTime(Message msg, DTNHost hostDest, DTNHost thisHost) {
        Collection<Message> msgCollection = thisHost.getMessageCollection();
//		List<Tuple<Message, Double>> list=new ArrayList<Tuple<Message,Double>>();
        double transferOpportunity = 0;					//B:    expected transfer opportunity in bytes between X and Z
        double packetsSize = 0.0;						//b(i): sum of sizes of packets that precede the actual message
        double transferTime = INFINITY;					//MX(i):random variable for corresponding transfer time delay
        double meetingTime = 0.0;						//MXZ:  random variable that represent the meeting Time between X and Y

        // packets are already sorted in decreasing order of receive time
        // sorting packets in decreasing order of time since creation is optional
//		// sort packets in decreasing order of time since creation
//		double timeSinceCreation = 0;
//		for (Message m : msgCollection) {
//			timeSinceCreation = SimClock.getTime() - m.getCreationTime();
//			list.add(new Tuple<Message, Double>(m, timeSinceCreation));
//		}
//		Collections.sort(list, new TupleComparator2());
        // compute sum of sizes of packets that precede the actual message 
        for (Message m : msgCollection) {
//		for (Tuple<Message, Double> t : list) {
//			Message m = t.getKey();
            if (m.equals(msg)) {
                continue; //skip
            }
            packetsSize = packetsSize + m.getSize();
        }

        // compute transfer time  
        transferOpportunity = this.delayTable.getAvgTransferOpportunity();	//B in bytes

        MeetingEntry entry = this.delayTable.getIndirectMeetingEntry(thisHost.getAddress(), hostDest.getAddress());
//        System.out.println("entry "+entry);
        // a meeting entry of null means that these hosts have never met -> set transfer time to maximum
        if (entry == null) {
            transferTime = INFINITY;		//MX(i)
        } else {
            meetingTime = entry.getAvgMeetingTime();	//MXZ
            transferTime = meetingTime * Math.ceil(packetsSize / transferOpportunity);	// MX(i) = MXZ * ceil[b(i) / B]
        }
//        System.out.println(transferTime);
        return transferTime;
    }

    private double computePacketDelay(Message msg, double remainingTime) {
        double timeSinceCreation = 0.0;					//T(i): time since creation of message i 
        double expectedRemainingTime = 0.0;				//A(i): expected remaining time E[a(i)]=A(i)
        double packetDelay = 0.0;						//D(i): expected delay of message i

        //TODO E[a(i)]=Erwartungswert von a(i) mit a(i)=remainingTime
        expectedRemainingTime = remainingTime;

        // compute packet delay
        timeSinceCreation = SimClock.getTime() - msg.getCreationTime();
        packetDelay = timeSinceCreation + expectedRemainingTime;

        return packetDelay;
    }

    private double computeUtility(Message msg, DTNHost thisHost, boolean recompute) {
        double utility = 0.0;		// U(i): The utility of the message (packet) i
        double packetDelay = 0.0;	// D(i): The expected delay of message i

        switch (ALGORITHM) {
            // minimizing average delay
            // U(i) = -D(i)
            case AVERAGE_DELAY: {
                packetDelay = estimateDelay(msg, thisHost, recompute);
                utility = -packetDelay;
                break;
            }
            // minimizing missed deadlines
            // L(i)   : The life time of message (packet) i
            // T(i)   : The time since creation of message i
            // a(i)   : A random variable that determines the remaining time to deliver message i
            // P(a(i)): The probability that the message will be delivered within its deadline
            //
            //  	   / P(a(i) < L(i) - T(i)) , L(i) > T(i)
            // U(i) = <|
            // 		   \           0   		   , otherwise
            case MISSED_DEADLINES: {
                //life time in seconds
                double lifeTime = msg.getTtl() * 60;
                //creation time in seconds
                double timeSinceCreation = SimClock.getTime() - msg.getCreationTime();
                double remainingTime = computeRemainingTime(msg, thisHost);
                if (lifeTime > timeSinceCreation) {
                    // compute remaining time by using metadata
                    if (remainingTime < lifeTime - timeSinceCreation) {
                        utility = lifeTime - timeSinceCreation - remainingTime;
                    } else {
                        utility = 0;
                    }
                } else {
                    utility = 0;
                }
                packetDelay = computePacketDelay(msg, remainingTime);
                break;
            }
            // minimizing maximum delay
            // S   : Set of all message in buffer of X
            //
            // 		   / -D(i) , D(i) >= D(j) for all j element of S
            // U(i) = <|
            // 		   \   0   , otherwise
            case MAXIMUM_DELAY: {
                packetDelay = estimateDelay(msg, thisHost, recompute);
                Collection<Message> msgCollection = thisHost.getMessageCollection();
                for (Message m : msgCollection) {
                    if (m.equals(msg)) {
                        continue;	//skip 
                    }
                    if (packetDelay < estimateDelay(m, thisHost, recompute)) {
                        packetDelay = 0;
                        break;
                    }
                }
                utility = -packetDelay;
                break;
            }
            default:
        }

        return utility;
    }

    /**
     * Returns the current marginal utility (MU) value for a host of a specified
     * router
     *
     * @param msg One message of the message queue
     * @param router The router which the message could be send to
     * @param host The host which contains a copy of this message
     * @return the current MU value
     */
    private double getMarginalUtility(Message msg, RapidWithKnapsackRouter router, DTNHost thisHost) {
        double marginalUtility = 0.0;
        double utility = 0.0;			// U(i): The utility of the message (packet) i
        double utilityOld = 0.0;

        assert (this != router);
        utility = router.computeUtility(msg, thisHost, true);
        utilityOld = this.computeUtility(msg, thisHost, true);

        // s(i): The size of message i
        // delta(U(i)) / s(i)
        if ((utilityOld == -INFINITY) && (utility != -INFINITY)) {
            marginalUtility = (Math.abs(utility) / msg.getSize());
        } else if ((utility == -INFINITY) && (utilityOld != -INFINITY)) {
            marginalUtility = (Math.abs(utilityOld) / msg.getSize());
        } else if ((utility == utilityOld) && (utility != -INFINITY)) {
            marginalUtility = (Math.abs(utility) / msg.getSize());
        } else {
            marginalUtility = (utility - utilityOld) / msg.getSize();
        }

        return marginalUtility;
    }

    /**
     * Returns the current marginal utility (MU) value for a connection of two
     * nodes
     *
     * @param msg One message of the message queue
     * @param con The connection
     * @param host The host which contains a copy of this message
     * @return the current MU value
     */
    private double getMarginalUtility(Message msg, DTNHost otherHost, DTNHost thisHost) {
        final RapidWithKnapsackRouter otherRouter = getOtherDecisionEngine(otherHost);

        return getMarginalUtility(msg, otherRouter, thisHost);
    }

    public double getAvgDurations(DTNHost nodes) {
        List<Duration> list = getListDuration(nodes);
        Iterator<Duration> duration = list.iterator();
        double hasil = 0;
        while (duration.hasNext()) {
            Duration d = duration.next();
            hasil += (d.end - d.start);
        }
        int avgDuration = (int) (hasil / list.size());
        if (avgDuration == 0) {
            avgDuration = 1;
        }
        return avgDuration;
    }

    public List<Duration> getListDuration(DTNHost nodes) {
        if (this.connHistory.containsKey(nodes)) {
            return this.connHistory.get(nodes);
        } else {
            List<Duration> d = new LinkedList<>();
            return d;
        }
    }

    public int getTransferSpeed(DTNHost thisHost) {
        return thisHost.getInterfaces().get(0).getTransmitSpeed();
    }

    public void knapsackSend(DTNHost thisHost, DTNHost otherHost) {
        tempMsg.addAll(thisHost.getMessageCollection());
        int jumlahMsg = 0;
        int retriction = 0;
        jumlahMsg = tempMsg.size();
        retriction = this.getRetrictionForSend(thisHost, otherHost);

        bestSolution = new double[jumlahMsg + 1][retriction + 1];

        for (int i = 0; i <= jumlahMsg; i++) {
            for (int length = lengthAwal; length <= retriction; length++) {
                if (i == 0 || length == lengthAwal) {
                    bestSolution[i][length] = 0;
                } else if (lengthMsg.get(i - 1) <= length) {
                    bestSolution[i][length] = Math.max(bestSolution[i - 1][length],
                            utilityMsg.get(i - 1) + bestSolution[i - 1][length - lengthMsg.get(i - 1)]);
                } else {
                    bestSolution[i][length] = bestSolution[i - 1][length];
                }
            }
        }
        int temp = retriction;
        for (int j = jumlahMsg; j >= 1; j--) {
            if (bestSolution[j][temp] > bestSolution[j - 1][temp]) {
//                msg.get(j - 1).updateProperty(knapsackSend, 1);
//                tempMsg.get(j - 1).updateProperty(knapsackSend, 1);
//                listSend01.addFirst((Integer) tempMsg.get(j-1).getProperty(knapsackSend));
                tempMsgTerpilih.add(tempMsg.get(j - 1));
                temp = temp - lengthMsg.get(j - 1);
            }
//            else {
////                msg.get(j - 1).updateProperty(knapsackSend, 0);
//                tempMsg.get(j - 1).updateProperty(knapsackSend, 0);
//                listSend01.addFirst((Integer) tempMsg.get(j-1).getProperty(knapsackSend));
//            }
        }
//        System.out.println(listSend01);
    }

    public int getRetrictionForSend(DTNHost thisHost, DTNHost otherHost) {
        int retriction;
        int avgDuration = (int) this.getAvgDurations(otherHost);

        int tfSpeed = this.getTransferSpeed(thisHost);
        int tfSpeed1 = (int) this.delayTable.getAvgTransferOpportunity();
        retriction = Math.abs(avgDuration * tfSpeed1);

        return retriction;
    }

    public void getUtilityMsgToArr(DTNHost thisHost, DTNHost otherHost) {
        Collection<Message> msgCollection = thisHost.getMessageCollection();
        for (Message m : msgCollection) {
            lengthMsg.add(m.getSize()); //in bit
            m.updateProperty(UTILITY, getMarginalUtility(m, otherHost, thisHost));
            utilityMsg.add(getMarginalUtility(m, otherHost, thisHost));
        }
//        System.out.println(utilityMsg);
//        System.out.println(lengthMsg);
    }
}
