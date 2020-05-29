package geniusweb.exampleparties.humangui;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import javax.websocket.DeploymentException;

import geniusweb.actions.Action;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.connection.ConnectionEnd;
import geniusweb.issuevalue.Bid;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.profile.Profile;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.listener.DefaultListenable;
import tudelft.utilities.logging.Reporter;

/**
 * Contains the current state of the user's negotiation. mutable. Assumes SAOP
 * protocol.
 * <p>
 * Listeners receive the following objects:
 * <ul>
 * <li>{@link Action} if user did an action (place offer, end nego, etc)
 * <li>{@link Bid} if user changed his prepared bid (but did not yet offer it)
 * <li>{@link Profile} if the profile was changed
 * <li>{@Link Boolean} if the isMyTurn value changed.
 * </ul>
 */
public class BiddingInfo extends DefaultListenable<Object> {
	private final PartyId me;
	private Offer lastReceivedOffer = null;
	private Progress progress = null;
	private Reporter reporter;

	// profile is the latest one received.
	private Profile profile = null;

	// same as profile.getDomain but forces order on the issues
	// unmodifyable (but we can replace the whole list)
	private List<String> issues = Collections.emptyList();
	private Bid currentBid = new Bid(Collections.emptyMap());
	private boolean isMyTurn = false;
	private ConnectionEnd<Inform, Action> connection;

	/**
	 * 
	 * @param settings   the session {@link Settings}
	 * @param connection the connection to the protocol.
	 * @param reporter   the {@link Reporter} where we can log issues.
	 * @param profileint the {@link ProfileInterface}
	 * @throws Exception if we can not reach the protocol server
	 */
	public BiddingInfo(Settings settings,
			ConnectionEnd<Inform, Action> connection, Reporter reporter,
			ProfileInterface profileint)
			throws IOException, DeploymentException {
		this.connection = connection;
		this.me = settings.getID();
		this.progress = settings.getProgress();
		this.reporter = reporter;

		profileint.addListener(prof -> updateProfile(prof));
		// this call blocks till we have the profile.
		updateProfile(profileint.getProfile());
	}

	/**
	 * 
	 * @return the profile currently used.
	 */
	public Profile getProfile() {
		return profile;
	}

	/**
	 * Changes the model to use the given new profile.
	 * 
	 * @param newprof a new profile
	 */
	private void updateProfile(Profile newprof) {
		this.profile = newprof;
		this.issues = Collections.unmodifiableList(
				new LinkedList<String>(newprof.getDomain().getIssues()));
		notifyListeners(newprof);
	}

	/**
	 * executes an action and sets isMyTurn to false.
	 * 
	 * @param action the action to execute.
	 * @throws IOException           if action can not be sent.
	 * @throws IllegalStateException if isMyTurn is false.
	 */
	public void doAction(Action action) {
		if (!isMyTurn) {
			reporter.log(Level.SEVERE, "User did action but it's not his turn");
			return;
		}
		try {
			connection.send(action);
		} catch (IOException e) {
			reporter.log(Level.SEVERE, "Can't send action", e);
			return;
		}
		if (progress instanceof ProgressRounds) {
			progress = ((ProgressRounds) progress).advance();
		}
		setMyTurn(false);
		// notifyListeners(action);
	}

	/**
	 * @param offer offer received from some other party
	 */
	public void receivedOffer(Offer offer) {
		lastReceivedOffer = offer;
		notifyListeners(offer);
	}

	/**
	 * 
	 * @return the last received offer, or null if no offer received yet.
	 */
	public Offer getReceivedOffer() {
		return lastReceivedOffer;
	}

	public List<String> getIssues() {
		return issues;
	}

	/**
	 * 
	 * @return current bid. Initially is an empty bid.
	 */
	public Bid getCurrentBid() {
		return currentBid;
	}

	public void setCurrentBid(Bid bid) {
		if (bid == null)
			throw new IllegalArgumentException();
		if (!bid.equals(currentBid)) {
			currentBid = bid;
			notifyListeners(bid);
		}
	}

	public PartyId getMyId() {
		return me;
	}

	public void setMyTurn(boolean b) {
		if (isMyTurn != b) {
			isMyTurn = b;
			notifyListeners(b);
		}
	}

}
