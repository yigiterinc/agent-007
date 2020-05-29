package geniusweb.exampleparties.linear;

import geniusweb.exampleparties.timedependentparty.TimeDependentParty;
import tudelft.utilities.logging.Reporter;

/**
 * A simple party that places random bids and accepts when it receives an offer
 * with sufficient utility.
 */
public class Linear extends TimeDependentParty {

	public Linear() {
		super();
	}

	public Linear(Reporter reporter) {
		super(reporter);
	}

	@Override
	public String getDescription() {
		return "Linear: concedes linearly with time";
	}

	@Override
	public double getE() {
		return 1;
	}

}
