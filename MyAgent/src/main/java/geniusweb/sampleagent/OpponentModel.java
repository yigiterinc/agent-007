package geniusweb.sampleagent;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.PartialOrdering;
import geniusweb.progress.ProgressRounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpponentModel {
    private static OpponentModel opponentModel;

    private Domain domain;

    private double selfishness = 1;

    private double acceptability = 0;
    private double opponentNiceness = 0;

    private ProgressRounds progressRounds;

    // Maps each issue to a list of weights for each value
    private HashMap<String, HashMap<String, Weight>> myPreferenceMap;
    private HashMap<String, HashMap<String, Weight>> opponentPreferenceMap;

    // Map each issue to their weight
    private HashMap<String, Double> myIssueWeights;
    private HashMap<String, Double> opponentIssueWeights;

    private ArrayList<Bid> opponentBids = null;

    private PartialOrdering profile;

    private class Weight {
        int count;
        double weight;

        public Weight() {
            this.count = 0;
            this.weight = 0.0;
        }
    }

    private OpponentModel() {

    }

    public static OpponentModel getInstance() {
        if (opponentModel == null)
            opponentModel = new OpponentModel();

        return opponentModel;
    }

    public void init(PartialOrdering profile, ProgressRounds progressRounds) {
        this.domain = profile.getDomain();
        this.profile = profile;
        this.progressRounds = progressRounds;

        initImportanceMaps();
    }

    /**
     * Initializes and fills preference maps & issue weights
     */
    private void initImportanceMaps() {
        this.myPreferenceMap = new HashMap<>();
        this.opponentPreferenceMap = new HashMap<>();
        this.myIssueWeights = new HashMap<>();
        this.opponentIssueWeights = new HashMap<>();

        for (String issue : domain.getIssues()) {
            ValueSet values = domain.getValues(issue);
            HashMap<String, Weight> myIssueValueWeights = new HashMap<>();
            HashMap<String, Weight> opponentIssueValueWeights = new HashMap<>();

            for (Value value : values) {
                myIssueValueWeights.put(value.toString(), new Weight());
                opponentIssueValueWeights.put(value.toString(), new Weight());
            }

            this.myPreferenceMap.put(issue, myIssueValueWeights);
            this.opponentPreferenceMap.put(issue, opponentIssueValueWeights);
        }

        // Fill both issue weights maps as well
        for (String issue : domain.getIssues()) {
            this.myIssueWeights.put(issue, 0.0);
            this.opponentIssueWeights.put(issue, 0.0);
        }
    }

    /**
     *
     * @param bidOrdering: a sorted list of bids, ascending
     * Fills myPreferenceMap and myIssueWeights
     */
    // TODO: As we go down the list, occurrences must be more valuable (count * smth)
    public void calculateMyPreferences(List<Bid> bidOrdering) {
        int numberOfBids = bidOrdering.size();

        // Calculate value weights for each issue
        for (Bid bid : bidOrdering) {
            for (String issue : bid.getIssues()) {
                HashMap<String, Weight> valueWeights = this.opponentPreferenceMap.get(issue);

                String issueValue = bid.getValue(issue).toString();
                Weight currentWeight = valueWeights.get(issueValue);
                currentWeight.count++;
                currentWeight.weight = (double) currentWeight.count / numberOfBids;
            }
        }

        for (String issue : myIssueWeights.keySet()) {
            HashMap<String, Weight> preferenceMapForIssue = this.myPreferenceMap.get(issue);
            int maxOccurrences = getMostOccurrences(preferenceMapForIssue);
            double issueWeight = (double) maxOccurrences / numberOfBids;
            this.myIssueWeights.put(issue, issueWeight);
        }
    }

    // TODO
    public double getMyUtility(Bid bid) {
        return 0.0;
    }

    // TODO
    public double getOpponentUtility(Bid bid) {
        return 0.0;
    }

    /**
     * Works when we receive a new bid
     * Updates preference maps of opponent, niceness score and acceptability
     * @param bid: Recently received (last) bid
     * @param previousBid: Second last bid
     */
    public void updateModel(Bid bid, Bid previousBid) {
        if (opponentBids == null) {  // We are just gettin started
            this.opponentBids = new ArrayList<>();
            this.opponentBids.add(previousBid);
            this.opponentBids.add(bid);
            updateOpponentPreferences(previousBid);
            updateOpponentPreferences(bid);
        } else {
            this.opponentBids.add(bid);
            updateOpponentPreferences(bid);
        }

        this.opponentNiceness = getOpponentUtility(bid) / getOpponentUtility(previousBid);

        int round = this.progressRounds.getCurrentRound();
        int numberOfRounds = this.progressRounds.getTotalRounds();

        int time = round / numberOfRounds;

        this.acceptability = log2(time) * Math.pow(opponentNiceness, 2) / 10;
        this.selfishness = 1 - acceptability;
    }

    public static double log2(int n) {
        return (Math.log(n) / Math.log(2));
    }

    /**
     * Updates the issue weights and value weights for each issue
     * @param bid: Most recently received bid
     */
    private void updateOpponentPreferences(Bid bid) {
        int numberOfBids = this.opponentBids.size();
        Map<String, Value> issueValues = bid.getIssueValues();

        // Update value weights
        for (String issue : issueValues.keySet()) {
            String value = issueValues.get(issue).toString();
            Weight currentWeight = opponentPreferenceMap.get(issue).get(value);
            currentWeight.count += 1;
            currentWeight.weight = (double) currentWeight.count / numberOfBids;
            opponentPreferenceMap.get(issue).put(value, currentWeight);
        }

        // Update issue weights
        for (String issue : opponentPreferenceMap.keySet()) {
            HashMap<String, Weight> preferenceMapForIssue = this.opponentPreferenceMap.get(issue);
            int maxOccurrences = getMostOccurrences(preferenceMapForIssue);
            double issueWeight = (double) maxOccurrences / numberOfBids;
            this.myIssueWeights.put(issue, issueWeight);
        }
    }

    /**
     * Finds the number of occurrences of most preferred value of issue
     * @param preferenceMap: Maps issue value to weight
     * @return number of occurrences of max occurring value
     */
    private int getMostOccurrences(HashMap<String, Weight> preferenceMap) {
        int maxOccurrences = 0;

        for (Weight weight : preferenceMap.values()) {
            if (weight.count > maxOccurrences) {
                maxOccurrences = weight.count;
            }
        }

        return maxOccurrences;
    }

    public double getSelfishness() {
        return selfishness;
    }

    public double getAcceptability() {
        return acceptability;
    }
}
