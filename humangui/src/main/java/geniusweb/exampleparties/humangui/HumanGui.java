package geniusweb.exampleparties.humangui;

import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.HashSet;
import java.util.logging.Level;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.Party;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Finished;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import tudelft.utilities.logging.Reporter;

/**
 * A {@link Party} that shows a GUI to the user showing the last received offer
 * and allowing the user to enter his own bid, make an offer or accept the
 * received offer. The GUI is shown on the window attached to the machine where
 * the party runs, so this usually will be on the partyserver. Therefore the
 * users usually will install a partyserver on their local machine to use this
 * party.
 */
public class HumanGui extends DefaultParty {

	private JFrame frame;
	private BiddingInfo biddinginfo;

	public HumanGui() {
	}

	public HumanGui(Reporter reporter) {
		super(reporter); // for debugging
	}

	@Override
	public void notifyChange(Inform info) {
		try {
			if (info instanceof Settings) {
				Settings settings = (Settings) info;
				ProfileInterface profileint = ProfileConnectionFactory
						.create(settings.getProfile().getURI(), reporter);
				biddinginfo = new BiddingInfo(settings, getConnection(),
						getReporter(), profileint);
				initGUI();
			} else if (info instanceof ActionDone) {
				Action otheract = ((ActionDone) info).getAction();
				if (otheract instanceof Offer) {
					biddinginfo.receivedOffer((Offer) otheract);
				}
			} else if (info instanceof YourTurn) {
				biddinginfo.setMyTurn(true);
			} else if (info instanceof Finished) {
				getReporter().log(Level.INFO, "Final ourcome:" + info);
				if (frame != null)
					frame.setVisible(false);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to handle info", e);
		}
	}

	private void initGUI() {
		frame = new JFrame("Negotiation for " + biddinginfo.getMyId());
		frame.getContentPane().setLayout(new BorderLayout());
		frame.add(new MyGUI(biddinginfo), BorderLayout.CENTER);
		frame.pack();
		// we could do frame.addWindowListener but that's a lot of code..
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.setVisible(true);
	}

	@Override
	public Capabilities getCapabilities() {
		return new Capabilities(new HashSet<>(Arrays.asList("SAOP")));
	}

	@Override
	public String getDescription() {
		return "Offers a GUI to the human owning the partiesserver to manually make offers";
	}

}
