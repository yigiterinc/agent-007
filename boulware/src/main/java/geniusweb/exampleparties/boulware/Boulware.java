package geniusweb.exampleparties.boulware;

import geniusweb.exampleparties.timedependentparty.TimeDependentParty;
import tudelft.utilities.logging.Reporter;

/**
 * A simple party that places random bids and accepts when it receives an offer
 * with sufficient utility.
 */
public class Boulware extends TimeDependentParty {

	public Boulware() {
		super();
	}

	public Boulware(Reporter reporter) {
		super(reporter);
	}

	@Override
	public String getDescription() {
		return "Boulware: reluctant to concede";
	}

	@Override
	public double getE() {
		return 0.2;
	}

}
