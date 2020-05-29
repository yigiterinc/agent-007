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
import tudelft.utilities.logging.Reporter;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;

/**
 * A simple implementation of a SHAOP party that can handle only bilateral
 * negotiations (1 other party). It will ignore all other parties except the one
 * that has the turn right before us. It estimates the utilities of bids by
 * assigning a linear increasing utility from the orderings that have been
 * created.
 * <p>
 * <b>Requirement<b> the initial {@link PartialOrdering} must contain at least
 * the bids with lowest utility and highest utility, and the proper comparison
 * info for these two bids.
 */
public class MyAgent extends DefaultParty {

    private final Random random = new Random();
    protected ProfileInterface profileint;
    private PartyId me;
    private Progress progress;
    private SimpleLinearOrdering estimatedProfile = null;

    ArrayList<Bid> receivedBids = new ArrayList<>();

    private OpponentModel opponentModel;
    private Reporter reporter;

    public MyAgent() {
    }

    public MyAgent(Reporter reporter) {
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
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "Final ourcome:" + info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    public void init(Settings info) throws IOException, DeploymentException {
        this.me = info.getID();
        this.progress = info.getProgress();

        this.profileint = ProfileConnectionFactory
                .create(info.getProfile().getURI(), getReporter());
        PartialOrdering partialProfile = (PartialOrdering) profileint
                .getProfile();

        this.opponentModel = OpponentModel.getInstance();
        opponentModel.init(partialProfile);

        List<Bid> orderedbids = new SimpleLinearOrdering(
                profileint.getProfile()).getBids();

        getReporter().log(Level.INFO,
                "Party " + me + " has finished initialization");
    }

    public void onOfferReceived(Offer receivedOffer) {
        receivedBids.add(receivedOffer.getBid());

        if (this.receivedBids.size() > 2) {
            int numberOfReceivedBids = receivedBids.size();
            Bid lastBid = receivedBids.get(numberOfReceivedBids - 1);
            Bid previousBid = receivedBids.get(numberOfReceivedBids - 2);
            opponentModel.updateModel(lastBid, previousBid);
        }
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

        for (int i = 0; i < numberOfBids.intValue(); i++) {
            possibleBids.add(bidspace.get(i));
        }

        // sort all offers by = selfishness × ourUtility + acceptability × opponentUtility
        possibleBids.sort(Comparator.comparingDouble(this::getBidScore));

        return possibleBids.get(0);
    }


    private double getBidScore(Bid bid) {
        // Call getter from opponent model for these
        double acceptability = 0;
        double selfishness = 1;

        double ourEstimatedUtility = 1;
        double opponentEstimatedUtility = 1;

        return selfishness * ourEstimatedUtility + acceptability * opponentEstimatedUtility;
    }


    private Offer randomBid() throws IOException {
        AllBidsList bidspace = new AllBidsList( profileint.getProfile().getDomain());
        long i = random.nextInt(bidspace.size().intValue());
        Bid bid = bidspace.get(BigInteger.valueOf(i));

        return new Offer(me, bid);
    }

    private boolean isGood(Bid bid, Bid nextBid) {
        if (bid == null) {
            return false;
        }

        return false;
    }

}
