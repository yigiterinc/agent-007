package geniusweb.exampleparties.conceder;

import geniusweb.exampleparties.timedependentparty.TimeDependentParty;
import tudelft.utilities.logging.Reporter;

/**
 * A simple party that places random bids and accepts when it receives an offer
 * with sufficient utility.
 */
public class Conceder extends TimeDependentParty {

	public Conceder() {
		super();
	}

	public Conceder(Reporter reporter) {
		super(reporter);
	}

	@Override
	public String getDescription() {
		return "Conceder: going to the reservation value very quickly";
	}

	@Override
	public double getE() {
		return 2;
	}

}
