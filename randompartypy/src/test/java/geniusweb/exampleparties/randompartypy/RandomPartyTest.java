package geniusweb.exampleparties.randompartypy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import geniusweb.actions.Accept;
import geniusweb.actions.Action;
import geniusweb.actions.EndNegotiation;
import geniusweb.actions.Offer;
import geniusweb.actions.PartyId;
import geniusweb.bidspace.AllBidsList;
import geniusweb.connection.ConnectionEnd;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.inform.ActionDone;
import geniusweb.party.inform.Inform;
import geniusweb.party.inform.Settings;
import geniusweb.party.inform.YourTurn;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.progress.ProgressRounds;
import geniusweb.pythonadapter.PythonPartyAdapter;
import geniusweb.references.Parameters;
import geniusweb.references.ProfileRef;
import geniusweb.references.ProtocolRef;
import geniusweb.references.Reference;
import tudelft.utilities.listener.DefaultListenable;

public class RandomPartyTest {

	private static final String PROFILE = "src/test/resources/testprofile.json";
	private static final String SAOP = "SAOP";
	private static final PartyId otherparty = new PartyId("other");
	private final static ObjectMapper jackson = new ObjectMapper();

	private PythonPartyAdapter party;
	private TestConnection connection = new TestConnection();
	private final ProtocolRef protocol = mock(ProtocolRef.class);
	private final ProgressRounds progress = mock(ProgressRounds.class);
	private Settings settings;
	private final Parameters parameters = new Parameters();

	private LinearAdditive profile; // the real profile

	@Before
	public void before() throws JsonParseException, JsonMappingException,
			IOException, URISyntaxException {
		party = new PythonPartyAdapter() {

			@Override
			public String getPythonClass() {
				return "RandomParty";
			}

		};
		settings = new Settings(new PartyId("party1"),
				new ProfileRef(new URI("file:" + PROFILE)), protocol, progress,
				parameters);
		String serialized = new String(Files.readAllBytes(Paths.get(PROFILE)),
				StandardCharsets.UTF_8);
		profile = (LinearAdditive) jackson.readValue(serialized, Profile.class);

	}

	@Test
	public void smokeTest() {
	}

	@Test
	public void getDescriptionTest() {
		assertNotNull(party.getDescription());
	}

	@Test
	public void getCapabilitiesTest() {
		Capabilities capabilities = party.getCapabilities();
		assertFalse("party does not define protocols",
				capabilities.getBehaviours().isEmpty());
	}

	@Test
	public void testInformConnection() {
		party.connect(connection);
		// agent should not start acting just after an inform
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testInformSettings() {
		party.connect(connection);
		connection.notifyListeners(settings);
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testInformAndConnection() {
		party.connect(connection);
		party.notifyChange(settings);
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testOtherWalksAway() {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new ActionDone(new EndNegotiation(otherparty)));

		// party should not act at this point
		assertEquals(0, connection.getActions().size());
	}

	@Test
	public void testAgentHasFirstTurn() {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new YourTurn());
		assertEquals(1, connection.getActions().size());
		assertTrue(connection.getActions().get(0) instanceof Offer);
	}

	@Test
	public void testAgentAccepts() {
		party.connect(connection);
		party.notifyChange(settings);

		Bid bid = findGoodBid();
		party.notifyChange(new ActionDone(new Offer(otherparty, bid)));
		party.notifyChange(new YourTurn());
		assertEquals(1, connection.getActions().size());
		assertTrue(connection.getActions().get(0) instanceof Accept);

	}

	@Test
	public void testAgentsUpdatesProgress() {
		party.connect(connection);
		party.notifyChange(settings);

		party.notifyChange(new YourTurn());
		verify(progress).advance();
	}

	@Test
	public void testGetCapabilities() {
		assertTrue(party.getCapabilities().getBehaviours().contains(SAOP));
	}

	private Bid findGoodBid() {
		for (Bid bid : new AllBidsList(profile.getDomain())) {
			if (profile.getUtility(bid)
					.compareTo(BigDecimal.valueOf(0.7)) > 0) {
				return bid;
			}
		}
		throw new IllegalStateException(
				"Test can not be done: there is no good bid with utility>0.7");
	}

}

/**
 * A "real" connection object, because the party is going to subscribe etc, and
 * without a real connection we would have to do a lot of mocks that would make
 * the test very hard to read.
 *
 */
class TestConnection extends DefaultListenable<Inform>
		implements ConnectionEnd<Inform, Action> {
	private List<Action> actions = new LinkedList<>();

	@Override
	public void send(Action action) throws IOException {
		actions.add(action);
	}

	@Override
	public Reference getReference() {
		return null;
	}

	@Override
	public URI getRemoteURI() {
		return null;
	}

	@Override
	public void close() {

	}

	@Override
	public Error getError() {
		return null;
	}

	public List<Action> getActions() {
		return actions;
	}

}
