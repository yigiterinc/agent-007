package geniusweb.exampleparties.agentgg;

import static java.lang.Math.max;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import javax.websocket.DeploymentException;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Value;
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
 * Translated version of Genius ANAC 2019 AgentGG originally written by Shaobo
 * Xu and Peihao Ren of University of Southampton. This party requires a partial
 * ordering as input (notice that most profiles are also partial ordering
 * anyway).
 */
public class AgentGG extends DefaultParty {

	private ImpMap impMap;
	private ImpMap opponentImpMap;
	private double offerLowerRatio = 1.0;
	private double offerHigherRatio = 1.1;
	private double MAX_IMPORTANCE;
	private double MIN_IMPORTANCE;
	private double MEDIAN_IMPORTANCE;
	private Bid MAX_IMPORTANCE_BID;
	private Bid MIN_IMPORTANCE_BID;
	private Bid receivedBid;
	private double reservationImportanceRatio;
	private boolean offerRandomly = true;

	private double startTime;
	private boolean maxOppoBidImpForMeGot = false;
	private double maxOppoBidImpForMe;
	private double estimatedNashPoint;
	private Bid lastReceivedBid;
	private boolean initialTimePass = false;

	// new for GeniusWeb
	private ProfileInterface profileint;
	private PartyId me;
	private Progress progress;
	private Action lastReceivedAction = null;
	private final Random rand = new Random();
	private AllBidsList allbids; // all bids in domain.

	public AgentGG() {
		super();
	}

	public AgentGG(Reporter reporter) {
		super(reporter);
	}

