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
public class testRapidDE implements RoutingDecisionEngineRapid {

    private DelayTable delayTable;
    private double timestamp;
    private final UtilityAlgorithm ALGORITHM = UtilityAlgorithm.AVERAGE_DELAY;
    private static final double INFINITY = 99999;
    
    
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    public testRapidDE(Settings s) {
        super();
        delayTable = null;
        timestamp = 0.0;
    }

    @Override
    public void initi(DTNHost host, List<MessageListener> mListeners) {
        delayTable = new DelayTable(host);
//        System.out.println(host);
    }

    public testRapidDE(testRapidDE r) {
        super();
        delayTable = r.delayTable;
        timestamp = r.timestamp;       
    }   

    private enum UtilityAlgorithm {
        AVERAGE_DELAY,
        MISSED_DEADLINES,
        MAXIMUM_DELAY;
    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        timestamp = SimClock.getTime();
        synchronizeDelayTables(peer);
        synchronizeMeetingTimes(peer);
        updateDelayTableStat(thisHost, peer);
        synchronizeAckedMessageIDs(peer);  
        updateAckedMessageIds(thisHost);
        this.delayTable.dummyUpdateConnection1(peer);
    }
    
    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        
    }

    @Override
    public void connectionDown(Connection con, DTNHost thisHost, DTNHost peer) {
        double times = SimClock.getTime() - timestamp;
        this.delayTable.updateConnection(con, times);           
    }
    
    @Override
    public void deletedAckMsgInDelayTable(DTNHost thisHost) {
        deleteAckedMessages(thisHost);
    }
    
    @Override
    public boolean newMessage(Message m) {
        return true;
    }

    @Override
    public void updateDelayTableEntryNewMessage(Message m, DTNHost myHost) {
        updateDelayTableEntry(m, myHost, estimateDelay(m, myHost, true), SimClock.getTime());
        m.updateProperty("utility", estimateDelay(m, myHost, true));
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        if (m.getTo() == aHost) {
            this.delayTable.addAckedMessageIds(m.getId());
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost, DTNHost from) {       
        return m.getTo() != thisHost;
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
        double utility = getMarginalUtility(m, otherHost, thisHost);
        m.updateProperty("utility", utility);
//        System.out.println("util "+utility);
        if (otherHost.getRouter().hasMessage(m.getId())) {
            return false;
        }
        if (utility <= 0) {
            return false;
        }
        return true;
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
    public RoutingDecisionEngineRapid replicate() {
        return new testRapidDE(this);
    }

    private testRapidDE getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();
        assert otherRouter instanceof DecisionEngineRapidRouter : "This router only works "
                + " with other routers of same type";

        return (testRapidDE) ((DecisionEngineRapidRouter) otherRouter).getDecisionEngine();
    }

    private void synchronizeDelayTables(DTNHost peer) {
//        DTNHost otherHost = con.getOtherNode(getHost());
//        RapidRouter otherRouter = (RapidRouter) otherHost.getRouter();
        testRapidDE otherRouter = this.getOtherDecisionEngine(peer);
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
                DTNHost myHost = entry.getKey();
                Double myDelay = entry.getValue().getKey();
                Double myTime = entry.getValue().getValue();

                //create a new host entry if host entry at other host doesn't exist
                if ((otherDelayEntry == null) || (!otherDelayEntry.contains(myHost))) {
                    //parameters: 
                    //m The message 
                    //myHost The host which contains a copy of this message
                    //myDelay The estimated delay
                    //myTime The entry was last changed
                    otherRouter.updateDelayTableEntry(m, myHost, myDelay, myTime);
                } else {
                    //check last update time of other hosts entry and update it 
                    if (otherDelayEntry.isOlderThan(myHost, delayEntry.getLastUpdate(myHost))) {
                        //parameters: 
                        //m The message 
                        //myHost The host which contains a copy of this message
                        //myDelay The estimated delay
                        //myTime The entry was last changed 

                        otherRouter.updateDelayTableEntry(m, myHost, myDelay, myTime);
                    }

                    if ((otherDelayEntry.isAsOldAs(myHost, delayEntry.getLastUpdate(myHost))) && (delayEntry.getDelayOf(myHost) > otherDelayEntry.getDelayOf(myHost))) {
                        //parameters: 
                        //m The message 
                        //myHost The host which contains a copy of this message
                        //myDelay The estimated delay
                        //myTime The entry was last changed 

                        otherRouter.updateDelayTableEntry(m, myHost, myDelay, myTime);
                    }
                }
            }
        }
    }

    private void synchronizeMeetingTimes(DTNHost peer) {
//        DTNHost otherHost = con.getOtherNode(getHost());
//        RapidRouter otherRouter = (RapidRouter) otherHost.getRouter();
        testRapidDE otherRouter = this.getOtherDecisionEngine(peer);
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

    private void updateDelayTableStat(DTNHost myHost, DTNHost peer) {
//        DTNHost myHost = con.getOtherNode(peer);
//        RapidRouter otherRouter = ((RapidRouter) otherHost.getRouter());
        testRapidDE otherRouter = this.getOtherDecisionEngine(peer);
        int from = peer.getAddress();

        for (Message m : myHost.getMessageCollection()) {
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

    private void synchronizeAckedMessageIDs(DTNHost peer) {
//        DTNHost otherHost = con.getOtherNode(getHost());
//        RapidRouter otherRouter = (RapidRouter) otherHost.getRouter();
        testRapidDE otherRouter = this.getOtherDecisionEngine(peer);
        this.delayTable.addAllAckedMessageIds(otherRouter.delayTable.getAllAckedMessageIds());
        otherRouter.delayTable.addAllAckedMessageIds(this.delayTable.getAllAckedMessageIds());

        assert (this.delayTable.getAllAckedMessageIds().equals(otherRouter.delayTable.getAllAckedMessageIds()));
    }

    /**
     * Deletes the messages from the message buffer that are known to be ACKed
     * and also delete the message entries of the ACKed messages in the delay
     * table
     */
    private void deleteAckedMessages(DTNHost myHost) {
//        DTNHost myHost = con.getOtherNode(peer);
        for (String id : this.delayTable.getAllAckedMessageIds()) {
            if (myHost.getRouter().hasMessage(id)) {
                //delete messages from the message buffer
                myHost.getRouter().deleteMessage(id, true);
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
    private void updateAckedMessageIds(DTNHost myHost) {
//        DTNHost myHost = con.getOtherNode(peer);
        ArrayList<String> removableIds = new ArrayList<String>();

        for (String id : this.delayTable.getAllAckedMessageIds()) {
            Message m = myHost.getRouter().getMessage(id);
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
        // a meeting entry of null means that these hosts have never met -> set transfer time to maximum
        if (entry == null) {
            transferTime = INFINITY;		//MX(i)
        } else {
            meetingTime = entry.getAvgMeetingTime();	//MXZ
            transferTime = meetingTime * Math.ceil(packetsSize / transferOpportunity);	// MX(i) = MXZ * ceil[b(i) / B]
        }

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
    private double getMarginalUtility(Message msg, testRapidDE router, DTNHost thisHost) {
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
    private double getMarginalUtility(Message msg, DTNHost peer, DTNHost thisHost) {
        final testRapidDE otherRouter = getOtherDecisionEngine(peer);

        return getMarginalUtility(msg, otherRouter, thisHost);
    }
}
