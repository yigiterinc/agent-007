package geniusweb.exampleparties.anac2019.winkyagent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import geniusweb.issuevalue.DiscreteValue;
import geniusweb.issuevalue.DiscreteValueSet;
import geniusweb.issuevalue.Domain;
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
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

/**
 * This is translated version of WinkyAgent, winner from ANAC2019. Authors Siqi
 * Chen and Jie Lin Tianjin University China.
 * 
 * <p>
 * IMPORTANT This party requires a {@link ProgressRounds}, and the domain must
 * contain only {@link DiscreteValue}s.
 * <p>
 * WARNING this contains poor code and is here just for comparison and testing.
 */
public class WinkyAgent extends DefaultParty {

	private Bid lastReceivedBid = null;
	private Map<Bid, Double> receiveBids = new HashMap<Bid, Double>();
	private List<Bid> bidOrder = null;
	int utilitySize = 0;
	int ranklistSize = 0;
	double receivehighestUtility = 0.0;// 接收过的最高出价的效用
	List<String> issueList = null;// issue列表
	int issueSize = 0;// issue个数
	int valueSum = 0;// value个数
	double initUtility = 0.0;// value初始效用
	Map<DiscreteValue, Double> valueCorrespond = new HashMap<DiscreteValue, Double>();// value和对应效用
	DiscreteValue[] values = null;// value数组
	double learningRate;
	List<Map.Entry<Bid, Double>> list = new ArrayList<>();// 对receiveBids按照效用进行排序后得到的list
	boolean listSort = true;
	boolean lastBidTag = true;

	// new fields
	private PartyId me;
	private ProgressRounds progress;
	private PartialOrdering partialprofile;
	private AllBidsList allbids; // all bids in the domain.
	private final Random rand = new Random();
	private SimpleLinearOrdering simpleOrdering;

	public WinkyAgent() {
		super();
	}

	public WinkyAgent(Reporter reporter) {
		super(reporter);
	}