	@Override
	public void notifyChange(Inform info) {
		try {
			if (info instanceof Settings) {
				init((Settings) info);
			} else if (info instanceof ActionDone) {
				lastReceivedAction = ((ActionDone) info).getAction();
				if (lastReceivedAction instanceof Offer) {
					this.receivedBid = ((Offer) lastReceivedAction).getBid();
				}
			} else if (info instanceof YourTurn) {
				Action action = chooseAction();
				getConnection().send(action);
				if (progress instanceof ProgressRounds) {
					progress = ((ProgressRounds) progress).advance();
				}
			} else if (info instanceof Finished) {
				getReporter().log(Level.INFO, "Final ourcome:" + info);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to handle info", e);
		}
	}

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("SAOP")));
	}

	@Override
	public String getDescription() {
		return "ANAC 2019 AgentGG translated to GeniusWeb. Requires partial profile. Use frequency counting to estimate important opponent values. ";
	}

	/***************
	 * private
	 * 
	 * @throws DeploymentException
	 *********************/

	private void init(Settings info) throws IOException, DeploymentException {
		this.me = info.getID();
		this.progress = info.getProgress();

		this.profileint = ProfileConnectionFactory
				.create(info.getProfile().getURI(), getReporter());
		PartialOrdering partialprofile = (PartialOrdering) profileint
				.getProfile();
		allbids = new AllBidsList(partialprofile.getDomain());

		// Create empty my import map
		this.impMap = new ImpMap(partialprofile);
		// and opponent's value map. CHECK why is the opponent map not initially
		// empty?
		this.opponentImpMap = new ImpMap(partialprofile);

		// Wouter we use SimpleLinearOrdering (from shaop party) to get sorted
		// bids from our profile.
		List<Bid> orderedbids = new SimpleLinearOrdering(
				profileint.getProfile()).getBids();

		// Update my importance map
		this.impMap.self_update(orderedbids);

		// Get maximum, minimum, median bid
		this.getMaxAndMinBid();
		this.getMedianBid(orderedbids);

		// Get the reservation value, converted to the percentage of importance
		this.reservationImportanceRatio = this.getReservationRatio();

		getReporter().log(Level.INFO,
				"reservation ratio: " + this.reservationImportanceRatio);
		getReporter().log(Level.INFO,
				"my max importance bid: " + this.MAX_IMPORTANCE_BID);
		getReporter().log(Level.INFO,
				"my max importance: " + this.MAX_IMPORTANCE);
		getReporter().log(Level.INFO,
				"my min importance bid: " + this.MIN_IMPORTANCE_BID);
		getReporter().log(Level.INFO,
				"my min importance: " + this.MIN_IMPORTANCE);
		getReporter().log(Level.INFO,
				"my median importance: " + this.MEDIAN_IMPORTANCE);
		getReporter().log(Level.INFO,
				"Party " + me + " has finished initialization");
	}

	private Action chooseAction() {
		double time = progress.get(System.currentTimeMillis());

		// Start competition
		if (!(this.lastReceivedAction instanceof Offer))
			return new Offer(me, this.MAX_IMPORTANCE_BID);

		// The ratio of the other party's offer to me
		double impRatioForMe = (this.impMap.getImportance(this.receivedBid)
				- this.MIN_IMPORTANCE)
				/ (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE);

		// Accept the terms of the offer, which is higher than my threshold
		if (impRatioForMe >= this.offerLowerRatio) {
			getReporter().log(Level.INFO, "\n\naccepted agent: Agent" + me);
			getReporter().log(Level.INFO, "last bid: " + this.receivedBid);
			getReporter().log(Level.INFO,
					"\ncurrent threshold: " + this.offerLowerRatio);
			getReporter().log(Level.INFO, "\n\n");
			return new Accept(me, this.receivedBid);
		}

		// When the opponent's importance is around 1.0, how much can he get.
		// Finding the endpoints of the Pareto boundary
		if (!maxOppoBidImpForMeGot)
			this.getMaxOppoBidImpForMe(time, 3.0 / 1000.0);

		// Update opponent importance table
		if (time < 0.3)
			this.opponentImpMap.opponent_update(this.receivedBid);

		// Strategy
		this.getThreshold(time);

		// Last round
		if (time >= 0.9989) {
			double ratio = (this.impMap.getImportance(this.receivedBid)
					- this.MIN_IMPORTANCE)
					/ (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE);
			if (ratio > this.reservationImportanceRatio + 0.2) {
				return new Accept(me, receivedBid);
			}
		}

		getReporter().log(Level.INFO,
				"high threshold: " + this.offerHigherRatio);
		getReporter().log(Level.INFO, "low threshold: " + this.offerLowerRatio);
		getReporter().log(Level.INFO,
				"estimated nash: " + this.estimatedNashPoint);
		getReporter().log(Level.INFO,
				"reservation: " + this.reservationImportanceRatio);

		Bid bid = getNeededRandomBid(this.offerLowerRatio,
				this.offerHigherRatio);
		this.lastReceivedBid = this.receivedBid;
		return new Offer(me, bid);
	}

	/**
	 * Get our optimal value (Pareto optimal boundary point) when the utility of
	 * the other party is around 1.0 The opponent may first report the same bid
	 * several times, ignore it, and start timing at different times. For
	 * durations (such as 20 rounds), choose the bid with the highest importance
	 * for me. Since the bid of the other party must be very important to the
	 * other party at this time, it can meet our requirements.
	 */
	private void getMaxOppoBidImpForMe(double time, double timeLast) {
		double thisBidImp = this.impMap.getImportance(this.receivedBid);
		if (thisBidImp > this.maxOppoBidImpForMe)
			this.maxOppoBidImpForMe = thisBidImp;

		if (this.initialTimePass) {
			if (time - this.startTime > timeLast) {
				double maxOppoBidRatioForMe = (this.maxOppoBidImpForMe
						- this.MIN_IMPORTANCE)
						/ (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE);
				this.estimatedNashPoint = (1 - maxOppoBidRatioForMe) / 1.7
						+ maxOppoBidRatioForMe; // 1.414 是圆，2是直线
				this.maxOppoBidImpForMeGot = true;
			}
		} else {
			if (this.lastReceivedBid != this.receivedBid) {
				this.initialTimePass = true;
				this.startTime = time;
			}
		}
	}

	/**
	 * Get upper and lower thresholds based on time
	 */
	private void getThreshold(double time) {
		if (time < 0.01) {
			// The first 10 rounds of 0.9999, in order to adapt to some special
			// domains
			this.offerLowerRatio = 0.9999;
		} else if (time < 0.02) {
			// 10 ~ 20 rounds of 0.99, in order to adapt to some special domains
			this.offerLowerRatio = 0.99;
		} else if (time < 0.2) {
			// 20 ~ 200 rounds reported high price, dropped to 0.9
			this.offerLowerRatio = 0.99 - 0.5 * (time - 0.02);
		} else if (time < 0.5) {
			this.offerRandomly = false;
			// 200 ~ 500 rounds gradually reduce the threshold to 0.5 from the
			// estimated Nash point
			double p2 = 0.3 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			this.offerLowerRatio = 0.9
					- (0.9 - p2) / (0.5 - 0.2) * (time - 0.2);
		} else if (time < 0.9) {
			// 500 ~ 900 rounds quickly decrease the threshold to 0.2 from the
			// estimated Nash point
			double p1 = 0.3 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double p2 = 0.15 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			this.offerLowerRatio = p1 - (p1 - p2) / (0.9 - 0.5) * (time - 0.5);
		} else if (time < 0.98) {
			// Compromise 1
			double p1 = 0.15 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double p2 = 0.05 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double possibleRatio = p1 - (p1 - p2) / (0.98 - 0.9) * (time - 0.9);
			this.offerLowerRatio = max(possibleRatio,
					this.reservationImportanceRatio + 0.3);
		} else if (time < 0.995) {
			// Compromise 2 980 ~ 995 rounds
			double p1 = 0.05 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double p2 = 0.0 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double possibleRatio = p1
					- (p1 - p2) / (0.995 - 0.98) * (time - 0.98);
			this.offerLowerRatio = max(possibleRatio,
					this.reservationImportanceRatio + 0.25);
		} else if (time < 0.999) {
			// Compromise 3 995 ~ 999 rounds
			double p1 = 0.0 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double p2 = -0.35 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			double possibleRatio = p1
					- (p1 - p2) / (0.9989 - 0.995) * (time - 0.995);
			this.offerLowerRatio = max(possibleRatio,
					this.reservationImportanceRatio + 0.25);
		} else {
			double possibleRatio = -0.4 * (1 - this.estimatedNashPoint)
					+ this.estimatedNashPoint;
			this.offerLowerRatio = max(possibleRatio,
					this.reservationImportanceRatio + 0.2);
		}
		this.offerHigherRatio = this.offerLowerRatio + 0.1;
	}

	/**
	 * Get the ratio of the reservation value to the importance matrix. ASSUMES
	 * The imgMap has been initialized.
	 * 
	 */
	private double getReservationRatio() throws IOException {
		double medianBidRatio = (this.MEDIAN_IMPORTANCE - this.MIN_IMPORTANCE)
				/ (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE);
		Bid resBid = this.profileint.getProfile().getReservationBid();
		double resValue = 0.1;
		if (resBid != null) {
			resValue = this.impMap.getImportance(resBid);
		}
		return resValue * medianBidRatio / 0.5;
	}

	/**
	 * Get the maximum and minimum importance values and corresponding offers
	 */
	private void getMaxAndMinBid() {
		HashMap<String, Value> lValues1 = new HashMap<>();
		HashMap<String, Value> lValues2 = new HashMap<>();
		for (Map.Entry<String, List<impUnit>> entry : this.impMap.entrySet()) {
			Value value1 = entry.getValue().get(0).valueOfIssue;
			Value value2 = entry.getValue()
					.get(entry.getValue().size() - 1).valueOfIssue;
			String issue = entry.getKey();
			lValues1.put(issue, value1);
			lValues2.put(issue, value2);
		}
		this.MAX_IMPORTANCE_BID = new Bid(lValues1);
		this.MIN_IMPORTANCE_BID = new Bid(lValues2);
		this.MAX_IMPORTANCE = this.impMap
				.getImportance(this.MAX_IMPORTANCE_BID);
		this.MIN_IMPORTANCE = this.impMap
				.getImportance(this.MIN_IMPORTANCE_BID);
	}

	/**
	 * Get the import value corresponding to the median bid in bid ranking
	 * 
	 * @param orderedbids a list of bids, ordered from low to high utility.
	 */
	private void getMedianBid(List<Bid> orderedbids) {

		int median = (orderedbids.size() - 1) / 2;
		int median2 = -1;
		if (orderedbids.size() % 2 == 0) {
			median2 = median + 1;
		}
		int current = 0;
		for (Bid bid : orderedbids) {
			current += 1;
			if (current == median) {
				this.MEDIAN_IMPORTANCE = this.impMap.getImportance(bid);
				if (median2 == -1)
					break;
			}
			if (current == median2) {
				this.MEDIAN_IMPORTANCE += this.impMap.getImportance(bid);
				break;
			}
		}
		if (median2 != -1)
			this.MEDIAN_IMPORTANCE /= 2;
	}

