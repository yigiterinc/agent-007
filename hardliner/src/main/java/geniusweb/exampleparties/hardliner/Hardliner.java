package geniusweb.exampleparties.hardliner;

import geniusweb.exampleparties.timedependentparty.TimeDependentParty;
import tudelft.utilities.logging.Reporter;

/**
 * A simple party that places random bids and accepts when it receives an offer
 * with sufficient utility.
 */
public class Hardliner extends TimeDependentParty {

	public Hardliner() {
		super();
	}

	public Hardliner(Reporter reporter) {
		super(reporter);
	}

	@Override
	public String getDescription() {
		return "Hardliner: does not concede";
	}

	@Override
	public double getE() {
		return 0;
	}

}
