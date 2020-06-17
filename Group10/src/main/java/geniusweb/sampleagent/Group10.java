package geniusweb.sampleagent;

import geniusweb.actions.*;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.*;
import geniusweb.profile.PartialOrdering;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;

/**
 * <b>Requirement<b> the initial {@link PartialOrdering} must contain at least
 * the bids with lowest utility and highest utility, and the proper comparison
 * info for these two bids.
 */
public class Group10 extends DefaultParty {

    protected ProfileInterface profileint;
    private PartyId me;
    private ProgressRounds progress;

    ArrayList<Bid> receivedBids = new ArrayList<>();

    private OpponentModel opponentModel;

    public Group10() {
    }

    public Group10(Reporter reporter) {
        super(reporter); // for debugging
    }

    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                init((Settings) info);
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();

                if (lastReceivedAction instanceof Offer) {
                    onOfferReceived((Offer) lastReceivedAction);
                }
            } else if (info instanceof YourTurn) {
                Action action = chooseAction();
                getConnection().send(action);
                progress = progress.advance();
                opponentModel.setProgressRounds(this.progress);
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "Final ourcome:" + info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    public void init(Settings info) throws IOException, DeploymentException {
        this.me = info.getID();

        this.progress = (ProgressRounds) info.getProgress();

        this.profileint = ProfileConnectionFactory
                .create(info.getProfile().getURI(), getReporter());
        PartialOrdering partialProfile = (PartialOrdering) profileint
                .getProfile();

        this.opponentModel = OpponentModel.getInstance();
        opponentModel.init(partialProfile, this.progress, getReporter());

        List<Bid> orderedBids = new SimpleLinearOrdering(profileint.getProfile())
                .getBids();

        opponentModel.calculateMyPreferences(orderedBids);

        getReporter().log(Level.INFO,
                "Party " + me + " has finished initialization");
    }

    public void onOfferReceived(Offer receivedOffer) {
        receivedBids.add(receivedOffer.getBid());

        if (this.receivedBids.size() > 1) {
            int numberOfReceivedBids = receivedBids.size();
            Bid lastBid = receivedBids.get(numberOfReceivedBids - 1);
            Bid previousBid = receivedBids.get(numberOfReceivedBids - 2);
            opponentModel.updateModel(lastBid, previousBid);
        }
    }

    public Action chooseAction() throws IOException {
        Bid ourNextBid = this.generateBid();

        if (receivedBids.size() > 1) {
            Bid lastReceivedBid = this.receivedBids.get(receivedBids.size() - 1);

            if (isGood(lastReceivedBid, ourNextBid))
                return new Accept(me, lastReceivedBid);
        }

        return new Offer(me, ourNextBid);
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SHAOP")));
    }

    @Override
    public String getDescription() {
        return "Communicates with COB party to figure out which bids are good. Accepts bids with utility > 0.9. Offers random bids. Requires partial profile";
    }

    public Bid generateBid() throws IOException {
        AllBidsList bidspace = new AllBidsList(profileint.getProfile().getDomain());
        BigInteger numberOfBids = bidspace.size();
        ArrayList<Bid> possibleBids = new ArrayList<>();

        getReporter().log(Level.INFO, "here");

        for (int i = 0; i < numberOfBids.intValue(); i++) {
            possibleBids.add(bidspace.get(i));
        }

        // sort all offers by = selfishness × ourUtility + acceptability × opponentUtility
        possibleBids.sort(Comparator.comparingDouble(this::getBidScore));

        return possibleBids.get(0);
    }

    private double getBidScore(Bid bid) {
        double acceptability = opponentModel.getAcceptability();
        double selfishness = opponentModel.getSelfishness();

        double ourEstimatedUtility = opponentModel.getMyUtility(bid);
        double opponentEstimatedUtility = opponentModel.getOpponentUtility(bid);

        return selfishness * ourEstimatedUtility + acceptability * opponentEstimatedUtility;
    }

    private boolean isGood(Bid receivedBid, Bid ourNextBid) {
        return opponentModel.getMyUtility(receivedBid) > opponentModel.getMyUtility(ourNextBid);
    }
}
