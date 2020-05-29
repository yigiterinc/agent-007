package geniusweb.exampleparties.timedependentparty;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.immutablelist.ImmutableList;
import tudelft.utilities.logging.Reporter;

/**
 * General time dependent party. This is a simplistic implementation that does
 * brute-force search through the bidspace and can handle bidspace sizes up to
 * 2^31 (approx 1 billion bids). It may take excessive time and run out of time
 * on bidspaces > 10000 bids. In special cases it may even run out of memory,
 */
public class TimeDependentParty extends DefaultParty {

	private static final BigDecimal DEC0001 = new BigDecimal("0.0001");
	private static final BigDecimal DEC100 = new BigDecimal("100");
	private ProfileInterface profileint;
	private LinearAdditive utilspace = null; // last received space
	private PartyId me;
	private Progress progress;
	private Bid lastReceivedBid = null;
	private ExtendedUtilSpace extendedspace;
	private double e = 1.2;

	public TimeDependentParty() {
		super();
	}

	public TimeDependentParty(Reporter reporter) {
		super(reporter); // for debugging
	}

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("SAOP")));
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
				Object newe = settings.getParemeters().get("e");
				if (newe != null) {
					if (newe instanceof Double) {
						this.e = (Double) newe;
					} else {
						getReporter().log(Level.WARNING,
								"parameter e should be Double but found "
										+ newe);
					}
				}

			} else if (info instanceof ActionDone) {
				Action otheract = ((ActionDone) info).getAction();
				if (otheract instanceof Offer) {
					lastReceivedBid = ((Offer) otheract).getBid();
				}
			} else if (info instanceof YourTurn) {
				myTurn();
				if (progress instanceof ProgressRounds) {
					progress = ((ProgressRounds) progress).advance();
				}
			} else if (info instanceof Finished) {
				getReporter().log(Level.INFO, "Final ourcome:" + info);
			}
		} catch (Exception e) {
			getReporter().log(Level.SEVERE, "Failed to handle info", e);
		}
	}

	/**
	 * @return the E value that controls the party's behaviour. Depending on the
	 *         value of e, extreme sets show clearly different patterns of
	 *         behaviour [1]:
	 * 
	 *         1. Boulware: For this strategy e < 1 and the initial offer is
	 *         maintained till time is almost exhausted, when the agent concedes
	 *         up to its reservation value.
	 * 
	 *         2. Conceder: For this strategy e > 1 and the agent goes to its
	 *         reservation value very quickly.
	 * 
	 *         3. When e = 1, the price is increased linearly.
	 * 
	 *         4. When e = 0, the agent plays hardball.
	 */
	public double getE() {
		return e;
	}

	/******************* private support funcs ************************/

	private void myTurn() throws IOException {
		updateUtilSpace();
		Bid bid = makeBid();

		Action myAction;
		if (bid == null || lastReceivedBid != null
				&& utilspace.getUtility(lastReceivedBid)
						.compareTo(utilspace.getUtility(bid)) >= 0) {
			// if bid==null we failed to suggest next bid.
			myAction = new Accept(me, lastReceivedBid);
		} else {
			myAction = new Offer(me, bid);
		}
		getConnection().send(myAction);

	}

	private LinearAdditive updateUtilSpace() throws IOException {
		Profile newutilspace = profileint.getProfile();
		if (!newutilspace.equals(utilspace)) {
			utilspace = (LinearAdditive) newutilspace;
			extendedspace = new ExtendedUtilSpace(utilspace);
		}
		return utilspace;
	}

	/**
	 * @return next possible bid with current target utility, or null if no such
	 *         bid.
	 */
	private Bid makeBid() {
		double time = progress.get(System.currentTimeMillis());
		BigDecimal utilityGoal = utilityGoal(time, getE());
		ImmutableList<Bid> options = extendedspace.getBids(utilityGoal);
		if (options.size() == BigInteger.ZERO)
			return null;
		// pick a random one.
		return options.get(new Random().nextInt(options.size().intValue()));

	}

	/**
	 * 
	 * @param t the time in [0,1] where 0 means start of nego and 1 the end of
	 *          nego (absolute time/round limit)
	 * @param e the e value
	 * @return the utility goal for this time and e value
	 */
	private BigDecimal utilityGoal(double t, double e) {
		BigDecimal minUtil = extendedspace.getMin();
		BigDecimal maxUtil = extendedspace.getMax();

		double ft = 0;
		if (e != 0)
			ft = Math.pow(t, 1 / e);
		// we subtract epsilon to correct possibly small round-up errors
		return new BigDecimal(minUtil.doubleValue()
				+ (maxUtil.doubleValue() - minUtil.doubleValue()) * (1 - ft))
						.min(maxUtil).max(minUtil);
	}

	@Override
	public String getDescription() {
		return "Time-dependent conceder. Aims at utility u(t) = scale * t^(1/e) "
				+ "where t is the time (0=start, 1=end), e is a parameter (default 1.1), and scale such that u(0)=minimum and "
				+ "u(1) = maximum possible utility. ";
	}
}
