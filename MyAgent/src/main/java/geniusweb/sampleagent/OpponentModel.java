package geniusweb.sampleagent;

import geniusweb.issuevalue.Bid;

public class OpponentModel {
    private static OpponentModel opponentModel;

    private double selfishness = 1;
    private double acceptability = 0;
    private double opponentNiceness = 0;

    private OpponentModel() {

    }

    public static OpponentModel getInstance() {
        if (opponentModel == null)
            opponentModel = new OpponentModel();

        return opponentModel;
    }

    // Update model upon receiving a new bid
    public void updateModel(Bid bid, Bid previousBid) {
        // update preference maps
        // update niceness, selfishness and acceptability
    }

}
