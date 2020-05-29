package geniusweb.exampleparties.simpleshaop;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.Comparison;
import geniusweb.actions.ElicitComparison;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profile.PartialOrdering;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

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
public class ShaopParty extends DefaultParty {

	private static final BigDecimal N09 = new BigDecimal("0.9");
	private Bid lastReceivedBid = null; // we ignore all others
	private PartyId me;
	private final Random random = new Random();
	protected ProfileInterface profileint;
	private Progress progress;
	private SimpleLinearOrdering estimatedProfile = null;

	public ShaopParty() {
	}

	public ShaopParty(Reporter reporter) {
		super(reporter); // for debugging
	}

	@Override
	public void notifyChange(Inform info) {
		try {
			if (info instanceof Settings) {
				Settings settings = (Settings) info;
				this.profileint = ProfileConnectionFactory
						.create(settings.getProfile().getURI(), getReporter());
				this.me = settings.getID();
				this.progress = settings.getProgress();
			} else if (info instanceof ActionDone) {
				Action otheract = ((ActionDone) info).getAction();
				if (otheract instanceof Offer) {
					lastReceivedBid = ((Offer) otheract).getBid();
				} else if (otheract instanceof Comparison) {
					estimatedProfile = estimatedProfile.with(
							((Comparison) otheract).getBid(),
							((Comparison) otheract).getWorse());
					myTurn();
				}
			} else if (info instanceof YourTurn) {
				myTurn();
			} else if (info instanceof Finished) {
				getReporter().log(Level.INFO, "Final ourcome:" + info);
				getReporter().log(Level.INFO, "aaaaabasbenni");
				getReporter().log(Level.INFO, "My profile:" + profileint.toString());
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to handle info", e);
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

	/**
	 * Called when it's (still) our turn and we should take some action. Also
	 * Updates the progress if necessary.
	 */
	private void myTurn() throws IOException {
		Action action = null;
		if (estimatedProfile == null) {
			estimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
		}

		if (lastReceivedBid != null) {
			// then we do the action now, no need to ask user
			if (estimatedProfile.contains(lastReceivedBid)) {
				if (isGood(lastReceivedBid)) {
					action = new Accept(me, lastReceivedBid);
				}
			} else {
				// we did not yet assess the received bid
				action = new ElicitComparison(me, lastReceivedBid, estimatedProfile.getBids());
			}
			if (progress instanceof ProgressRounds) {
				progress = ((ProgressRounds) progress).advance();
			}
		}
		// Otherwise just offer a Random bid
		// FIXME this is weird, can't we do better than random?
		if (action == null)
			action = randomBid();
		getConnection().send(action);
	}

	private Offer randomBid() throws IOException {
		AllBidsList bidspace = new AllBidsList(
				profileint.getProfile().getDomain());
		long i = random.nextInt(bidspace.size().intValue());
		Bid bid = bidspace.get(BigInteger.valueOf(i));
		return new Offer(me, bid);
	}

	private boolean isGood(Bid bid) {
		if (bid == null)
			return false;
		return estimatedProfile.getUtility(bid).compareTo(N09) >= 0;
	}

}
