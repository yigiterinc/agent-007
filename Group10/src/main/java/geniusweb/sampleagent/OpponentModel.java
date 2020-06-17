package geniusweb.sampleagent;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.PartialOrdering;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

import java.util.*;
import java.util.logging.Level;

public class OpponentModel {
    private static OpponentModel opponentModel;

    private Domain domain;

    private double selfishness = 1;

    private double acceptability = 0;
    private double opponentNiceness = 0;

    private ProgressRounds progressRounds;

    // Map each issue to a list of weights for each value
    private HashMap<String, HashMap<String, Weight>> myPreferenceMap;
    // Map each issue to their weight
    private HashMap<String, Double> myIssueWeights;

    private double utilityOfBestBid;

    // Map each issue to their weight
    private HashMap<String, Double> opponentIssueWeights;
    // Map each issue to another map which holds of weights for each value
    private HashMap<String, HashMap<String, Weight>> opponentPreferenceMap;

    private ArrayList<Bid> opponentBids = null;

    private Reporter reporter;

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

    public void init(PartialOrdering profile, ProgressRounds progressRounds, Reporter reporter) {
        this.domain = profile.getDomain();
        this.progressRounds = progressRounds;
        this.reporter = reporter;

        initImportanceMaps();
    }

    public void setProgressRounds(ProgressRounds progress) {
        this.progressRounds = progress;
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
    // TODO: As we go down the list, occurrences must be more valuable (count * smth), mention in report
    public void calculateMyPreferences(List<Bid> bidOrdering) {
        int numberOfBids = bidOrdering.size();

        // Calculate value weights for each issue

        int[] positionBonus = new int[numberOfBids];
        Arrays.setAll(positionBonus, i -> i + 1);
        for (int i = 0; i < bidOrdering.size(); i++) {
            Bid bid = bidOrdering.get(i);
            for (String issue : bid.getIssues()) {
                HashMap<String, Weight> valueWeights = this.opponentPreferenceMap.get(issue);

                String issueValue = bid.getValue(issue).toString();
                Weight currentWeight = valueWeights.get(issueValue);
                currentWeight.count += positionBonus[i];

                // Total weight to be distributed is n * (n + 1) / 2
                int totalWeight = (numberOfBids * (numberOfBids + 1)) / 2;
                currentWeight.weight = (double) currentWeight.count / totalWeight;
            }
        }

        // Calculate issue weights
        HashMap<String, Integer> maxOccurrences = new HashMap<>();
        for (String issue : myIssueWeights.keySet()) {
            HashMap<String, Weight> preferenceMapForIssue = this.myPreferenceMap.get(issue);
            maxOccurrences.put(issue, getMostOccurrences(preferenceMapForIssue));
        }

        int sumOfMaxOccurrences = maxOccurrences.values().stream().reduce(0, Integer::sum);

        for (String issue : myIssueWeights.keySet()) {
            double issueWeight = (double) maxOccurrences.get(issue) / sumOfMaxOccurrences;
            this.myIssueWeights.put(issue, issueWeight);
        }

        // Calculate the utility of best bid
        Bid bestBid = bidOrdering.get(bidOrdering.size() - 1);
        this.utilityOfBestBid = calculateBidUtility(bestBid, myPreferenceMap, myIssueWeights);
    }

    // TODO mention normalization in report
    public double getMyUtility(Bid bid) {
        // Normalize the utility
        return this.calculateBidUtility(bid, myPreferenceMap, myIssueWeights)
                / utilityOfBestBid;
    }

    public double getOpponentUtility(Bid bid) {
        return calculateBidUtility(bid, opponentPreferenceMap, opponentIssueWeights);
    }

    /**
     * @param bid: Bid, which's utility is going to be calculated
     * @param preferenceMap: Maps each issue to a list of weights for each value
     * @param issueWeights: Maps each issue to it's own weight
     * @return
     */
    private double calculateBidUtility(Bid bid,
                                       HashMap<String, HashMap<String, Weight>> preferenceMap,
                                       HashMap<String, Double> issueWeights) {

        Map<String, Value> issueValues = bid.getIssueValues();

        double utility = 0;
        for (String issue : issueValues.keySet()) {
            Value value = issueValues.get(issue);
            String valueToString = value.toString();
            double valueWeight = preferenceMap.get(issue).get(valueToString).weight;
            double issueWeight = issueWeights.get(issue);

            utility = utility + (issueWeight * valueWeight);
        }

        return utility;
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
        int numberOfRounds = this.progressRounds.getTotalRounds()/2;

        int time = (int) Math.floor(((double) round / numberOfRounds) * 10);

        this.acceptability = (log2(time) / opponentNiceness) / numberOfRounds;

        this.selfishness = 1 - acceptability;

        this.reporter.log(Level.INFO, "time: " + time);
        reporter.log(Level.INFO, "niceness: " + opponentNiceness);
        reporter.log(Level.INFO, "selfishness: " + selfishness);
        reporter.log(Level.INFO, "acceptability: " + acceptability);
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
        HashMap<String, Integer> maxOccurrences = new HashMap<>();
        for (String issue : opponentIssueWeights.keySet()) {
            HashMap<String, Weight> preferenceMapForIssue = this.opponentPreferenceMap.get(issue);
            maxOccurrences.put(issue, getMostOccurrences(preferenceMapForIssue));
        }

        int sumOfMaxOccurrences = maxOccurrences.values().stream().reduce(0, Integer::sum);

        for (String issue : opponentIssueWeights.keySet()) {
            double issueWeight = (double) maxOccurrences.get(issue) / sumOfMaxOccurrences;
            this.opponentIssueWeights.put(issue, issueWeight);
        }
    }

    /**
     * Finds the sum of weighted count of most preferred value of issue
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
