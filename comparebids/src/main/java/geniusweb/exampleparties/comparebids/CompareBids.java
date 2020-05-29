package geniusweb.exampleparties.comparebids;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import geniusweb.actions.Action;
import geniusweb.actions.Comparison;
import geniusweb.actions.ElicitComparison;
import geniusweb.actions.PartyId;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.Profile;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import tudelft.utilities.logging.Reporter;

/**
 * A party following the COB protocol and thus answers {@link ElicitComparison}
 * questions. This party does not use a GUI but uses the given profile to
 * determine the answer.
 */
public class CompareBids extends DefaultParty {

	private PartyId me;
	protected ProfileInterface profileint;

	public CompareBids() {
	}

	public CompareBids(Reporter reporter) {
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
			} else if (info instanceof ActionDone) {
				Action action = ((ActionDone) info).getAction();
				if (action instanceof ElicitComparison) {
					reply((ElicitComparison) action);
				}
			} else if (info instanceof Finished) {
			} else {
				reporter.log(Level.WARNING, "COB party ignores " + info);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to handle info", e);
		}
	}

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("COB")));
	}

	@Override
	public String getDescription() {
		return "compares a bid with a given list of other bids.";
	}

	/**
	 * Handle the {@link ElicitComparison} and reply with {@link Comparison}.
	 * This shoul dbe fast (assuming the request is reasonably sized) so we
	 * handle this in-line instead of in separate thread.
	 */
	private void reply(ElicitComparison compareRequest) throws IOException {
		List<Bid> better = new LinkedList<>();
		List<Bid> worse = new LinkedList<>();
		Profile profile1 = profileint.getProfile();
		if (!(profile1 instanceof PartialOrdering)) {
			throw new IllegalStateException(
					"Profile must be a partial ordering");
		}
		PartialOrdering profile = (PartialOrdering) profile1;

		Bid bid = compareRequest.getBid();
		for (Bid otherbid : compareRequest.getOptions()) {
			boolean betteroreq = profile.isPreferredOrEqual(otherbid, bid);
			boolean worseoreq = profile.isPreferredOrEqual(bid, otherbid);
			if (betteroreq && !worseoreq)
				better.add(otherbid);
			else if (worseoreq && !betteroreq)
				worse.add(otherbid);
		}
		getConnection().send(new Comparison(me, bid, better, worse));
	}

}
