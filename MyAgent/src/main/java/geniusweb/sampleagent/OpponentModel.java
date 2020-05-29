package geniusweb.sampleagent;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.utilityspace.UtilitySpace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OpponentModel {
    private static OpponentModel opponentModel;

    private Domain domain;

    private double selfishness = 1;
    private double acceptability = 0;
    private double opponentNiceness = 0;

    // Maps each issue to a list of weights for each value
    private HashMap<String, List<Weight>> myPreferenceMap;
    private HashMap<String, List<Weight>> opponentPreferenceMap;

    // Map each issue to their weight
    private HashMap<String, Double> myIssueWeights;
    private HashMap<String, Double> opponentIssueWeights;

    private ArrayList<Bid> opponentBids = null;

    private PartialOrdering profile;
    private int numberOfIssues = 0;

    private class Weight {
        int count;
        double weight;
    }

    private OpponentModel() {

    }

    public static OpponentModel getInstance() {
        if (opponentModel == null)
            opponentModel = new OpponentModel();

        return opponentModel;
    }

    public void init(PartialOrdering profile) {
        this.domain = profile.getDomain();
        this.profile = profile;

        initImportanceMaps();
    }

    private void initImportanceMaps() {
        // Create empty opponent's preference map and issue weights
        // Create my preference map and issue weights

    }

    /**
     *
     * @param bidOrdering: a sorted list of bids, ascending
     * Fills myPreferenceMap and myIssueWeights
     */
    private void calculateMyPreferences(List<Bid> bidOrdering) {

    }

    private double getMyUtility(Bid bid) {
        return 0.0;
    }

    private double getOpponentUtility(Bid bid) {
        return 0.0;
    }

    // Update model upon receiving a new bid
    public void updateModel(Bid bid, Bid previousBid) {
        // update preference maps
        // update niceness, selfishness and acceptability

        if (opponentBids == null) {  // We are just gettin started
            this.opponentBids = new ArrayList<>();
            this.opponentBids.add(previousBid);
            this.opponentBids.add(bid);
        } else {
            this.opponentBids.add(bid);
        }


    }

}
