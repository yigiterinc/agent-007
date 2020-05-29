package geniusweb.exampleparties.humangui;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Level;

import javax.websocket.DeploymentException;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import geniusweb.actions.Accept;
import geniusweb.actions.PartyId;
import geniusweb.issuevalue.Bid;
import geniusweb.party.inform.Settings;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.ProgressRounds;
import geniusweb.references.Parameters;
import geniusweb.references.ProfileRef;
import geniusweb.references.ProtocolRef;
import tudelft.utilities.listener.DefaultListenable;
import tudelft.utilities.listener.Listener;
import tudelft.utilities.logging.Reporter;

/**
 * Should test the GUI stuff. At this moment it's just a simple quick test of
 * the GUI without much testing
 *
 */
public class BiddingInfoTest {

	private static final PartyId PARTY_ID = new PartyId("party1");
	private static final ProtocolRef SAOP = new ProtocolRef("SAOP");
	private static final PartyId otherparty = new PartyId("other");
	private static final String PROFILE = "src/test/resources/testprofile.json";
	private static final String PROFILE2 = "src/test/resources/testprofile2.json";
	private final static ObjectMapper jackson = new ObjectMapper();

	private final TestConnection connection = new TestConnection();
	private final ProtocolRef protocol = mock(ProtocolRef.class);
	private final ProgressRounds progress = mock(ProgressRounds.class);
	private Settings settings;
	private LinearAdditive profile, profile2;
	private final Parameters parameters = new Parameters();
	private Reporter reporter = mock(Reporter.class);
	private ProfileInterface profileint;
	private BiddingInfo info;

	@Before
	public void before() throws JsonParseException, JsonMappingException,
			IOException, URISyntaxException, DeploymentException {
		settings = new Settings(PARTY_ID,
				new ProfileRef(new URI("file:" + PROFILE)), protocol, progress,
				parameters);

		String serialized = new String(Files.readAllBytes(Paths.get(PROFILE)),
				StandardCharsets.UTF_8);
		profile = (LinearAdditive) jackson.readValue(serialized, Profile.class);
		String serialized2 = new String(Files.readAllBytes(Paths.get(PROFILE2)),
				StandardCharsets.UTF_8);
		profile2 = (LinearAdditive) jackson.readValue(serialized2,
				Profile.class);

		profileint = ProfileConnectionFactory
				.create(settings.getProfile().getURI(), reporter);

		info = new BiddingInfo(settings, connection, reporter, profileint);
	}

	@Test
	public void smokeTest() {
		assertEquals(profile, info.getProfile());
		assertEquals(Arrays.asList("issue2", "issue1"), info.getIssues());
		verify(reporter, times(0)).log(any(), any());

	}

	@Test
	public void testUpdateProfile() throws IOException, DeploymentException {
		TestProfileInterface profileInterface = new TestProfileInterface(
				profile);
		info = new BiddingInfo(settings, connection, reporter,
				profileInterface);
		@SuppressWarnings("unchecked")
		Listener<Object> testlistener = mock(Listener.class);
		info.addListener(testlistener);
		assertEquals(profile, info.getProfile());

		// push new profile into the interface and check it's handled
		profileInterface.updateProfile(profile2);
		assertEquals(profile2, info.getProfile());
		verify(testlistener, times(1)).notifyChange(profile2);
		assertEquals(Arrays.asList("issue3", "issue2"), info.getIssues());
		verify(reporter, times(0)).log(any(), any());

	}

	@Test
	public void testActNotMyTurn() {
		info.doAction(new Accept(PARTY_ID, new Bid(Collections.emptyMap())));
		verify(reporter, times(1)).log(eq(Level.SEVERE), any());

	}

	@Test
	public void testChangeTurn() {
		Listener<Object> testlistener = mock(Listener.class);
		info.addListener(testlistener);

		info.setMyTurn(true);
		verify(testlistener, times(1)).notifyChange(eq(true));
	}

	@Test
	public void testActMyTurn() {
		info.setMyTurn(true);
		info.doAction(new Accept(PARTY_ID, new Bid(Collections.emptyMap())));
		verify(reporter, times(0)).log(any(), any());

	}

	@Test
	public void testSetCurrentBid() {
		Listener<Object> testlistener = mock(Listener.class);
		info.addListener(testlistener);

		Bid bid = mock(Bid.class);
		info.setCurrentBid(bid);
		assertEquals(bid, info.getCurrentBid());
		verify(testlistener, times(1)).notifyChange(bid);

	}

}

class TestProfileInterface extends DefaultListenable<Profile>
		implements ProfileInterface {

	private Profile profile;

	public TestProfileInterface(Profile profile) {
		this.profile = profile;
	}

	public void updateProfile(Profile profile) {
		this.profile = profile;
		notifyListeners(profile);
	}

	@Override
	public Profile getProfile() {
		return profile;
	}

}