	@Override
	public void notifyChange(Inform info) {
		try {
			if (info instanceof Settings) {
				init((Settings) info);
			} else if (info instanceof ActionDone) {
				Action lastReceivedAction = ((ActionDone) info).getAction();
				if (lastReceivedAction instanceof Offer) {
					receivOffer((Offer) lastReceivedAction);
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

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("SAOP")));
	}

	@Override
	public String getDescription() {
		return "ANAC 2019 WinkyAgent translated to GeniusWeb. Requires partial profile. ";
	}

	private void receivOffer(Offer action) {
		lastReceivedBid = action.getBid();
		double lastReceivedBidUtility = linearEstUtility(lastReceivedBid);
		receiveBids.put(lastReceivedBid, lastReceivedBidUtility);
		if (lastReceivedBidUtility > receivehighestUtility) {
			receivehighestUtility = lastReceivedBidUtility;
		}
	}

	private void init(Settings info) throws IOException, DeploymentException {
		this.me = info.getID();
		this.progress = (ProgressRounds) info.getProgress();

		ProfileInterface profileint = ProfileConnectionFactory
				.create(info.getProfile().getURI(), getReporter());
		partialprofile = (PartialOrdering) profileint.getProfile();
		Domain domain = partialprofile.getDomain();
		allbids = new AllBidsList(domain);

		utilitySize = allbids.size().intValue();// 一共可能有多少种出价
		simpleOrdering = new SimpleLinearOrdering(partialprofile);
		bidOrder = simpleOrdering.getBids();
		ranklistSize = bidOrder.size();
		// THIS FIXES THE ORDER OF THE ISSUES, BACKCOMPATIBILITY issue numbers
		issueList = new ArrayList<String>(domain.getIssues());
		issueSize = issueList.size();
		double[] results = new double[ranklistSize];// Array of given utility
		for (int i = 0; i < ranklistSize; i++) { // results array initialization
			results[i] = getBidOrderUtility(bidOrder.get(i));
		}

		int[] valueSize = new int[issueSize];// 第i个问题有j种选择
		for (int i = 0; i < issueSize; i++) {
			String issued = issueList.get(i);
			valueSize[i] = domain.getValues(issued).size().intValue();
			valueSum += valueSize[i];
		}
		initUtility = 1.0 / valueSum; // value初始化的值
		learningRate = initUtility / 10.0;

		values = new DiscreteValue[valueSum];// value数组
		int valuesIndexCnt = 0;
		while (valuesIndexCnt < valueSum) { // 初始化values数组和map valueCorrespond
			for (int i = 0; i < issueSize; i++) {
				String issued = issueList.get(i);
				DiscreteValueSet availableVals = (DiscreteValueSet) domain
						.getValues(issued);
				for (int j = 0; j < availableVals.size().intValue(); j++) {
					values[valuesIndexCnt] = availableVals.getValues().get(j); // 初始化values数组
					valueCorrespond.put(values[valuesIndexCnt], initUtility); // 初始化map
																				// valueCorrespond
					valuesIndexCnt++;
				}
			}
		}

		// bidOrder training set
		// makes a flat list 2D list
		// each element is for one of the bids.
		// valueSum values, in order
		// of iss1val1, iss1val2,...,iss2val1,iss2val2,...,
		// issNval1,...,isNvalM.
		// this fills the features array with 1's
		// if the bid contains that particular value.
		int[][] features = new int[ranklistSize][valueSum];

		// Wouter: original code was bugged. #1766. Putting a fix here.
		for (int bidnr = 0; bidnr < ranklistSize; bidnr++) {
			Bid bid = bidOrder.get(bidnr);
			int featurenr = 0;
			for (String issue : issueList) {
				Value thisbidvalue = bid.getValue(issue);
				for (Value value : domain.getValues(issue)) {
					features[bidnr][featurenr++] = thisbidvalue.equals(value)
							? 1
							: 0;
				}
			}

		}

		double[] parameters = new double[valueSum];// Training value
		for (int i = 0; i < valueSum; i++) {
			parameters[i] = initUtility;
		}

		for (int i = 0; i < ranklistSize * valueSum; i++) { // training
			BGD(features, results, learningRate, parameters);
		}
	}

	private void BGD(int[][] features, double[] results, double learningRate,
			double[] parameters) {
		for (int t = 0; t < valueSum; t++) {
			double sum = 0.0;
			double parametersSum = 0.0;
			for (int j = 0; j < results.length; j++) {
				for (int i = 0; i < valueSum; i++) {
					parametersSum += parameters[i] * features[j][i];
				}
				parametersSum = parametersSum - results[j];
				parametersSum = parametersSum * features[j][t];
				sum += parametersSum;
			}
			double updateValue = 2 * learningRate * sum / results.length;
			parameters[t] = parameters[t] - updateValue;
			/**
			 * ValueCorrespond is bugged, because it uses issuevalues as key
			 * while issuevalues may be not unique (multiple issues may have the
			 * same value, eg "yes" and "no".
			 */
			valueCorrespond.put(values[t], parameters[t]);

		}

	}

	private double linearEstUtility(Bid bid) {
		double linearUtility = 0.0;

		// Wouter the original code was buggy. Replaced with this better
		// version. This code is still buggy because of the valueCorrespond
		// issue.
		// that issue was not fixed.
		int featurenr = 0;
		for (String issue : issueList) {
			Value thisbidvalue = bid.getValue(issue);
			for (Value value : partialprofile.getDomain().getValues(issue)) {
				if (thisbidvalue != null && thisbidvalue.equals(value)) {
					linearUtility += valueCorrespond.get(value);
				}
				featurenr++;
			}
		}
		return linearUtility;
	}

	/**
	 * 
	 * @param bid
	 * @return Estimate known bid utility, equally divided. Seems equal to
	 *         {@link SimpleLinearOrdering#getUtility(Bid)}
	 */
	private double getBidOrderUtility(Bid bid) {
		return simpleOrdering.getUtility(bid).doubleValue();
	}

	private Action chooseAction() {
		int round = progress.getCurrentRound();
		int tround = progress.getTotalRounds();
		double receiveBidUtility = 0.0;
		double bidOrderMax = 1;// getHighUtility();
		Bid bid;
		if (round < tround * 0.7) {
			if (round > 10 && receiveBids.size() < 7) {
				int temp = (int) Math.ceil(ranklistSize * 0.1);
				int randz = rand.nextInt(temp);
				bid = bidOrder.get(ranklistSize - 1 - randz);
				log("receiveBid<7,bidOrder: " + getBidOrderUtility(bid));
				return new Offer(me, bid);
			}
			bid = generateBid(7, bidOrderMax);
			return new Offer(me, bid);
		} else if (round < tround * 0.98) {
			if (receiveBids.size() < 10) {
				int temp = (int) Math.ceil(ranklistSize * 0.15);
				int randz = rand.nextInt(temp);
				bid = bidOrder.get(ranklistSize - 1 - randz);
				log("receiveBid<10,bidOrder: " + getBidOrderUtility(bid));
				return new Offer(me, bid);
			}
			bid = generateBid(9, bidOrderMax);
			return new Offer(me, bid);
		} else if (round < tround * 0.99) {
			receiveBidUtility = linearEstUtility(lastReceivedBid);
			if (listSort) {
				sortReceive();
				listSort = false;
				for (Map.Entry<Bid, Double> entry : list) {
					System.out.println(entry);
				}
				log(receivehighestUtility + "\n");
			}
			if (receiveBidUtility > (receivehighestUtility - 0.03)) {
				return new Accept(me, lastReceivedBid);
			}
			bid = generateReceiveBid();
			log("receive bid Utility: " + linearEstUtility(lastReceivedBid)
					+ " accept阈值: " + (receivehighestUtility - 0.07) + "\n");
			return new Offer(me, bid);
		} else if (round < tround * 0.995) {
			receiveBidUtility = linearEstUtility(lastReceivedBid);
			if (receiveBidUtility > (receivehighestUtility - 0.07)) {
				return new Accept(me, lastReceivedBid);
			}
			bid = generateReceiveBid();
			log("receive bid Utility: " + linearEstUtility(lastReceivedBid)
					+ " accept阈值: " + (receivehighestUtility - 0.11) + "\n");
			return new Offer(me, bid);
		} else if (round == (tround - 1)) {
			return new Accept(me, lastReceivedBid);

		} else {
			receiveBidUtility = linearEstUtility(lastReceivedBid);
			if (receiveBidUtility > (receivehighestUtility - 0.1)) {
				return new Accept(me, lastReceivedBid);
			}
			bid = generateReceiveBid();
			log("receive bid Utility: " + linearEstUtility(lastReceivedBid)
					+ " accept阈值: " + (receivehighestUtility - 0.15) + "\n");
			return new Offer(me, bid);
		}
	}

	public Bid generateBid(int zcnt, double bidOrderMax) {
		Bid randomBid = null;
		if (lastReceivedBid == null) {
			List<Bid> b = simpleOrdering.getBids();
			randomBid = b.get(b.size() - 1);// max bid
		} else if (zcnt == 7) {
			if (bidOrderMax > 0.9) {
				do {
					randomBid = generateRandomBid();
				} while (linearEstUtility(randomBid) < 0.82);
			} else if (bidOrderMax > 0.8) {
				do {
					randomBid = generateRandomBid();
				} while (linearEstUtility(randomBid) < 0.75);
			} else {
				do {
					randomBid = generateRandomBid();
				} while (linearEstUtility(randomBid) < 0.7);
			}
		} else if (zcnt == 9) {
			if (bidOrderMax > 0.9) {
				do {
					randomBid = generateRandomBid();
				} while (linearEstUtility(randomBid) < 0.8);
			} else if (bidOrderMax > 0.8) {
				do {
					randomBid = generateRandomBid();
				} while (linearEstUtility(randomBid) < 0.7);
			} else {
				do {
					randomBid = generateRandomBid();
				} while (linearEstUtility(randomBid) < 0.68);
			}
		}

		log(progress.getCurrentRound() + "generateBid: "
				+ linearEstUtility(randomBid));
		return randomBid;
	}

	private Bid generateReceiveBid() {
		Bid bid;

		int listSelectUtility = (int) Math.ceil(list.size() * 0.03);
		double temp = list.get(listSelectUtility - 1).getValue();

		if (temp < 0.7) {
			temp = 0.7;
			do {
				bid = generateRandomBid();
			} while (linearEstUtility(bid) < temp);
			log(progress.getCurrentRound() + " generateRandomBid: "
					+ linearEstUtility(bid) + " temp:" + temp);
			return bid;
		} else {
			if (lastBidTag) {
				int rand1 = rand.nextInt(listSelectUtility);
				bid = list.get(rand1).getKey();
				lastBidTag = false;
				log(progress.getCurrentRound() + " generateReceiveBid: "
						+ linearEstUtility(bid) + " temp:" + temp);
				return bid;
			} else {
				do {
					bid = generateRandomBid();
				} while (linearEstUtility(bid) < temp);
				lastBidTag = true;
				log(progress.getCurrentRound() + " generateRandomBid: "
						+ linearEstUtility(bid) + " temp:" + temp);
				return bid;
			}
		}

	}

	private void sortReceive() { // 将收到的出价进行排序
		for (Map.Entry<Bid, Double> entry : receiveBids.entrySet()) {
			list.add(entry); // 将map中的元素放入list中
		}

		list.sort(new Comparator<Map.Entry<Bid, Double>>() {
			@Override
			public int compare(Map.Entry<Bid, Double> o1,
					Map.Entry<Bid, Double> o2) {
				double result = o2.getValue() - o1.getValue();
				if (result > 0)
					return 1;
				else if (result == 0)
					return 0;
				else
					return -1;
			}
			// 逆序（从大到小）排列，正序为“return o1.getValue()-o2.getValue”
		});
	}

	private void log(String s) {
		getReporter().log(Level.INFO, s);
	}

	private Bid generateRandomBid() {
		return allbids.get(rand.nextInt(allbids.size().intValue()));
	}

}
