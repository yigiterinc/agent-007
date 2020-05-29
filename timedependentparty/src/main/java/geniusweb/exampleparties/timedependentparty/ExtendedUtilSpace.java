package geniusweb.exampleparties.timedependentparty;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedList;

import geniusweb.bidspace.BidsWithUtility;
import geniusweb.bidspace.Interval;
import geniusweb.bidspace.IssueInfo;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Value;
import geniusweb.profile.utilityspace.LinearAdditive;
import tudelft.utilities.immutablelist.ImmutableList;

/**
 * Inner class for TimeDependentParty, made public for testing purposes. This
 * class may change in the future, use at your own risk.
 *
 */
public class ExtendedUtilSpace {
	private LinearAdditive utilspace;
	private BigDecimal tolerance; // utility tolerance for a bid.
	private BidsWithUtility bidutils;
	// min and max achievable utility
	private BigDecimal minUtil;
	private BigDecimal maxUtil;

	public ExtendedUtilSpace(LinearAdditive space) {
		this.utilspace = space;
		bidutils = new BidsWithUtility(utilspace);
		computeMinMax();
		this.tolerance = computeTolerance();

	}

	/**
	 * Computes the fields minutil and maxUtil.
	 * <p>
	 * TODO this is simplistic, very expensive method and may cause us to run
	 * out of time on large domains.
	 * <p>
	 * Assumes that utilspace and bidutils have been set properly.
	 */
	private void computeMinMax() {
		Interval range = bidutils.getRange();
		this.minUtil = range.getMin();
		this.maxUtil = range.getMax();

		Bid rvbid = utilspace.getReservationBid();
		if (rvbid != null) {
			BigDecimal rv = utilspace.getUtility(rvbid);
			if (rv.compareTo(minUtil) > 0)
				minUtil = rv;
		}
	}

	/**
	 * Tolerance is the Interval we need when searching bids. When we are close
	 * to the maximum utility, this value has to be the distance between the
	 * best and one-but-best utility.
	 * 
	 * @return the minimum tolerance required, which is the minimum difference
	 *         between the weighted utility of the best and one-but-best issue
	 *         value.
	 */
	protected BigDecimal computeTolerance() {
		BigDecimal tolerance = BigDecimal.ONE;
		for (IssueInfo iss : bidutils.getInfo()) {
			if (iss.getValues().size().compareTo(BigInteger.ONE) > 0) {
				// we have at least 2 values.
				LinkedList<BigDecimal> values = new LinkedList<BigDecimal>();
				for (Value val : iss.getValues()) {
					values.add(iss.getWeightedUtil(val));
				}
				Collections.sort(values);
				Collections.reverse(values);
				tolerance = tolerance
						.min(values.get(0).subtract(values.get(1)));
			}
		}
		return tolerance;
	}

	public BigDecimal getMin() {
		return minUtil;
	}

	public BigDecimal getMax() {
		return maxUtil;
	}

	/**
	 * @param utilityGoal
	 * @return bids with utility inside [utilitygoal-tolerance, utilitygoal]
	 */
	public ImmutableList<Bid> getBids(BigDecimal utilityGoal) {
		return bidutils.getBids(
				new Interval(utilityGoal.subtract(tolerance), utilityGoal));
	}

}