//    /**
//     * 更新对手的最大及最小Importance的值及对应OFFER
//     */
//    private void getOpponentMaxAndMinBid() {
//        HashMap<Integer, Value> lValues1 = new HashMap<>();
//        HashMap<Integer, Value> lValues2 = new HashMap<>();
//        for (Map.Entry<Issue, List<impUnit>> entry : this.opponentImpMap.entrySet()) {
//            Value value1 = entry.getValue().get(0).valueOfIssue;
//            Value value2 = entry.getValue().get(entry.getValue().size() - 1).valueOfIssue;
//            int issueNumber = entry.getKey().getNumber();
//            lValues1.put(issueNumber, value1);
//            lValues2.put(issueNumber, value2);
//        }
//        Bid OPPONENT_MAX_IMPORTANCE_BID = new Bid(this.getDomain(), lValues1);
//        Bid OPPONENT_MIN_IMPORTANCE_BID = new Bid(this.getDomain(), lValues2);
//        this.OPPONENT_MAX_IMPORTANCE = this.opponentImpMap.getImportance(OPPONENT_MAX_IMPORTANCE_BID);
//        this.OPPONENT_MIN_IMPORTANCE = this.opponentImpMap.getImportance(OPPONENT_MIN_IMPORTANCE_BID);
//    }

	/**
	 * Get eligible random bids. Generate k bids randomly, select bids within
	 * the threshold range, and return the bid with the highest opponent import.
	 *
	 * @param lowerRatio Generate a lower limit for the random bid
	 * @param upperRatio Generate random bid upper limit
	 * @return Bid
	 * @throws IOException
	 */
	private Bid getNeededRandomBid(double lowerRatio, double upperRatio) {
		final long k = 2 * this.allbids.size().longValue();
		double lowerThreshold = lowerRatio
				* (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE)
				+ this.MIN_IMPORTANCE;
		double upperThreshold = upperRatio
				* (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE)
				+ this.MIN_IMPORTANCE;

		for (int t = 0; t < 3; t++) {
			double highest_opponent_importance = 0.0;
			Bid returnedBid = null;
			for (int i = 0; i < k; i++) {
				Bid bid = generateRandomBid();
				double bidImportance = this.impMap.getImportance(bid);
				double bidOpponentImportance = this.opponentImpMap
						.getImportance(bid);
				if (bidImportance >= lowerThreshold
						&& bidImportance <= upperThreshold) {
					if (this.offerRandomly)
						return bid; // Randomly bid for the first 0.2 time
					if (bidOpponentImportance > highest_opponent_importance) {
						highest_opponent_importance = bidOpponentImportance;
						returnedBid = bid;
					}
				}
			}
			if (returnedBid != null) {
				return returnedBid;
			}
		}
		// If something goes wrong and no suitable bid is found, then a higher
		// than the lower limit
		while (true) {
			Bid bid = generateRandomBid();
			if (this.impMap.getImportance(bid) >= lowerThreshold) {
				return bid;
			}
		}
	}

	private Bid generateRandomBid() {
		return allbids.get(rand.nextInt(allbids.size().intValue()));
	}

}
